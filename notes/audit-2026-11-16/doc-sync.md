# Doc sync after fix groups G1–G4, G9 and John's decisions (2026-11-16)

The code is the source of truth. Verify every item against the code (grep) before writing it.

## Code changes to reflect

### G1 revisions
- **Working copies.** A working copy is refused (ConflictException "Nejnovější revize je pracovní. Upravte ji, nebo ji nejprve sdílejte.") while the newest revision is WORKING:
  - `MenuService.createWorkingCopy` checks the latest header.
  - The `saveRevision` insert branch requires the latest revision to be SHARED.
  - Affects A2 UC08 2.x / 7.x and UC09.
- **Atomic share and overwrite.** Both are guarded by state/`created_at`, so a stale share gives ConflictException (UC08 7.1, UC11 2).
  - `MenuMapper.updateState(MenuRevision)` is replaced by `boolean markShared(long menuId, int revisionNo, LocalDateTime loadedSavedAt)`.
  - `OracleMenuMapper.overwriteRevision` has `AND state='WORKING'`.
- **In-memory state.**
  - `MenuRevision.snapshotForSave(note, userId, savedAt)` is new. The revision object changes only after commit.
  - `SharingService` calls `markShared()` after commit.
- **Menu limits.**
  - Category name ≤ 60, item note ≤ 200.
  - Quantity: 2 decimals, < 1 000 000. Unit price < 100 000 000.
  - A null quantity or price is refused.
  - Product price: 2 decimals, < 100 000 000.
  - User full name ≤ 100, e-mail ≤ 254.

### G2 documents
- **Node names.**
  - `NodeCreator.createIn` rejects names containing `/`, `\` or control characters, and the names `.` and `..` (UC02 name rules).
  - Downloads never overwrite: same names are saved as `name (2).ext`. A bad stored name is refused.
- **Storage.** Storage keys are ASCII (accents stripped). `FileDocumentStorage.store` writes a tmp file and then moves it atomically.
- **Data layer.**
  - `Jdbc` maps a unique/PK violation to ConflictException.
  - `Database` has a login timeout of 5 s, and a failed rollback is attached to the original error.
- **PDF.** The PDF sanitises unsupported characters, wraps long titles and categories, and repeats the table header after a page break.

### G3 web
- **Manager session.** The session keeps only the user id and re-reads the user on every request (`AuthService.findActiveUser(long)`). A deactivated or demoted manager loses access at once.
- **Login.**
  - After 5 failures in 10 minutes per user name it answers 429.
  - An unknown user costs the same PBKDF2 time.
  - The cookie gets `Secure` when `web.baseUrl` is https.
- **HTTP behaviour.**
  - Security headers: CSP, nosniff, no-referrer.
  - Wrong method gives 405 with `Allow`, a malformed escape gives 400, an oversized body gives 413.
  - Request and response timeouts are 30 s.
- **Other.**
  - `UserService.requireAdmin` re-reads the actor from the DB.
  - The manager list uses `ProjectService` search.
  - `AppContext.baseUrl` is new.

### G4 desktop
- **Menu editor.** It keeps the selection after edits and asks before closing with unsaved changes.
- **Edit project dialog.** It asks before archiving with unsaved edits.
- **Comments window.** It has an "Obnovit" button.
- **Other windows.**
  - The customer search is debounced.
  - Catalog and customer windows are single-instance and owned by the main window.
- **Compare.** "Porovnat revize" never compares a revision with itself.
- **Error dialogs.** Technical errors show a fixed Czech text. Validation and conflict texts stay as they are.

### G9 roles and decisions
- **Roles are additive**, with all rules in `business/domain/AccessPolicy`:
  - `canUseDesktopWorker`, `canUseAdminMode`, `canUseManagerWeb`, `canLoginToDesktop`, `requireWorker`, `requireManager`.

  | Roles | Desktop | /manager |
  |---|---|---|
  | W | worker | no |
  | A | admin mode only, straight to users window | no |
  | M | login refused ("Účet manažera má přístup jen k webovému přehledu (adresa /manager).") | own workers' projects |
  | W+A | worker + admin switch | no |
  | W+M | worker | own workers' projects |
  | A+M | admin mode only | own workers' projects |
  | W+A+M | worker + admin switch | own workers' projects |

  - "Own workers' projects" means projects whose responsible worker has `users.supervisor_id` = the manager. Others give 404; nobody reporting gives an empty list.
- **Service gates.**
  - `AuthService.loginDesktop` is new.
  - `ProjectService.searchManagedProjects(manager, text)`, `findManagedProject(manager, id)` and `reactivateProject(id, user)` are new, as are `ProjectMapper.findBySupervisor`, `MenuService.projectIdOf` and `Project.reactivate()`.
  - `createProject`, `createMenu`, `saveRevision` and `resolveComment` require WORKER.
  - Service methods without a User parameter are gated by the login plus the UI. Say this honestly.
- **UC03 reactivation.** "Obnovit projekt" in `EditProjectDialog(projects, project, user)` turns ARCHIVED into IN_PROGRESS after a confirmation, and everything becomes writable again.
- **Seed org chart.** mkral manages jzamostny (projects 1, 4, 10). Others: tsvoboda 2 projects, ehorakova 4, mdvorakova 1. Manager-only accounts cannot log in to the desktop. The worker demo account is jzamostny (ADMIN+WORKER).
- **A-18.** The 4 plain TIMESTAMP columns default to LOCALTIMESTAMP, so times are no longer shifted.
- **A-54.** The item note is labelled "Poznámka (vidí zákazník)".
- **A-55.** The config flag `web.embedded` defaults to false. Only the app server (`WebMain`) runs the web server and the scheduler. The local demo config has it true.
- **Tests: 330**, all green. New classes: RoleMatrixTest, DesktopRolesTest, AppContextTest, CreatedAtTimeZoneTest, DesktopFixesTest, plus others. Recount `@Test` per group for A7.

## John's documentation decisions
- **A-19.** In A6 the reverse proxy forwards only `/menu/` to the public. `/manager` is reachable only from the company network (VPN/LAN).
- **A-20.** Keep the folder storage (Gateway). This is already in OPEN-QUESTIONS 25 (ask the teacher). No artefact change beyond what is there.
- **A-52.** Keep UC11 5.3: an expired link offers the current link. A2 UC11, the A4 web screens (W2), the A5 sequence diagram seq-uc11-b and A7 must describe it consistently.
- **A-53.** Document as a simplification in A2 UC08 (and A3 if it describes revisions): a revision stores quantity and unit price per item, while product name, allergens and VAT rate are read from the current catalogue, so a shared revision shows current catalogue names and allergens.
