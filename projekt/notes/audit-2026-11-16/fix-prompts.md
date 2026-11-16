# Fix prompts for the 2026-11-16 audit

Findings: `/home/jan/Documents/VSB/tretak/VIS/projekt/notes/AUDIT-2026-11-16.md` (IDs A-01…A-70).
Each group below is one subagent prompt. Groups G1–G7 touch **disjoint files** and can run in parallel.
G8 runs **after** G1–G4 and G7 have reported (doc sync, test count, PDF rebuild).

| Group | Model | Findings | Files (exclusive) |
|---|---|---|---|
| G1 Revisions | Sonnet | A-05, A-07, A-08, A-09, A-22 (menu part), A-23, A-27 | MenuRevision, Product, User, MenuService, SharingService, MenuMapper, OracleMenuMapper + their tests |
| G2 Documents, storage, PDF, JDBC | Sonnet | A-04, A-10, A-21 (data part), A-22 (document part), A-24, A-25, A-26, A-41, A-42 | NodeCreator, DocumentService, MenuExportService, FileDocumentStorage, PdfMenuRenderer, Jdbc, Database + their tests |
| G3 Web + auth | Sonnet | A-06, A-13, A-28, A-29, A-30, A-31, A-32, A-56 (comment) | presentation/web/*, AuthService, UserService, AppContext + their tests |
| G4 Desktop UI | Sonnet | A-11, A-12, A-21 (UI part), A-33…A-40, UI part of A-05 | presentation/desktop/* + desktop tests |
| G5 TDS scripts + notes | Sonnet | A-14, A-15, A-49, A-50 (+ README/notes lines for 04) | TDS/db 01, 02, 03, tds-design-notes.md, TDS/README.md |
| G6 TDS query catalogue | Sonnet | A-01 | TDS/db/04_queries.sql (new) only |
| G7 Docs + hand-in packaging | Sonnet | A-03, A-16, A-43…A-48, A-51 | a3, a4, a6, a7 .tex, uc-overview/act-uc08 .puml/.png, main.tex, projekt/pack-handin.sh, OPEN-QUESTIONS.md |
| G8 Doc sync + final rebuild (after) | Sonnet | follow-ups listed by G1–G4 under "Doc sync needed", A7 test counts | a2, a5 .tex, class-*/seq-* .puml/.png, a7 (test table), all PDFs |

Not in any group (John decides first): A-02, A-17, A-18, A-19, A-20, A-52, A-53, A-54, A-55. WONTFIX: A-56…A-70
(G3 only adds the A-56 shortcut comment).

---

## Common rules (every group reads this section first)

**Paths.** App: `/home/jan/Documents/VSB/tretak/VIS/projekt/app` (main code `src/main/java/cz/vsb/catering/{presentation,business,data}`,
tests `src/test/java/cz/vsb/catering/...`). DB scripts: `/home/jan/Documents/VSB/tretak/TDS/db`. Artefacts:
`/home/jan/Documents/VSB/tretak/VIS/projekt/docs/artefacts`. Notes: `/home/jan/Documents/VSB/tretak/VIS/projekt/notes`.
Read `/home/jan/Documents/VSB/tretak/VIS/CLAUDE.md` and the finding texts in `AUDIT-2026-11-16.md` for your IDs.

**File ownership.** Edit only the files listed for your group. Other groups edit the same source tree at the same
time. If you need a file outside your list, do not edit it: write it under "Blocked / needs other group" in your
report. If the build fails in a file you do not own, wait a minute and retry (another group is mid-edit); never
"fix" it. Do not edit `AUDIT-2026-11-16.md` (the coordinator updates the status column), any `CLAUDE.md`, or memory files.

**Code rules.** KISS and SOLID; it is a semester project to defend. Plain Java 21, JavaFX, JDK HttpServer, JDBC with
Data Mapper; no frameworks, no ORM, no new dependencies. Layers: presentation → business ← data (`business` imports
nothing from `data` or `presentation`; `presentation` imports nothing from `data`). UI texts and exception messages
in Czech, identifiers in English. Follow John's style in the surrounding code: small methods, one-line Javadoc that
names the UC step (e.g. `/** UC08 7.1: … */`), a known limit gets `// shortcut: <limit>, <when to upgrade>`.
Keep public signatures that appear in the docs (`docs/artefacts/diagrams/class-*.puml`, `\svc{}` in `a2-*.tex`)
unless the fix needs a change; if you change one, do not edit the docs, list it under "Doc sync needed".

**Tests.** Add a test for each fix. Unit tests with fakes go to `src/test/java/cz/vsb/catering/business/{domain,service}`
(fakes in `business/fake`, `DirectTransactionRunner`). Oracle end-to-end tests go to `business/e2e` (extend
`E2eBaseTest`; each test creates its own data and deletes it; `@AfterAll` checks the DB is back at seed). Data-layer
tests use `data/oracle/TestDatabase`. Web tests: `presentation/web/*Test`. Desktop tests: `presentation/desktop/*Test`
(need a display: `env DISPLAY=:1`).

