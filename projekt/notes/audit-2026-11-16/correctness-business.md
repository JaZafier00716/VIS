# Audit: correctness-business (business/** vs A2 UC02, 08, 11, 13, 16 + short UC)

## Progress
Done: all of business/{domain,command,pricing,port,service} read; A2 UC02/08/11/13/16 alt flows compared; mapper SQL (OracleMenuMapper, tree query) and schema column sizes checked where the business rule depends on them. No tests run (findings are from code paths).
Next: nothing planned. Not covered: presentation, data-layer details beyond what is cited.

Rules verified OK (no finding): share link 14 days + older active links deactivated in the same tx; comment <=2000, author <=100, resolution note <=200; password >=8 (create and update); last active admin check on the mutated list; WORKING overwrite keeps number, SHARED/historical -> copy N+1 (latest+1); archived project refused in createFolder/createDocument/deleteNodes/createMenu/saveRevision/shareRevision/addComment/resolveComment/update/archive; UC19 all-or-nothing inside one tx on a fresh tree, files deleted after commit; double resolve guarded by `WHERE resolved='N'`; UC18 in one tx; Money rounding HALF_UP per line, VAT factor exact.

### BUS-1 In-memory revision left SHARED after a failed share (and observers already told)
- Severity: medium
- Evidence: SharingService.java:198-201 `revision.markShared(); menus.updateState(revision);` and :202-210 links update/insert, all inside `tx.inTransaction`. MenuRevision.java:168-172 `markShared()` sets `state = SHARED; changed();`.
- Why: if `updateState`, `links.update` or `links.insert` throws, the DB rolls back (still WORKING) but the object the editor holds is SHARED, read-only, and listeners were notified. The editor then shows a shared revision with no link; the user must reload. Violates "in-memory objects consistent after rollback" and UC11 "Stav systému".
- Fix: do the DB work first, change the object after commit (like `markStored()` in saveRevision). E.g. in tx call `menus.updateState(revision.getMenuId(), revisionNo, SHARED)` (or pass the state), and call `revision.markShared()` after `inTransaction` returns. Keep `validate()` before the tx.
- Confidence: confirmed (code path)

### BUS-2 A working copy of an older revision can be saved while the newest revision is WORKING (two WORKING revisions, older one orphaned)
- Severity: medium
- Evidence: MenuRevision.java:159-165 `createWorkingCopy()` allowed for any non-editable (historical) revision; MenuService.saveRevision insert branch :124-129 only checks `latest.getRevisionNo() != revision.getRevisionNo() - 1`, not `latest.getState()`. UI path: MenuEditorWindow.java:88 / :380-382 offers the copy button for historical revisions ("Starší revize ... Pracovní kopie se uloží jako nová revize za nejnovější").
- Why: with latest = r3 WORKING, opening r1 and "Vytvořit pracovní kopii" saves r4 WORKING; r3 stays WORKING but is no longer newest, cannot be edited or shared any more (Conflict) and its unsaved/saved work is silently buried. A2 UC08: "Pracovní může být vždy jen nejnovější revize menu".
- Fix: in the insert branch require `latest.getState() == RevisionState.SHARED`, otherwise ConflictException with a Czech message ("Nejnovější revize je pracovní, upravte ji nebo ji nejprve sdílejte"); better also refuse the copy in `MenuService.createWorkingCopy` when the revision is historical and the newest is WORKING (needs the latest state, e.g. pass it into `markHistorical`).
- Confidence: confirmed (code path; not run)

### BUS-3 Concurrent edits are only checked by a read-then-write, not atomically (share vs save, share vs overwrite)
- Severity: medium
- Evidence: OracleMenuMapper.java:100-113 `overwriteRevision` UPDATE has no `AND state = 'WORKING'`; sharing (`updateState`, :116-119) does not change `created_at`, so the optimistic `created_at = loadedSavedAt` guard does not notice it. MenuService.java:118-123 and SharingService.java:195-201 check `latest` in an earlier statement with no `FOR UPDATE`.
- Why: A saves while B shares in the same instant -> A's UPDATE and the DELETE/INSERT of categories rewrite a revision already SHARED and linked to the customer (A2: shared revision stays unchanged). Also `shareRevision` never compares `getLoadedSavedAt()`: if worker B overwrote the WORKING revision after A opened it, A validates and shares B's content (A validated its own in-memory copy), without the UC08 7.1 style conflict.
- Fix: add `AND state = 'WORKING'` to the overwrite UPDATE (0 rows -> ConflictException, already handled); in `updateState` use `WHERE ... AND state='WORKING'` and check the row count; in shareRevision compare `created_at` with `revision.getLoadedSavedAt()` (new mapper method or a header re-read) and throw ConflictException on mismatch.
- Confidence: confirmed by reading (race itself not reproduced)

