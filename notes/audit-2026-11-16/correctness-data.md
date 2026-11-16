# Audit: correctness-data (data layer vs schema and port contracts)

## Progress
Done: all files in data/** read against 01_schema.sql and business/port Javadoc; existing tests run under db+mvn lock
(Oracle*MapperTest, FileDocumentStorageTest, PdfMenuRendererTest: green); PDF edge cases run by a scratch program; DB timezone checked by SELECT.
Next: nothing open.

Checked and found OK (no finding): all ResultSet/PreparedStatement are closed (try-with-resources in Jdbc.query/insert/update);
connections only inside Database.inTransaction (mapper outside tx throws IllegalStateException); LISTAGG of allergens max ~14*(4+240 bytes)=3.4 kB < 4000
and roles tiny; CHAR padding handled (role_code trim, token exact 32 chars, users.active CHAR(1)); NUMBER scales go through BigDecimal/Money;
only 19c SQL (FETCH FIRST, LISTAGG, CONNECT BY ... ORDER SIBLINGS BY, DEFAULT seq.NEXTVAL + getGeneratedKeys); CONNECT BY tree loading is one query,
parent always precedes child, missing root throws a clear exception; no N+1 in findRevision/loadTree (one JOIN query each); TIMESTAMPTZ columns
(menu_revisions, share_links) round-trip through getTimestamp correctly; micros truncation for the optimistic token is done in MenuRevision.recordSave.

### DATA-1 Plain TIMESTAMP columns filled by the DB are shown shifted by the zone offset
- Severity: medium
- Evidence: 01_schema.sql project_nodes.created_at / product_price_history.changed_at / users.created_at / projects.created_at `TIMESTAMP DEFAULT SYSTIMESTAMP`;
  Jdbc.java `dateTime()` = `rs.getTimestamp(column).toLocalDateTime()` (used in OracleProjectNodeMapper.read and OracleProductMapper.findPriceHistory).
  Verified: `select dbtimezone, sessiontimezone` = +00:00, JVM is CET; node 1 created_at = 15-NOV 22:22 (UTC wall clock) is read as 22:22 local, real local time was 23:22.
- Why: SYSTIMESTAMP is stored as DB-server wall clock without zone, the driver reads it as JVM-local time, so every node creation date and every price-change time (trigger-written, so always DB time) is 1 h (summer 2 h) wrong; it gets worse if the DB host zone differs from the app host.
- Fix: in 01_schema.sql make these columns `TIMESTAMP WITH LOCAL TIME ZONE` (comments.created_at already is; the driver then returns session-zone time = JVM zone). Minimal alternative without schema change: nothing safe in the mapper, because the server zone is unknown to it.
- Confidence: confirmed

### DATA-2 PdfMenuRenderer throws on characters the font has no glyph for
- Severity: medium
- Evidence: PdfMenuRenderer `show()` -> `out.showText(s)` and `width()` -> `font.getStringWidth(s)`. Ran the renderer with product names containing an emoji, CJK, TAB, newline, U+0001:
  each gives `IllegalArgumentException: No glyph for U+... in font DejaVuSans` (a RuntimeException, not IOException; nothing is written). NBSP/ZWSP/long names are fine.
- Why: product/category/menu/project names are only length-checked, so one pasted emoji or a tab/newline in a name makes UC14 (PDF) crash for the whole menu, and callers that catch only IOException surface a raw stack/exception.
- Fix: one helper in the renderer that maps each code point to itself if the font has it, else '?' (control chars to ' '), applied to every string before width()/showText(); e.g. `font.hasGlyph(cp)` (PDType0Font) per code point.
- Confidence: confirmed

### DATA-3 FileDocumentStorage.retrieve overwrites the target silently; target path comes from an unvalidated node name
- Severity: medium
- Evidence: FileDocumentStorage.retrieve `Files.copy(resolve(key), target, REPLACE_EXISTING)`; DocumentService.downloadDocuments `storage.retrieve(d.getStorageKey(), targetDir.resolve(d.getName()))`;
  NodeCreator.createIn checks only blank and length <= 100, so a document/folder name may contain `/`, `..`, NUL.
- Why: (a) two documents with the same name from different folders, or an existing file in the chosen folder, are overwritten without a word while the count says all were downloaded; (b) a document named `../../x` makes `resolve` leave targetDir and writes outside it (path traversal on the worker's machine; names come from a user, the storage key itself is safe: `safe()` + UUID + root check). `resolve(name)` with NUL throws unchecked InvalidPathException, not caught by `catch (IOException)`.
- Fix: reject `/ \ NUL` and the names `.`/`..` in NodeCreator.createIn (business, one check, Czech message), and in downloadDocuments use `targetDir.resolve(d.getName()).normalize()` + `startsWith(targetDir)` guard and a free-name suffix instead of REPLACE_EXISTING (drop REPLACE_EXISTING in retrieve, let FileAlreadyExistsException be the signal).
- Confidence: confirmed (code path); not exercised through the UI

### DATA-4 PDF layout edge cases: long title / category / header not fitted, overlap in quantity column, row after page break without table header
- Severity: low
- Evidence: PdfMenuRenderer `w.text(w.bold, 16, MARGIN, title)` and category name `w.text(w.bold, 12, ...)` and the "Revize ..." line are drawn without `fit`/wrap (title of 300 chars renders without error but runs off the page edge, text lost); `row()` fits only the product column to 250 pt, the quantity string (`qty + " " + unitLabel`) is right-aligned at QTY_RIGHT and can reach into the product column for big quantities/long unit labels; after a page break (`need()`) the items continue without the "Produkt/Množství/..." header row; a single word longer than WIDTH in `wrapped` is not split (allergen list only, harmless).
- Why: cosmetic but visible in the delivered PDF; the empty menu case is fine (title, Celkem 0, "Alergeny: žádné").
- Fix: reuse the existing `wrapped`/`fit` for title and category (title max 100+100 chars), repeat the header row in `newPage()` when inside a category (small flag).
- Confidence: confirmed (title overflow run; others by code reading)

### DATA-5 Business rules inside the data layer (mapper SQL/Java)
- Severity: low
- Evidence: OracleProjectNodeMapper.TREE `s.active = 'Y' AND s.valid_until > SYSTIMESTAMP` decides "link is live" with the DB clock, while ShareLinkMapper.findActive Javadoc says "UC18 decides in the domain which of them expired" (domain uses the application Clock); OracleMenuMapper.createdAt() `LocalDateTime.now()` when the revision has no date (system clock inside a mapper, bypasses the injected Clock); `findTitle` builds the display string `p.name || ' – ' || m.name` in SQL.
- Why: the "active link" flag on the tree and the sharing service can disagree (clock skew, tests with a fixed Clock), and the mappers contain decisions that the rules say belong to business.
- Fix: select `valid_until` plus `active` in TREE and let Menu/ShareLink domain compute `hasActiveLink(now)`; make createdAt required (throw if null, business always calls recordSave first); return project name and menu name separately from findTitle or build the string in MenuService.
- Confidence: confirmed

### DATA-6 overwriteRevision deletes item comments (ON DELETE CASCADE) and its optimistic token has a DST blind spot
- Severity: low
- Evidence: OracleMenuMapper.overwriteRevision `DELETE FROM menu_categories ...` -> menu_items -> comments (fk_comments_item ON DELETE CASCADE) and the Javadoc/comment even states it. Token compare `created_at = ?` with `Timestamp.valueOf(LocalDateTime)`: in the repeated hour of the autumn clock change the LocalDateTime is ambiguous, so a value read back may bind to the other instant.
- Why: today only WORKING revisions are overwritten and shareRevision turns a revision SHARED before any link/comment exists, so no comment is lost in the normal flow; but nothing in the data layer or the schema protects it (an item comment on a WORKING revision would vanish silently). The DST case gives a false "someone else saved" conflict once a year, at most.
- Fix: keep as is, but add a one-line guard in overwriteRevision (`AND state = 'WORKING'` in the UPDATE, return false otherwise) so a SHARED revision with comments can never be rebuilt; ignore DST.
- Confidence: confirmed (mechanism), flow suspected safe

### DATA-7 No connection pool, no login/statement timeout; rollback failure hides the original error
- Severity: low
- Evidence: Database.inTransaction opens `DriverManager.getConnection(url, user, password)` per business operation (every web request of the share-link server and every UI action); no `DriverManager.setLoginTimeout`/`oracle.net.CONNECT_TIMEOUT`/`setQueryTimeout`; `connection.rollback()` inside `catch (RuntimeException | Error e)` throws SQLException which replaces `e`.
- Why: a dead DB blocks the HTTP/FX thread for the default TCP timeout; under many customer requests each pays a full Oracle login (tens of ms) and can exhaust DB sessions; the root cause is lost when rollback also fails. Acceptable for a semester demo, but easy to bound.
- Fix: `DriverManager.setLoginTimeout(5)` in init (and `props.put("oracle.jdbc.ReadTimeout", "30000")`); wrap rollback in its own try/catch with `e.addSuppressed(...)`. A pool is not needed (framework-free rule).
- Confidence: confirmed

### DATA-8 FileDocumentStorage / InMemory edge cases
- Severity: low
- Evidence: FileDocumentStorage.store/retrieve use `Files.copy(..., REPLACE_EXISTING)` (delete + copy, not atomic; a failure leaves a truncated file at the key or target); `delete` leaves empty `pNN` directories; `resolve` throws unchecked InvalidPathException for NUL / unmappable chars while the port declares IOException only; keys contain Czech letters (`safe()` keeps `\p{L}`), which fail with "Malformed input or input contains unmappable characters" when the JVM runs with `sun.jnu.encoding` = ANSI_X3.4-1968 (LANG=C/POSIX) – suspected, not run. Same key twice cannot happen (UUID in key; `uq_project_nodes_storage`). InMemoryDocumentStorage uses an unsynchronised HashMap (stub for tests only; not wired in DataAccessFactory).
- Why: documents with Czech names could be unstorable on a server started without a UTF-8 locale; a crash mid-copy leaves a corrupt document that the DB row still points to.
- Fix: in `safe()` use only ASCII (`[^A-Za-z0-9._-]` after Normalizer NFD + strip accents) — the display name is in the DB anyway; copy to `key + ".tmp"` then `Files.move(ATOMIC_MOVE)`; catch InvalidPathException in `resolve` and rethrow as IOException.
- Confidence: suspected (encoding), confirmed (non-atomic copy, unchecked exception)

### DATA-9 DB errors reach the user verbatim; concurrent new revision gives raw ORA-00001
- Severity: low
- Evidence: Jdbc.java `new DataAccessException("Chyba při zápisu do databáze: " + e.getMessage(), e)` (message contains ORA-xxxxx and constraint names); MenuService.saveRevision checks `latest.getRevisionNo() != revision.getRevisionNo() - 1` in READ COMMITTED without a lock, so two workers inserting the same revision number concurrently both pass the check and the second hits pk_menu_revisions.
- Why: the second worker sees "ORA-00001: unique constraint (CATERING.PK_MENU_REVISIONS) violated" instead of the Czech conflict message of UC08 7.1; same message form can leak schema details to the web layer if a DataAccessException is rendered there.
- Fix: in Jdbc catch `SQLException` with `getErrorCode() == 1` and throw a dedicated DuplicateKeyException (data layer) that MenuService maps to ConflictException; keep the Oracle text only in the log, show a generic Czech message.
- Confidence: suspected (race not reproduced), confirmed (message form)

### DATA-10 findComments uses an OR across two tables; FK comments(category_id, product_id) has no index
- Severity: low
- Evidence: OracleMenuMapper.COMMENTS + `WHERE (k.menu_id = ? AND k.revision_no = ?) OR (c.menu_id = ? AND c.revision_no = ?)` after LEFT JOIN menu_categories; schema has `ix_comments_revision (menu_id, revision_no)` but no index for `fk_comments_item (category_id, product_id)`.
- Why: the OR over a joined table cannot use ix_comments_revision for the item branch, and every delete of menu_items/categories (overwriteRevision, delete menu) scans comments. Irrelevant at semester data size, noted only because the tables will grow with comments.
- Fix: optional: `UNION ALL` of the two selects, and `CREATE INDEX ix_comments_item ON comments (category_id, product_id)` in 01_schema.sql.
- Confidence: suspected

## Seen outside my area
- business/DocumentService.downloadDocuments: name-to-path join and silent overwrite (see DATA-3), belongs to correctness-business/security as well.
- business/NodeCreator: no check of `/`, `\`, `..` in node names (DATA-3).
- Schema comment says projects.responsible_user_id is "transferable" but OracleProjectMapper.update never writes it and no use case reassigns it (tds-compliance/vis-compliance to decide whether a use case is missing).

## Summary
high 0 / medium 3 (DATA-1, DATA-2, DATA-3) / low 7 (DATA-4 … DATA-10). Total 10.