**Build and locks.** Always:
```bash
SP=/tmp/claude-1000/-home-jan-Documents-VSB-tretak-VIS/45b80ccd-dc35-493e-a2a1-c9a5ccc67fed/scratchpad
export JAVA_HOME=~/.jdks/openjdk-27.0.1.0.3
M=/home/jan/Applications/idea-IU-262.10968.63/plugins/maven-plugin/lib/maven3/bin/mvn
POM=/home/jan/Documents/VSB/tretak/VIS/projekt/app/pom.xml
# unit tests only (no DB):
flock $SP/mvn.lock $M -q -B -f $POM test -Dtest='MenuServiceTest,MenuRevisionTest' -Dsurefire.failIfNoSpecifiedTests=false
# anything that touches the DB (e2e, Oracle mapper tests, full suite): db.lock FIRST, then mvn.lock
flock $SP/db.lock flock $SP/mvn.lock env DISPLAY=:1 $M -q -B -f $POM test -Dtest='Uc08*' -Dsurefire.failIfNoSpecifiedTests=false
```
Never run `mvn clean` (other groups compile in the same `target/`). Lock order is always db.lock then mvn.lock.
The local Oracle DB is shared: `export DOCKER_CONTEXT=desktop-linux`; sqlplus:
`docker exec -i vis-oracle sqlplus -s CATERING/***REMOVED***@//localhost:1521/FREEPDB1`. Read-only SELECTs need no
lock. Any DB-changing action holds `flock $SP/db.lock <command>` and must leave the DB at seed. Seed check (read-only):
```sql
SELECT (SELECT COUNT(*) FROM users) u, (SELECT COUNT(*) FROM projects) p, (SELECT COUNT(*) FROM project_nodes) n,
       (SELECT COUNT(*) FROM menu_revisions) r, (SELECT COUNT(*) FROM menu_items) i, (SELECT COUNT(*) FROM comments) c,
       (SELECT COUNT(*) FROM share_links) s, (SELECT COUNT(*) FROM product_price_history) h FROM dual;
-- seed: 10, 10, 77, 36, 346, 24, 12, 10
```
If unsure, rebuild under the lock:
```bash
flock $SP/db.lock bash -c 'cd /home/jan/Documents/VSB/tretak/TDS/db && for f in 01_schema.sql 02_seed.sql 03_checks.sql; do docker cp "$f" vis-oracle:/tmp/; done && printf "@/tmp/01_schema.sql\n@/tmp/02_seed.sql\n@/tmp/03_checks.sql\nexit\n" | docker exec -i vis-oracle sqlplus -S CATERING/***REMOVED***@//localhost:1521/FREEPDB1'
```
If the DB is down (ORA-12514/01109): `systemctl --user start docker-desktop`, `docker start vis-oracle`, wait for
"DATABASE IS READY TO USE!" in `docker logs vis-oracle`.

**Docs rules (groups that edit `.tex`).** Czech prose; no semicolons; no dashes inside sentences; never mention TDS
or the school server; cross-references with `\odkaz{label}{text}` and `\ucref{NN}`; a big diagram goes on its own
page. Diagrams: `cd docs/artefacts && plantuml -tpng diagrams/<name>.puml` (PNG only). List every changed `.tex`
and `.png` in your report so the coordinator rebuilds the PDFs.

**Progress.** Write `/home/jan/Documents/VSB/tretak/VIS/projekt/notes/audit-2026-11-16/fix-<group>.md` as you go
(e.g. `fix-g1.md`) with `## Progress` → `Done: … / Next: …`, so a cut-off run can resume.

**Report** (final message): per finding ID: what changed (files), tests added (class.method), test results
(command + counts), "Doc sync needed" (public signature or documented behaviour changes, with the doc place if you
know it), "Blocked / needs other group", and anything you skipped and why.

---

## G1 Revisions (Sonnet)

You fix menu-revision rules in the business layer and the menu mapper of the catering IS. Read the Common rules
section of `/home/jan/Documents/VSB/tretak/VIS/projekt/notes/audit-2026-11-16/fix-prompts.md` first, then findings
A-05, A-07, A-08, A-09, A-22, A-23, A-27 in `/home/jan/Documents/VSB/tretak/VIS/projekt/notes/AUDIT-2026-11-16.md`.
Progress file: `audit-2026-11-16/fix-g1.md`.

**Files you may edit** (paths under `src/main/java/cz/vsb/catering/` and `src/test/java/cz/vsb/catering/`):
main `business/domain/MenuRevision.java`, `business/domain/Product.java`, `business/domain/User.java`,
`business/service/MenuService.java`, `business/service/SharingService.java`, `business/port/MenuMapper.java`,
`data/oracle/OracleMenuMapper.java`; tests `business/domain/{MenuRevisionTest,ProductTest,UserTest}.java`,
`business/command/MenuEditorTest.java`, `business/service/{MenuServiceTest,SharingServiceTest,SharingServiceExpiryTest,MenuRevisionHistoryServiceTest}.java`,
`business/fake/{FakeMenuMapper,Fixtures}.java`, `data/oracle/OracleMenuMapperTest.java`,
`business/e2e/{Uc03ArchivedProjectTest,Uc08MenuRevisionTest,Uc09Uc10RevisionHistoryTest,Uc11ShareCommentTest,Uc13ResolveCommentsTest}.java`.
Not yours: `ArchivedProjectServiceTest` (G2), anything in `presentation/` (G4 wraps the UI calls).

**Fixes.**
1. A-05 (high). `MenuService.saveRevision` insert branch (around l.124-128) also requires
   `latest.getState() == RevisionState.SHARED`; otherwise `ConflictException("Nejnovější revize je pracovní. Upravte ji, nebo ji nejprve sdílejte.")`.
   `MenuService.createWorkingCopy(MenuRevision)` reads the latest header in a tx and throws the same exception when
   the newest revision is WORKING (so the user never edits a copy that cannot be saved). Today `MenuRevision.createWorkingCopy`
   (l.159-165) allows a copy of any historical revision. Tests: unit (fake) for both refusals and for the allowed
   case (newest SHARED); e2e in `Uc08MenuRevisionTest` (r1 shared, r2 working → copy of r1 refused, DB unchanged).
