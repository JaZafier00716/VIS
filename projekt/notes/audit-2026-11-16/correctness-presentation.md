# Audit: correctness-presentation (2026-11-16)

## Progress
Done: read all 27 files of presentation/** (desktop 20, web 7) plus the business/data code they call (SharingService, Comment, Project, Database). Compared with A2 UC02/08/11/13/16. Nothing was run; findings come from reading the code paths.
Next: nothing outstanding.

Base path: `projekt/app/src/main/java/cz/vsb/catering/presentation/`.

## What is correct (checked, no finding)
- Archived project read-only in the UI: `MainWindow.showProject` disables create/upload, `updateButtons` disables delete, `MenuEditorWindow.readOnly` disables edit/save/share/export/copy, `CommentsWindow.showDetail` disables resolve and edit. The customer web shows a notice instead of the form (`view.readOnly()`), and the service refuses the write anyway.
- Web handlers close the exchange (`try (ex)`), have no shared mutable state except the `ConcurrentHashMap` of sessions, and a transaction is bound per thread (`Database.current` ThreadLocal), so 4 pool threads plus FX plus scheduler are safe.
- UC11 alternative flows 5.1-5.3 and 7.x are implemented (404 / 410 with a link to the current offer, 400 with field errors, expired link on POST goes to W2).
- UC13 4.1-4.3 conflict flow: `Comment.resolve` names who and when, `CommentsWindow.resolve` reloads afterwards.
- UC16 7.3: the "last admin" refusal restores the original form values.
- Executor shutdown: `WebServer.stop` and `ExpiredLinkScheduler.stop` are called from `DesktopApp.stop`; the scheduler thread is a daemon.

## Findings

### PRES-1 Manager session keeps a stale User for 8 hours
- Severity: medium
- Evidence: `ManagerHandler.java:123` `sessions.put(token, new Session(user, ...))`; `currentUser` (l.136-145) returns `s.user()` without any re-check. The MANAGER role check is only in `login` (l.115).
- Why: an administrator who deactivates a manager or removes the MANAGER role (UC16) does not cut off an already logged-in session. That person can keep reading all projects and prices until the 8 h TTL ends or the app restarts. This breaks the "MANAGER-only" decision in practice.
- Fix: keep only the user id in the session and re-read the user per request (add `AuthService.findActiveUser(id)` or reuse `UserService`). Drop the session if the user is gone, inactive, or lacks MANAGER. One extra query per request.
- Confidence: confirmed

### PRES-2 Menu editor: selection is lost after every edit, "+ Přidat položku" becomes disabled
- Severity: medium (usability, hits the main UC08 path)
- Evidence: `MenuEditorWindow.refresh()` l.226-234 builds a new `TreeItem` root and calls `table.setRoot(root)`; there is no code that restores the selection. `updateButtons` l.254 needs `selectedCategory() != null` for `addItem`.
- Why: `TreeTableView.setRoot` clears the selection. After each Command (add item, change, remove, undo) the worker has to click the category again before adding the next item (UC08 step 3 repeated). `editForComment` selects the row by hand, which suggests that the loss was noticed there only.
- Fix: in `refresh()` remember the selected value (category object / item object) before `setRoot` and re-select the `TreeItem` holding the same object afterwards (or the parent category if the item is gone).
- Confidence: confirmed by code reading (standard JavaFX behaviour of `setRoot`); not run.

### PRES-3 Closing the menu editor silently discards unsaved changes
- Severity: medium
- Evidence: `MenuEditorWindow.java:130` only `stage.setOnHidden(e -> revision.removeListener(listener))`; no `setOnCloseRequest`. Compare `switchTo` l.195, which does ask "Neuložené změny se ztratí. Pokračovat?".
- Why: inconsistent protection. Closing the window (or the main window, which owns it) loses the working copy with no question. `MainWindow.openMenu` additionally reloads the tree on hide.
- Fix: `stage.setOnCloseRequest(e -> { if (dirty && revision.isEditable() && !Ui.confirm("Neuložené změny se ztratí. Zavřít?")) e.consume(); });`
- Confidence: confirmed

### PRES-4 Project list is loaded once; search can find a project that cannot be opened
- Severity: low
- Evidence: `MainWindow.java:71` `projectBox.getItems().setAll(ctx.projects.findAllProjects())` only in the constructor and in `projectCreated`. `selectProject` l.215-218 does `findFirst().ifPresent(...)`.
- Why: UC04 search (`ProjectSearchDialog`, queries the DB) can return a project that another worker created or archived after start-up. `selectProject(p.getId())` then silently does nothing. The combo box also shows stale statuses (e.g. an archived project still looks editable until a write is refused by the service).
- Fix: in `selectProject`, if the id is not in the items, reload the list first (`projectBox.getItems().setAll(findAllProjects())`) and then select.
- Confidence: confirmed

### PRES-5 On a failed tree load the previous project's tree and buttons stay active
- Severity: low
- Evidence: `MainWindow.showProject` l.220-243: the status and detail labels are already replaced (l.222-231), then `catch (Exception e) { Ui.error(e); return; }` leaves `tree`, `table`, `root` and the enabled/disabled state of `create`/`upload` from the previous project.
- Why: the combo shows project B while the tree and `activeFolder()` still belong to project A. The worker can create or upload into A believing it is B.
- Fix: on failure clear the tree and table (`tree.setRoot(null); table.getItems().clear(); table.setUserData(null);`) and disable `create`/`upload`.
- Confidence: confirmed

### PRES-6 `showFolder` marks a folder as shown before its content is loaded
- Severity: low
- Evidence: `MainWindow.java:272-274` `table.setUserData(folder); ... ctx.documents.listContent(folder)`; the guard `folder == table.getUserData()` l.272. `listContent` is not wrapped in try/catch and runs in a selection listener.
- Why: if the load throws (DB down), the user sees a stack trace on the console only, the table keeps old content, and clicking the same folder again is ignored by the guard.
- Fix: load first into a local list inside try/catch with `Ui.error(e)`, and call `setUserData(folder)` after success.
- Confidence: confirmed

### PRES-7 Customers and Catalog: after saving a new record that does not match the search text the form stays in "new" mode
- Severity: low
- Evidence: `CustomersWindow.save` l.106-119 and `CatalogWindow.save` l.350-366: `reload(saved.getId())` only selects the row if it is in the filtered list; `editing` is set only via the selection listener (`fill`).
- Why: with a non-empty search field, a newly created customer/product is not selected, `editing` stays `null`, the form keeps the typed values, and the next click on "Uložit" creates a duplicate (or a duplicate-email error).
- Fix: after a successful save call `fill(saved)` explicitly (or clear the search field before `reload`).
- Confidence: confirmed

### PRES-8 Compare button compares revision 1 with itself
- Severity: low
- Evidence: `MenuEditorWindow.compare()` l.265-268: `newerNo = revision.getRevisionNo()` (1), `Math.max(1, newerNo - 1)` = 1, so `CompareRevisionsWindow.open(ctx, id, 1, 1)`. The button is enabled whenever at least 2 stored revisions exist (l.261).
- Why: UC10 compares a revision with the one before it; for the oldest revision there is none, and the window shows an empty diff for rev. 1 vs rev. 1 (the user can change the combos, but the default is meaningless).
- Fix: for the oldest revision use `older = revisionNo`, `newer = revisionNo + 1` (next stored revision).
- Confidence: confirmed

### PRES-9 Windows are opened without owner and can pile up; closing the main window leaves them (and the embedded web server) running
- Severity: low
- Evidence: `MainWindow.java:86-87` `new CustomersWindow(...).stage().show()` / `new CatalogWindow(...)` on every click, no `initOwner`, no reuse; `CommentsWindow` and `CompareRevisionsWindow` also have no owner (`MenuEditorWindow.openComments` l.369-374, `CompareRevisionsWindow.open`). `DesktopApp` sets implicit exit true, which only ends the app when the last window closes.
- Why: several editing windows of the same customer or product can be open at once (the last save wins, `editing` objects go stale); after the main window is closed the app, the web server and the scheduler run on until every orphan is closed.
- Fix: keep one instance per window type and just `toFront()` it, and `initOwner(stage)` for all child windows. Optionally `stage.setOnHidden(e -> Platform.exit())` on the main stage.
- Confidence: confirmed

### PRES-10 Blocking DB and file work on the FX thread; one new Oracle connection per call
- Severity: low
- Evidence: all service calls are made directly in event handlers, e.g. `MainWindow.upload` (reads and stores the file), `download`, `MenuPdfExport`, `CustomersWindow` `search.textProperty().addListener(... reload(null))` (a query on every keystroke), `CatalogWindow.fill` (photo and price history per row click). `Database.inTransaction` opens a new `DriverManager.getConnection` per call (no pool).
- Why: the window freezes during large uploads, PDF export or on a slow DB, and typing in the customer search opens one JDBC connection per character. Acceptable for a demo, but visible.
- Fix: cheapest: run the customer search on action/Enter (or a `PauseTransition` debounce). Optionally run upload/download/export with `javafx.concurrent.Task`; not needed otherwise. Document as a known limit.
- Confidence: confirmed

### PRES-11 Manager project page does many sequential DB transactions (N+1)
- Severity: low
- Evidence: `ManagerHandler.showProject` l.168-178 calls `findAllProjects()` for one id, then `collectMenus` l.180-189: per menu `openLatestRevision` + `allComments` (= `listRevisions` + one `listComments` per revision). Each call is its own transaction and, via `Database.inTransaction`, its own new connection.
- Why: the request holds one of the 4 pool threads for a long time on projects with many menus and revisions (3 concurrent managers are expected, A3). A menu without a revision would throw `ValidationException` ("Menu nemá žádnou revizi.") out of `collectMenus` and the whole page becomes 500.
- Fix: look the project up by id (`ProjectService.findById` if present) and wrap the per-menu summary in a try/catch that shows "bez revize" for that row. Real batching only if it proves slow.
- Confidence: confirmed (structure); the slowness itself not measured

### PRES-12 HTTP details: wrong or missing status codes and wording on the customer web
- Severity: low
- Evidence: `MenuHandler.handle` l.43-45: a valid `/menu/{token}` with another method (PUT, HEAD, POST to the page, GET to `/comments`) returns 404 (`ManagerHandler` returns 405 with `Allow`). l.36-37: a token with illegal characters or longer than 64 shows "Stránka nenalezena" instead of the UC11 5.1 text "Odkaz není platný, kontaktujte pracovníka". l.46-47: malformed `%` escape returns 400 but with the "nenalezena" page body. l.66 and `ManagerHandler` l.105: `readNBytes(MAX)` silently truncates an oversized body instead of answering 413.
- Why: small mismatches with the spec text and with HTTP semantics; no functional break.
- Fix: return 405 + `Allow` for known routes with the wrong method; use `MenuPage.unknownLink()` for a token that does not match the pattern; read `MAX+1` bytes and answer 413 when exceeded.
- Confidence: confirmed

### PRES-13 Desktop error dialogs show raw technical messages
- Severity: low
- Evidence: `Ui.error` l.185-193: for anything but `ConflictException`/`ValidationException` shows `e.getMessage()`; `Database.inTransaction` builds `"Chyba připojení k databázi: " + e.getMessage()` (ORA-xxxxx text). `LoginDialog` l.431-432 puts `e.getMessage()` into the label for any exception. `DesktopApp` l.46 and `ExpiredLinkScheduler` l.39 print `e.getMessage()` too.
- Why: no stack trace reaches the user (good), but Oracle error texts and class names do (`"Neočekávaná chyba (NullPointerException)"`). It is a rough edge for the defence, not a data risk in the desktop app.
- Fix: for non-business exceptions show a fixed Czech text ("Operaci se nepodařilo provést, zkuste to znovu.") and keep the details on stderr only.
- Confidence: confirmed

### PRES-14 Editor and dialog small points
- Severity: low
- Evidence / fix, each one separate:
  - `EditProjectDialog.java:38` `project.getCustomer().getName()` NPE if a project has no customer, while `MainWindow.showProject` l.225 guards `getCustomer() == null`. Fix: same null guard.
  - `EditProjectDialog.archive()` l.103-113: the filter on the "Archivovat" button archives without saving the edited form fields and closes the dialog, so edits typed before are lost silently. Fix: either call `submit()` first, or disable archive when fields differ.
  - `CatalogWindow.choosePhoto` l.339-347 calls `Files.readAllBytes` before the 100 kB limit is checked by the service; a multi-GB file would exhaust memory. Fix: check `Files.size(file)` first and show the "photo" error.
  - `CatalogWindow.fill` l.313-314: a product with a VAT rate other than 0/12/21 gets `vat = null` and the form then fails on "DPH" with no hint why. Fix: add the product's own rate to the combo items.
  - `MenuItemDialog` l.440-442: the product listener dereferences `p` (`p.getCurrentPrice()`) without a null check.
  - `ShareDialog` l.338: `QrCode.image` throws `IllegalArgumentException` in the constructor; `MenuEditorWindow.share()` has already created and locked the link by then, so the worker sees an error and no link. Fix: catch around the QR only and show the dialog without it.
  - `CommentsWindow` loads comments once; comments that customers add while the window is open do not appear until it is reopened (UC13 2). Fix: a "Obnovit" button calling `reload(selectedId)`.
- Confidence: confirmed (each by reading the lines above)

## Seen outside my area
- Security: `ManagerHandler.login` has no throttling/lockout and runs PBKDF2 on one of only 4 pool threads, so a few parallel login attempts (or slow-client connections, no read timeout on `HttpServer`) can stall the whole web server including the customer pages.
- Security: manager cookie has no `Secure` flag (`ManagerHandler.java:124`); fine on plain HTTP locally, needs adding behind the HTTPS reverse proxy. No CSRF token on login/logout (mitigated by SameSite=Strict).
- Security: share token is the only secret of the customer page; `MenuHandler` does not rate-limit unknown tokens (token length/entropy is set in `SharingService`, not checked by me).
- Data/business: `Database.inTransaction` opens a new JDBC connection per business call (no pool); see PRES-10 and PRES-11 for the effect.
- Autologin: `-Dcatering.autologin=user:password` in `DesktopApp` puts a password on the command line; documented as dev-only, but it is shipped code.

## Summary
Counts: high 0, medium 3 (PRES-1, PRES-2, PRES-3), low 11 (PRES-4 to PRES-14; PRES-14 bundles 7 small points).