### BUS-4 Unique/PK violations from races surface as raw DataAccessException, not the A2 alternative flows
- Severity: low
- Evidence: Jdbc/Database have no ORA-00001 mapping (grep for 00001/getErrorCode in data/oracle: none). Service paths: MenuService.saveRevision insert branch (two workers saving copy r3 -> PK (menu_id, revision_no)), DocumentService.createFolder/createDocument and MenuService.createMenu (name clash created by another worker; the in-memory `Folder.add` check works on a stale tree), DocumentService.insertOrUndo :140-147.
- Why: UC08 7.1 / UC02 6.1, 8.1 promise ConflictException / DuplicateNameException with a suggested name; under a race the user gets a technical error message.
- Fix: in `Database`/`Jdbc` translate SQLException error 1 into a `ConstraintViolationException` (data) and catch it in the three services (-> ConflictException / DuplicateNameException with `parent.suggestFreeName`). Keep it small: only the two sites above.
- Confidence: suspected (not reproduced)

### BUS-5 Input limits that the DB enforces are not validated in the business layer (raw ORA errors on save)
- Severity: medium
- Evidence: schema 01_schema.sql:224 `menu_categories.name VARCHAR2(60)`, :237-239 `menu_items.quantity NUMBER(8,2)`, `unit_price NUMBER(10,2)`, `note VARCHAR2(200)`; products.current_price NUMBER(10,2); users.full_name 100, email 254. Business: MenuRevision.addCategory:51-59 (only blank/duplicate), findProblems:135-151 (only `quantity>0`, `price>=0`), MenuRevision.changeItem/addItem take any note, Product.update:339-350 (no price upper limit), User.create:229-240 (no fullName/email length).
- Why: category name 61+ chars, note >200, quantity >= 1 000 000 or price >= 100 000 000 fail only inside saveRevision with ORA-12899/ORA-01438 (rolled back, but the user loses the "UC08 6.2 highlight the faulty item" flow). Worse, quantity 0.004 passes `signum()>0` but is stored as 0.00 -> CHECK violation, and 0.005 is stored as 0.01 so the total shown before save (qty x price unrounded) differs from the total after reload.
- Fix: in `findProblems` add: category name <=60, note <=200, quantity scale <=2 and `< 1_000_000`, unit price `< 100_000_000`; in Product.update price < 100_000_000; User.create fullName <=100, email <=254. Messages in Czech via the existing problems list/fieldErrors.
- Confidence: confirmed for the missing checks (DB limits read from schema); ORA error not run

### BUS-6 No role check for worker functions in the services; a MANAGER-only account can use the whole desktop app
- Severity: medium
- Evidence: only UserService has `requireAdmin` (UserService.java:62-65). ProjectService, DocumentService, MenuService, SharingService (resolveComment takes `User worker` but only uses its id), CatalogService, CustomerService take no actor / check no role. Presentation: grep for `Role.`/`hasRole` shows only MainWindow (admin button) and ManagerHandler; desktop login (AuthService.login) checks only `isActive`.
- Why: A2 actors: Pracovník does UC02-UC14, UC19; Manažer is read-only (UC15, web). A user with only the MANAGER role (or an admin without WORKER) can log into the desktop app and create/edit/archive projects, edit the catalogue, share menus, resolve comments. Also a deactivated user keeps working until logout (see BUS-7). Attack detail goes to security.
- Fix: KISS: AuthService.login for the desktop requires WORKER or ADMIN (decision needed whether admin without WORKER may do worker functions; DECISIONS.md does not say), or add `User.requireRole(Role.WORKER)` calls in the services that already receive a User (`createMenu`, `saveRevision`, `resolveComment`) and a `User actor` parameter elsewhere.
- Confidence: confirmed (no check found); policy question for John

### BUS-7 UserService.requireAdmin trusts the caller's cached User object
- Severity: low
- Evidence: UserService.java:62-65 `admin.isActiveAdmin()` on the object passed in; findAllUsers/createUser/updateUser never reload it.
- Why: an admin who was deactivated or lost the role by another admin (UC16 7.4/7.5 flow) keeps full user management until the app restarts; same applies to all other services if BUS-6 is fixed with cached objects.
- Fix: in `requireAdmin` re-read the actor inside the tx (`users.findByUsername(admin.getUsername())` and test `isActiveAdmin()`); cheap, one query per admin operation.
- Confidence: confirmed (code path)

### BUS-8 Last-active-admin check is not atomic
- Severity: low
- Evidence: UserService.updateUser :44-56 reads all users, mutates, counts, then `users.update(user)` without a lock; no DB constraint.
- Why: two admins demoting each other at the same moment both pass the count and the system ends with zero active admins (UC16 7.1 violated). Window is tiny and the project is a semester demo.
- Fix: `SELECT ... FOR UPDATE` on the admin role rows in UserMapper, or ignore and note as a known shortcut (`// shortcut:` comment).
- Confidence: suspected