2. A-07 + A-08 (medium). Atomic, write-first state changes.
   - `OracleMenuMapper.overwriteRevision` (l.100-113): add `AND state = 'WORKING'` to the UPDATE (0 rows → `false`,
     the service already throws ConflictException).
   - Replace `MenuMapper.updateState(MenuRevision)` by a method that does
     `UPDATE menu_revisions SET state = 'SHARED' WHERE menu_id = ? AND revision_no = ? AND state = 'WORKING' AND created_at = ?`
     (bind `loadedSavedAt`) and returns whether one row changed. Suggested name `markShared(long menuId, int revisionNo, LocalDateTime loadedSavedAt)`.
     Update `FakeMenuMapper` the same way. `updateState` is called only from `SharingService` (checked).
   - `SharingService.shareRevision` (l.44-67): keep `revision.validate()` before the tx; inside the tx call the new
     mapper method and throw `ConflictException(MenuService.CONFLICT)` on `false`; call `revision.markShared()` only
     after `inTransaction` returns (split `markShared` if it validates/notifies at the wrong moment; keep it simple).
   - `MenuService.saveRevision` (l.122, 127): compute `savedAt = now()` locally, pass note/user/time to the mapper
     without mutating `revision` first (e.g. call `recordSave` on a copy, or let `recordSave` run after commit and
     give the mapper the values explicitly; pick the smaller diff), then `recordSave` + `markStored` after commit.
   - Tests: unit (fake mapper returns false → ConflictException, revision still WORKING/unchanged, listener not
     notified); Oracle mapper test: overwrite of a SHARED revision returns false; share with a stale
     `loadedSavedAt` → ConflictException (e2e in `Uc11ShareCommentTest`).
3. A-09 (medium). Limits matching `TDS/db/01_schema.sql`: in `MenuRevision.findProblems` (and `addCategory` where
   the name is set) category name ≤ 60 chars, item note ≤ 200, quantity scale ≤ 2 and < 1 000 000, unit price scale
   ≤ 2 and < 100 000 000; `Product` price scale ≤ 2 and < 100 000 000; `User.create` full name ≤ 100, e-mail ≤ 254.
   Read the exact column sizes in `01_schema.sql` before coding. Czech messages through the existing problem list /
   field errors. Unit tests for each limit (boundary value passes, +1 fails), including quantity 0.004.
4. A-22 (menu part). Move `requireOpenProject` in `MenuService.createMenu` (l.49) inside the insert transaction.
   Unit test: archived project → exception and no node/menu inserted.
5. A-23. `MenuRevision.addItem/changeItem`: null quantity or unit price → `ValidationException` (Czech). Unit test.
6. A-27. `OracleMenuMapper.createdAt()` throws `IllegalStateException` instead of `LocalDateTime.now()`. Make sure
   every caller sets the time first (after fix 2). Covered by existing mapper tests.

At the end run the unit tests, then (under both locks) `OracleMenuMapperTest`, `Uc0*`, `Uc1*`, and once the whole
suite. If a desktop test (`DesktopFlowTest`) fails because of the new A-05 rule, do not edit it: report the test name
under "Blocked / needs other group" (G4). Doc sync needed at least: the new mapper method (class-dal.puml), and the
new UC08 conflict (A2 UC08 2.x/7.x text).

---

## G2 Documents, storage, PDF, JDBC (Sonnet)

You fix document download, file storage, PDF rendering and JDBC error handling. Read the Common rules section of
`/home/jan/Documents/VSB/tretak/VIS/projekt/notes/audit-2026-11-16/fix-prompts.md` first, then findings A-04, A-10,
A-21 (data part), A-22 (document part), A-24, A-25, A-26, A-41, A-42 in `AUDIT-2026-11-16.md`.
Progress file: `audit-2026-11-16/fix-g2.md`.

**Files you may edit:** main `business/domain/NodeCreator.java`, `business/service/DocumentService.java`,
`business/service/MenuExportService.java`, `data/file/FileDocumentStorage.java`, `data/file/PdfMenuRenderer.java`,
`data/oracle/Jdbc.java`, `data/oracle/Database.java`; tests `business/domain/{NodeCreatorTest,FolderTest}.java`,
`business/service/{DocumentServiceTest,MenuExportServiceTest,ArchivedProjectServiceTest}.java`,
`business/fake/FakeProjectNodeMapper.java`, `data/file/{FileDocumentStorageTest,PdfMenuRendererTest}.java`,
`data/oracle/OracleProjectNodeMapperTest.java`, `business/e2e/{Uc02ProjectDocumentsTest,Uc14Uc18ExportExpiryTest,Uc19DeleteTest}.java`.

**Fixes.**
1. A-04 (high, security). (a) `NodeCreator.createIn` (l.13-21) rejects names containing `/`, `\`, any control
   character (`Character.isISOControl`), and the names `.` and `..`: `ValidationException("Název nesmí obsahovat lomítka ani řídicí znaky.", Map.of("name", …))`.
   (b) `DocumentService.downloadDocuments` (l.88): `Path dir = targetDir.toAbsolutePath().normalize(); Path target = dir.resolve(name).normalize();`
   refuse when `!dir.equals(target.getParent())` (old names in the DB may still be bad); if `target` exists, pick a
   free name `název (2).ext`, `(3)`… instead of overwriting; catch `InvalidPathException` as a validation error.
   (c) `FileDocumentStorage.retrieve` (l.24) copies without `REPLACE_EXISTING`. (d) `MenuExportService.fileName`
   also replaces `/` and `\`. Tests: NodeCreator rejects each bad name; download of a document named `../x.pdf`
   (insert it through the fake mapper, bypassing NodeCreator) writes nothing outside the folder; two documents with
   the same name are both saved (`x.pdf`, `x (2).pdf`); an existing file is not overwritten.
2. A-10 (medium). `PdfMenuRenderer`: one helper that keeps a code point if the font has a glyph
   (`PDType0Font.hasGlyph(int)`; check the PDFBox 3.0.3 API), turns control characters into a space, else `?`;
   apply it to every string before `show()` and `width()` (l.148, l.159). Test: product/category names with an emoji,
   a TAB, a newline and U+0001 render without exception.
3. A-25. Title, category name and the "Revize …" line use the existing `wrapped`/`fit`; repeat the table header row
   after a page break inside a category. Test: a 300-char title and a long category render; a menu long enough for
   two pages renders (check page count ≥ 2; no visual assertion needed).
4. A-21 (data part). `Jdbc` (l.32-33, l.50-51): when `e.getErrorCode() == 1` (unique/PK violation) throw
   `new ConflictException("Záznam se stejnou hodnotou už existuje nebo jej mezitím změnil jiný pracovník. Načtěte data znovu.")`
   (`business.domain.ConflictException`; data may import business). Other errors stay `DataAccessException`. Test
   in `OracleProjectNodeMapperTest`: inserting a duplicate sibling name gives ConflictException (clean up, DB at seed).
5. A-26. `Database`: `DriverManager.setLoginTimeout(5)` once at init; rollback in its own try/catch with
   `e.addSuppressed(rollbackError)`. Test only if cheap (a rollback-failure unit test is optional; say so if skipped).
6. A-22 (document part). Move `requireOpenProject` in `DocumentService.createFolder` (l.39) and `createDocument`
   (l.56) inside the insert transaction. Tests in `ArchivedProjectServiceTest`.
7. A-24. `FileDocumentStorage.store`: copy to `key + ".tmp"` then `Files.move(…, ATOMIC_MOVE, REPLACE_EXISTING)`;
   `resolve` catches `InvalidPathException` and throws `IOException`. `DocumentService.safe()` (l.157): strip accents
   (`Normalizer.normalize(name, NFD).replaceAll("\\p{M}", "")`) and keep `[A-Za-z0-9._-]`, others `_`. Existing keys
   stay valid (they are stored in the DB). Tests: key of "Příloha č. 1.pdf" is ASCII; store leaves no `.tmp`.
8. A-41. `DocumentService` l.152: keep the shortcut comment, print one Czech line to `System.err` with the key and message.
9. A-42. `MenuExportServiceTest`: direct test of `fileName` (diacritics, space, `/`).

Run the unit tests, then under both locks `OracleProjectNodeMapperTest`, `FileDocumentStorageTest`,
`PdfMenuRendererTest`, `Uc02*`, `Uc14*`, `Uc19*`, and once the whole suite. Doc sync needed: the new name rule
(A2 UC02 alternative flow for an invalid name, if A2 lists name rules) and anything public you changed.

---

## G3 Web + auth (Sonnet)

You harden the customer/manager web (JDK HttpServer) and the auth checks. Read the Common rules section of
`/home/jan/Documents/VSB/tretak/VIS/projekt/notes/audit-2026-11-16/fix-prompts.md` first, then findings A-06,
A-13, A-28, A-29, A-30, A-31, A-32, A-56 in `AUDIT-2026-11-16.md`. Progress file: `audit-2026-11-16/fix-g3.md`.

**Files you may edit:** main `presentation/web/{ManagerHandler,MenuHandler,WebServer,MenuPage,ManagerPage,WebMain}.java`,
`business/service/{AuthService,UserService}.java`, `AppContext.java` (only to expose the configured base URL if
needed); tests `presentation/web/{ManagerWebTest,WebServerTest,ExpiredLinkSchedulerTest}.java`,
`business/service/{AuthServiceTest,UserServiceTest}.java`, `business/fake/FakeUserMapper.java`,
`business/e2e/Uc16UsersTest.java`. Not yours: `User.java` (G1), `Fixtures.java` (G1; put helpers in your test class).

**Fixes.**
1. A-06 (medium). `ManagerHandler` (l.123, l.136-145): the session keeps the user id (and expiry), and
   `currentUser` re-reads the user on every request through a new `AuthService.findActiveUser(long userId)`
   (`users.findById` in a tx, empty if inactive). Drop the session (and send to login) if the user is gone,
   inactive, or no longer has `Role.MANAGER`. `UserService.requireAdmin` (l.62-65) re-reads the actor inside the
   transaction (`users.findById(admin.getId())`) and checks `isActiveAdmin()` on the fresh object. Tests: web test
   where the manager is deactivated / loses MANAGER after login → next request redirects to login; unit test that a
   deactivated admin object is refused by `findAllUsers`.
2. A-13 (medium). `WebServer`: before `HttpServer.create` set `sun.net.httpserver.maxReqTime` and
   `sun.net.httpserver.maxRspTime` to 30 (seconds) with `System.setProperty` unless already set (one-line comment
   why). Keep the 4 threads. Test: the properties are set after start (cheap); a real slowloris test is not needed.
3. A-28. (a) `ManagerHandler.login`: in-memory `Map<String, Attempts>` per lower-cased username; after 5 failures
   within 10 minutes answer 429 with `ManagerPage.login(username, "Příliš mnoho neúspěšných pokusů. Zkuste to za 10 minut.")`
   without calling `auth.login`; success clears the entry. Add `// shortcut: per process, resets on restart; move to the proxy or DB if needed`.
   (b) `AuthService.login`: when the username is unknown, still run one PBKDF2 check against a fixed dummy hash so
   timing does not reveal existing usernames. Tests: 6th wrong attempt → 429; unknown user still returns the same message.