### BUS-9 Document/node names may contain path separators and ".."; download writes outside the chosen folder
- Severity: low
- Evidence: NodeCreator.java:13-21 checks only blank and length; DocumentService.downloadDocuments :88 `targetDir.resolve(d.getName())`. Export names come from the menu name (MenuExportService.fileName), which can contain "/".
- Why: a name like `../x.pdf` or `a/b.pdf` makes `retrieve` write to a parent directory (path traversal on the worker's own machine) or fail with NoSuchFile; also an existing file of the same name is overwritten without asking.
- Fix: reject `/`, `\`, `..`-only and control characters in `NodeCreator.createIn` (Czech message), and in `downloadDocuments` use `targetDir.resolve(name).normalize()` and check `startsWith(targetDir)`.
- Confidence: confirmed (code); exploitability only by the user themself

### BUS-10 Shared revisions are not immutable in what the customer sees: product name, allergens and VAT come from the current catalogue
- Severity: low
- Evidence: OracleMenuMapper.java:80 `OracleProductMapper.read(rs)` builds MenuItem.product from the live products row; PriceWithVat.java:504 uses `item.getProduct().getVatRate()`; only quantity and unit price are stored per item (schema :237-239).
- Why: UC08 says the version the customer saw "zůstane zachována". After a catalogue edit (VAT 12 -> 21, new allergen, renamed dish) the "s DPH" total, allergens and names of an already shared/older revision change, and UC10 comparison of two revisions shows no difference although the with-VAT totals differ. Known simplification, but the doc claims the revision is frozen.
- Fix: either document it in A2/DECISIONS as a shortcut, or store `vat_rate` per menu_items row (TDS change). Minimum: mention in A2 UC08 / limits section.
- Confidence: confirmed (code); decision for John

### BUS-11 Check-then-write for the archived rule is outside the write transaction in createMenu/createFolder/createDocument
- Severity: low
- Evidence: MenuService.createMenu :49 and DocumentService.createFolder :39 / createDocument :56 call `requireOpenProject` (its own tx, :141-143 / :136-138) and only afterwards run the INSERT tx; deleteNodes and saveRevision/share/comment do the check inside the tx.
- Why: a project archived between the two statements still gets the folder/menu/document. Tiny window, but the "fully read-only incl. every write path" rule is then not guaranteed.
- Fix: move `requireOpenProject` inside the same `tx.inTransaction` as the insert (Database joins nested transactions, Database.java:39) - one-line moves.
- Confidence: confirmed (code); race not reproduced

### BUS-12 Observer/undo: a listener exception leaves a changed model that is not undoable; failed Money inputs
- Severity: low
- Evidence: MenuRevision.changed() :211-213 is called after mutation; MenuEditor.execute :146-150 pushes the command only after `command.execute()` returns. A throwing listener (UI refresh) -> state changed, command not on the undo stack.
- Why: the editor shows/keeps a change that cannot be undone and is not logged. Also `AddItemCommand`/`changeItem` accept null quantity or price (not validated until save), and `PriceStrategy.lineTotal` then throws NPE during the live recalculation (UC08 step 4) instead of a validation message.
- Fix: in MenuEditor push the command in a `try/finally`-like way (push before notifying) or catch listener exceptions in `changed()`; require non-null quantity and unit price in `MenuRevision.addItem/changeItem` (throw ValidationException).
- Confidence: suspected (listener failure) / confirmed (null path reachable only if a caller passes null; MenuItemDialog never does)

### BUS-13 saveRevision mutates the revision before the DB write
- Severity: low
- Evidence: MenuService.java:122 and :127 `revision.recordSave(...)` before `overwriteRevision`/`insertRevision`; on ConflictException or DB error the object keeps the new note/createdBy/createdAt.
- Why: the revision list label shows the unsaved timestamp until the user reloads; with UC08 7.3 the editor reloads anyway, so impact is cosmetic. `loadedSavedAt` is not changed, so the optimistic check stays correct.
- Fix: compute `savedAt` locally, pass to the mapper, call `recordSave` + `markStored` after commit (same as BUS-1).
- Confidence: confirmed (code), cosmetic

## Seen outside my area
- AuthService.login: unknown username returns before any PBKDF2 work (fast) while an existing user costs ~65k iterations: timing user enumeration (security).
- PasswordHasher.matches: a malformed stored hash (`parseHex`/`parseInt`) throws IllegalArgumentException/NumberFormatException instead of "wrong password" (data/security).
- OracleProjectNodeMapper TREE query uses `SYSTIMESTAMP` for "active link" while the services use the app Clock; same result unless clocks differ (data).
- Deleting a folder while another worker adds a child, or deleting a node twice (`selected` with duplicates), hits an FK/0-row path with no friendly message (data/presentation).

## Summary
high 0 / medium 5 (BUS-1, 2, 3, 5, 6) / low 8 (BUS-4, 7, 8, 9, 10, 11, 12, 13)