4. A-29. Add `; Secure` to both `Set-Cookie` headers when the configured base URL starts with `https://`. Test both cases.
5. A-30. One helper used by both handlers' `send()`: `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'`,
   `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`. Check what the pages embed (inline style, QR
   or images) and adjust the CSP so nothing breaks. Test: headers present on a customer page and a manager page.
6. A-31. `MenuHandler.handle` (l.36-47, l.66) and `ManagerHandler` (l.105): 405 + `Allow` for a known route with a
   wrong method; a token that does not match the pattern shows `MenuPage.unknownLink()` (UC11 5.1 text); a bad `%`
   escape gives 400 with a fitting body; read `MAX + 1` bytes and answer 413 when the body is longer. Tests for each.
7. A-32. `ManagerHandler.showList` (l.160-163): if `ProjectService.searchProjects(q, null, null, true)` matches the
   same fields as the current filter (name, place, customer) and includes archived projects, use it; otherwise keep
   the filter. Either way delete the stale comment l.160. Do not add new service methods. Keep existing web tests green.
8. A-56. In `UserService.updateUser` add `// shortcut: the last-admin count is not locked; two admins demoting each other at the same instant could both pass. Lock the admin rows (SELECT … FOR UPDATE) if it matters.`

Run web + auth unit tests, then under both locks `ManagerWebTest`, `WebServerTest`, `Uc16*`, and once the whole
suite. Doc sync needed: `AuthService.findActiveUser` (class-services.puml, A5 text), and A6 if it describes the
manager session.

---

## G4 Desktop UI (Sonnet)

You fix JavaFX desktop defects. Read the Common rules section of
`/home/jan/Documents/VSB/tretak/VIS/projekt/notes/audit-2026-11-16/fix-prompts.md` first, then findings A-11, A-12,
A-21 (UI part), A-33…A-40 and A-05 in `AUDIT-2026-11-16.md`. Progress file: `audit-2026-11-16/fix-g4.md`.

**Files you may edit:** main `presentation/desktop/*.java` (MainWindow, MenuEditorWindow, CustomersWindow,
CatalogWindow, EditProjectDialog, MenuItemDialog, ShareDialog, CommentsWindow, CompareRevisionsWindow, Ui,
LoginDialog, DesktopApp and the other desktop classes); tests `presentation/desktop/*Test.java`, `Fx.java`,
`ScriptedPrompter.java`. Nothing in `business/` or `data/`.

**Fixes.**
1. A-11. `MenuEditorWindow.refresh()` (l.226-234): remember the selected value before `table.setRoot(root)`,
   re-select the `TreeItem` holding the same object afterwards (or its category if the item is gone).
2. A-12. `MenuEditorWindow`: `stage.setOnCloseRequest(e -> { if (dirty && revision.isEditable() && !Ui.confirm("Neuložené změny se ztratí. Zavřít?")) e.consume(); });`
   (make it testable through the existing `Ui`/`Prompter` seam used by `ScriptedPrompter`).
3. A-05 (UI part). G1 makes `MenuService.createWorkingCopy` throw `ConflictException` when the newest revision is
   WORKING. Wrap both call sites (`MenuEditorWindow.java:88` copy button and `:383` in `editForComment`) so the
   exception goes to `Ui.error(e)` instead of the FX thread. No global uncaught handler exists.
4. A-21 (UI part). `Ui.error` (l.66-71): keep the texts of `ConflictException` and `ValidationException`; for any
   other exception show "Operaci se nepodařilo provést. Zkontrolujte připojení k databázi a zkuste to znovu." and
   print the stack trace to stderr. `LoginDialog.java:40`: same rule (business messages stay, others get the fixed text).
   Presentation must not import `data`.
5. A-33. `MainWindow.selectProject` (l.215-218): if the id is not in the combo items, reload `findAllProjects()` and select.
6. A-34. `MainWindow.showProject` (l.220-243): on failure clear tree and table, `table.setUserData(null)`, disable create/upload.
7. A-35. `MainWindow.showFolder` (l.272-274): load into a local list in try/catch with `Ui.error`, `setUserData` after success.
8. A-36. `CustomersWindow.save` (l.106-119) and `CatalogWindow.save` (l.350-366): after a successful save call `fill(saved)`.
9. A-37. `MenuEditorWindow.compare()` (l.265-268): for the oldest stored revision compare it with the next stored one, never (n, n).
10. A-38. One instance per Customers/Catalog window (`toFront()` if already open); `initOwner` for child windows
    (Customers, Catalog, Comments, CompareRevisions, MenuEditor if not already); `Platform.exit()` when the main
    stage is hidden after login (`DesktopApp.stop` already stops web server and scheduler).
11. A-39. `EditProjectDialog.archive()` (l.103-113) asks "Neuložené změny se ztratí. Archivovat?" when fields
    differ (or saves first, smaller diff wins); `CatalogWindow.choosePhoto` checks `Files.size` before reading
    (same 100 kB limit and message as the service); `CatalogWindow.fill` adds the product's own VAT rate to the combo
    if missing; `MenuItemDialog` product listener null check; `ShareDialog` catches the QR exception and shows the
    link without the QR; `CommentsWindow` gets an "Obnovit" button calling `reload(selectedId)`.
12. A-40. `CustomersWindow` search: 300 ms `PauseTransition` debounce before `reload(null)`.

Tests: extend `DesktopFlowTest`, `CustomersCatalogWindowTest`, `EditProjectDialogTest`, `CompareRevisionsWindowTest`
(selection kept after add item, close request consumed when dirty, compare indices, save with filter fills the form,
QR failure still shows the dialog, …). Desktop tests need `env DISPLAY=:1` and the DB (both locks). Run them and then
the whole suite once. Doc sync needed: A4/A7 sentences only if they describe the changed behaviour (e.g. the "Obnovit" button).

---

## G5 TDS scripts and design notes (Sonnet)

You fix the Oracle schema scripts and design notes of the TDS part. Read the Common rules section of
`/home/jan/Documents/VSB/tretak/VIS/projekt/notes/audit-2026-11-16/fix-prompts.md` first, then findings A-14, A-15,
A-49, A-50 in `AUDIT-2026-11-16.md` and the source texts TDS-3, TDS-4, TDS-5, TDS-7, TDS-9 in
`audit-2026-11-16/tds-compliance.md`. Also read `/home/jan/Documents/VSB/tretak/TDS/CLAUDE.md` (do not edit it).
Progress file: `audit-2026-11-16/fix-g5.md`.

**Files you may edit:** `/home/jan/Documents/VSB/tretak/TDS/db/{01_schema.sql,02_seed.sql,03_checks.sql,tds-design-notes.md}`,
`/home/jan/Documents/VSB/tretak/TDS/README.md`. Not yours: `04_queries.sql` (G6 creates it in parallel), any Java file.

**Fixes.**
1. A-14. `tds-design-notes.md`: two-sentence descriptions ("Each … must/may …") for menu ↔ MENU project node (1:1,
   `fk_menus_node`, `uq_menus_node`) and user resolves comment (`fk_comments_resolved_by`); add the matrix cells
   Menu–ProjectNode, Comment–User, MenuItem–Comment, and show Product–MenuItem correctly. Compare every FK in
   `01_schema.sql` with the list so none is missing.
2. A-15. No schema change. Under "Normal forms" state that `menus.project_id` and `menus.name` deliberately repeat
   facts of the MENU node (project_id for the non-transferable trigger and fast lookups, name so simple queries
   need no join), that this is a controlled denormalisation, and how it is checked. Add to `03_checks.sql` section 5
   an integrity check "menus whose node is not a MENU node of the same project with the same name" (expected 0).
3. A-49. `03_checks.sql`: check "nodes whose parent is not a FOLDER" (expected 0); notes say tree rules (parent is a
   folder of the same project) are enforced by the application and checked by 03.
4. A-50. `02_seed.sql:3` comment says PBKDF2, not SHA-256. Correct the label of the 03 test "STI: menu node as a
   root folder" (rejected by `ux_project_nodes_root`). Add `CONSTRAINT ck_users_no_self_supervisor CHECK (supervisor_id <> user_id)`
   to `users` plus a rejection test in 03 in the existing style. Add `CREATE INDEX ix_comments_item ON comments (category_id, product_id)`
   and update the index count and change log in the notes.
5. README + notes lines for G6: add `db/04_queries.sql` ("query catalogue: one example per SQL topic of the
   assignment; read-only except scratch objects it drops; run after 02") to the README file table and the
   run commands (01 → 02 → 03 → 04), and one line in the notes TDS checklist that the SQL topics are in 04.
   G6 writes the file itself; do not create it.

Keep Oracle 19c syntax (no `IF EXISTS`, no BOOLEAN, no multi-row VALUES). Rebuild under the db lock (01 → 02 → 03,
command in Common rules), check: no errors, all integrity checks 0, all rejection tests print OK, seed counts unchanged.
Then run the app's Oracle tests once under both locks (`-Dtest='Oracle*Test,Uc*Test'`) to show the schema change
breaks nothing; if compilation fails because another group is mid-edit, retry later or report. Leave the DB at seed.
Report the exact new objects so John can include them in the Data Modeler model (A-02).

---

## G6 TDS SQL query catalogue (Sonnet)

You write the SQL query catalogue required by the TDS assignment. Read the Common rules section of
`/home/jan/Documents/VSB/tretak/VIS/projekt/notes/audit-2026-11-16/fix-prompts.md` first (DB lock, seed check),
then finding A-01 in `AUDIT-2026-11-16.md`, the full list in `audit-2026-11-16/tds-compliance.md` (TDS-2, TDS-8),
`/home/jan/Documents/VSB/tretak/VIS/projekt/notes/REQ-TDS.md`, the assignment
`/home/jan/Documents/VSB/tretak/VIS/projekt/Semestrální projekt TDS I.pdf` (`pdftotext -layout`, pages 2–5), the
schema `/home/jan/Documents/VSB/tretak/TDS/db/01_schema.sql`, the seed `02_seed.sql` and `03_checks.sql` (do not
duplicate its sections, but a short repeat with a pointer is fine where a topic must be shown in 04).
Progress file: `audit-2026-11-16/fix-g6.md`.

**Files you may edit:** only the new file `/home/jan/Documents/VSB/tretak/TDS/db/04_queries.sql`. G5 adds the
README and design-notes lines for it; do not touch them.

**Requirements.**
- One section per assignment block, in assignment order (DD S15–S17, SQL S01 … S16), each with a header comment
  naming the block and lesson (e.g. `-- SQL S03 L02 NATURAL JOIN`), and every bullet of REQ-TDS.md covered by at
  least one statement with a one-line English comment saying what it shows on this schema (customers, projects,
  project_nodes, menus, menu_revisions, menu_items, products, allergens, users, comments …). Meaningful business
  questions, not `SELECT * FROM dual` filler (dual only where the topic is dual or a pure function demo).
- Oracle 19c syntax only. Runnable with sqlplus on the local DB (`docker exec -i vis-oracle sqlplus -S …`) and with
  F5 in SQL Developer: `SET` lines at the top are fine, each statement ends with `;` (PL/SQL with `/`).
- The run must leave the DB at seed: read-only SELECTs first. DML demos (INSERT VALUES/column list/INSERT SELECT,
  UPDATE, DELETE, DEFAULT, MERGE, INSERT ALL) run on `tmp_` scratch tables made by CTAS, or on real tables inside
  `SAVEPOINT … ROLLBACK TO` with a final `ROLLBACK` before any DDL (DDL commits). Never call `NEXTVAL` on schema
  sequences and never insert into real tables with a sequence DEFAULT (it advances the sequence); use scratch
  sequences. DDL demos (CREATE/ALTER/DROP/RENAME/TRUNCATE, ALTER TABLE ADD/MODIFY/DROP, SET UNUSED, COMMENT ON,
  FLASHBACK TABLE … TO BEFORE DROP with USER_RECYCLEBIN, CTAS, constraints NOT NULL/UNIQUE/FK ON DELETE
  CASCADE/SET NULL, views simple/complex/FORCE/WITH CHECK OPTION/WITH READ ONLY, sequences with all options,
  indexes, synonyms, GRANT/REVOKE incl. TO PUBLIC on a scratch table) only on `tmp_` objects, all dropped with
  `PURGE` at the end. Show own vs system constraint names from `user_constraints` (the `SYS_C…` NOT NULL ones).
- External table (SQL S08): include the full statement (CREATE DIRECTORY, ORGANIZATION EXTERNAL, TYPE ORACLE_LOADER,
  DEFAULT DIRECTORY, ACCESS PARAMETERS with RECORDS DELIMITED BY NEWLINE and FIELDS, LOCATION). If the CATERING user
  lacks the privilege locally, keep it in the file inside a clearly marked comment block with the reason and report
  it; do not change grants or run anything as SYS.
- Regular expressions (REGEXP_LIKE/REPLACE/INSTR/SUBSTR/COUNT), ROLLUP/CUBE/GROUPING SETS, set operators with ORDER
  BY, the NOT IN NULL trap, WITH clause, old-style `(+)` joins, COMMIT/ROLLBACK/SAVEPOINT are all required.
- Never mention the VIS application in the file; English comments.

Run the whole file under the db lock, fix every error, then run the seed check (Common rules) and `03_checks.sql`
(all checks 0). Run it twice to show it is re-runnable (drop scratch objects at the start too, in a PL/SQL block
that ignores ORA-00942/02289/01432 etc.). Report: a table "requirement bullet → line in 04", run result, what (if
anything) could not run locally and why.

---

## G7 Docs and hand-in packaging (Sonnet)

You fix documentation drift and build the hand-in packaging. Read the Common rules section of
`/home/jan/Documents/VSB/tretak/VIS/projekt/notes/audit-2026-11-16/fix-prompts.md` first (docs rules!), then
findings A-03, A-16, A-43…A-48, A-51 in `AUDIT-2026-11-16.md` and VIS-1…VIS-7 in `audit-2026-11-16/vis-compliance.md`.
Also `projekt/guiderails.md` (artefact form). Progress file: `audit-2026-11-16/fix-g7.md`.

**Files you may edit:** `docs/artefacts/{a3-technicka-specifikace,a4-skica-ui,a6-architektura,a7-implementace}.tex`
(in a7 only the "Odevzdaný archiv" paragraph and the demo-account sentence; the test table belongs to G8),
`docs/artefacts/diagrams/{uc-overview,act-uc08}.puml` and their `.png`, `docs/artefacts/main.tex`, new
`projekt/pack-handin.sh`, `notes/OPEN-QUESTIONS.md`, and the file mode of `app/config/db.properties`. Not
`preamble.tex` unless the title macro truly cannot take the text (it can: `\titulni{…\\[2mm]…}` works today). Do not
delete or move any existing file (clutter stays; the ZIP uses an explicit list).

**Fixes.**
1. A-03 (high). `projekt/pack-handin.sh` (bash, `set -euo pipefail`): builds `projekt/hand-in/VIS_ZAM0074_C06_IS.zip`
   from an explicit list only: `app/src`, `app/pom.xml`, `app/config/db.properties.example`, and `db/01_schema.sql`,
   `db/02_seed.sql` copied from `../TDS/db` into a temp staging dir at pack time (copy, not a link). It also copies
   `docs/artefacts/main.pdf` to `projekt/hand-in/VIS_ZAM0074_C06_A1-A7.pdf` and the existing
   `VIS_ZAM0074_C06_A5/A6/A7.pdf` next to it. It fails if `config/db.properties`, `target/` or `documents/` would be
   included, and prints the ZIP listing. Run it and check the listing (`unzip -l`). A6 step 1 (`a6:118`) names the
   `db/` folder and the order 01 then 02; A7 "Odevzdaný archiv" (`a7:275`) lists the `db/` folder; A7 names the seed
   password `heslo123` next to the demo accounts and says to change it in production (also covers A-28 note).
   No mention of TDS or the school server: call the scripts "skripty databáze".
2. A-16. `main.tex:7` → `\titulni{Kompletní dokumentace (artefakty 1 až 7)\\[2mm]Jan Zámostný (ZAM0074), skupina C06}`.
3. A-43. A3 l.169 facade list as code (`MenuService` menu a revize, `MenuExportService` export do PDF, `AuthService`
   přihlášení; compare with `business/service/*.java`); A3 l.138 `catering-is-1.0.jar`; A3 l.160 and A6 l.79:
   `Database` is a single instance that opens a JDBC connection per transaction and keeps it for the current thread
   (check `data/oracle/Database.java`).
4. A-44. A3 technology table row for the reverse proxy: nginx 1.18 nebo novější, ukončuje HTTPS s certifikátem
   společnosti, bufferuje požadavky a hlídá časové limity klientů; A6 replaces "například nginx" with "nginx".
5. A-45. `uc-overview.puml:18-19`: `usecase "05 - Spravovat zákazníky" as UC05 <<CRUD>>` and UC06 the same; keep
   the aliases used elsewhere in the file; re-render PNG.
6. A-46. `act-uc08.puml`: the 7.1 branch loops back to "Načte nejnovější revizi menu" (step 2) instead of `stop`;
   re-render PNG and look at it (Read the PNG).
7. A-47. A4 l.131: "Množství, cenu a poznámku položky pracovník změní tlačítkem \uv{Upravit položku} v dialogu (\ucref{08}, krok 3)."
8. A-48. OPEN-QUESTIONS items 15 and 16: mark answered (2026-11-16, what was done, see STATUS). Add a new question
   for the teacher (A-20): "Je souborové úložiště obsahu dokumentů za rozhraním Gateway dostatečné jako druhé datové úložiště?"
9. A-51. `chmod 600 /home/jan/Documents/VSB/tretak/VIS/projekt/app/config/db.properties`.

Compile the changed artefacts once to catch LaTeX errors (`cd docs/artefacts && latexmk -pdf VIS_ZAM0074_C06_A234.tex main.tex VIS_ZAM0074_C06_A6.tex VIS_ZAM0074_C06_A7.tex`)
and check the changed pages. Report every changed `.tex`/`.png`, the ZIP listing and the hand-in file names.

---

## G8 Doc sync and final rebuild (Sonnet, after G1–G4 and G7)

You bring the documentation back in line with the code after the fix groups. Read the Common rules section of
`/home/jan/Documents/VSB/tretak/VIS/projekt/notes/audit-2026-11-16/fix-prompts.md` first, then the reports and
progress files `audit-2026-11-16/fix-g1.md` … `fix-g4.md`, `fix-g7.md` (sections "Doc sync needed").
Progress file: `audit-2026-11-16/fix-g8.md`. Start only when the coordinator confirms G1–G4 and G7 are done.

**Files you may edit:** `docs/artefacts/{a2-funkcni-specifikace,a5-navrh-domenoveho-modelu,a6-architektura,a7-implementace}.tex`,
`docs/artefacts/diagrams/{class-*,seq-*}.puml` and their `.png`, all PDFs in `docs/artefacts`. No Java, no SQL.

**Work.**
1. For each "Doc sync needed" item: update the class diagram member, the A5 class/pattern text, the A2 `\svc{}` call
   or alternative-flow sentence (e.g. the new UC08 conflict "Nejnovější revize je pracovní …" and the node-name rule
   from G2) and A6/A7 sentences, so docs match code again. Check every member you touch against the Java source.
2. Run the full suite once under both locks (`flock $SP/db.lock flock $SP/mvn.lock env DISPLAY=:1 $M -q -B -f $POM test`,
   no `clean` unless the coordinator says no other group runs) and update the A7 test table and the "276 testů" text
   with the real counts per category (count test methods per package as the existing table does).
3. Re-render changed diagrams (`plantuml -tpng`), rebuild all PDFs:
   `latexmk -pdf VIS_ZAM0074_C06_A234.tex main.tex VIS_ZAM0074_C06_A5.tex VIS_ZAM0074_C06_A6.tex VIS_ZAM0074_C06_A7.tex`
   (and A1–A4 single files if they changed), then rerun `projekt/pack-handin.sh` so the hand-in copies are current.
Report: what changed per document, the new test counts, the PDF and ZIP file names.

---

## G9 John's decisions – code (Sonnet)

Code part of John's decisions, done after G1–G7 (single group running).
1. A-17 roles (additive): WORKER = desktop worker UI and worker actions; ADMIN = desktop admin mode only (final, not "sudo"); MANAGER = read-only `/manager` showing only projects whose responsible worker has `users.supervisor_id` = the manager. Desktop login refuses MANAGER-only. Rules in one place `business/domain/AccessPolicy`; enforced in the services that take the acting User. Seed: every manager supervises a worker. Tests for every row of the matrix.
2. A-18: 4 plain TIMESTAMP columns `DEFAULT LOCALTIMESTAMP`, notes change log, rebuild 01→03, test of `created_at` vs now.
3. A-54: item note labelled "Poznámka (vidí zákazník)".
4. A-55: `web.embedded` flag (default false) in `AppContext`; DesktopApp starts web + scheduler only when true; WebMain always.
5. UC03 reactivation: `Project.reactivate()`, `ProjectService.reactivateProject(id, User)`, button "Obnovit projekt" in EditProjectDialog; domain, service, e2e and desktop tests.
Report and progress: `fix-g9.md`.
