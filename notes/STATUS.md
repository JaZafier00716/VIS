# Status

Single source of truth for progress. Last update: 2026-11-13 (implementation session).

## Done

1. **Artefacts 1–4** written and handed in (`VIS_ZAM0074_C06_A234.pdf`). A5–A7 `.tex` files exist as
   skeletons with TODOs (only the A5 sequence diagrams are filled in). A2 now details UC02, UC08, UC11, UC13, UC16 (UC10 was swapped for UC13).
2. **Oracle schema** in `TDS/db/` – changed 2026-11-13 for the app (approved by John):
   - `project_nodes` accepts the `MENU` subtype
   - `menus.node_id` (FK, unique) links a menu to its MENU node; `uq_menus_name` dropped
   - `comments.resolution_note/resolved_by/resolved_at` with `ck_comments_resolution`
   - seed creates a MENU node per menu
   - `03_checks.sql`: 2 new rejection tests

   Re-run 01→02→03: 0 violations, 10× OK. `tds-design-notes.md` has a change log.
3. **Java app** in `VIS/projekt/app` (Maven, Java 21, JavaFX 21, ojdbc11, ZXing core, JUnit 5).
   Layers as folders (John's wish):
   - `presentation.{desktop,web}` → `business.{domain,command,pricing,port,service}` ← `data.{oracle,file}`
   - composition root `AppContext`
   - `business.port` holds the mapper interfaces and `DocumentStorage`; DAL implements them (dependency inversion, cv08)

   Use cases implemented with all alternative flows: UC01 login, UC02, UC08, UC11 (desktop share + web comment), UC13, UC16, UC17 admin mode, UC19 delete.
   Two storages: Oracle via Data Mappers and the document folder via `FileDocumentStorage` (Gateway).

   | Pattern | Where |
   |---|---|
   | Layers | the three layer packages |
   | Domain Model | `business.domain` |
   | Data Mapper | `data.oracle.Oracle*Mapper` |
   | STI | `project_nodes` |
   | Identity Field | `DomainObject.id` |
   | Dependent Mapping | categories/items in `OracleMenuMapper` |
   | Lazy Load | `MenuRevision.getComments` |
   | Layer Supertype | `DomainObject` |
   | Value Object | `Money` |
   | Special Case | `UnknownShareLink` |
   | Gateway | `DocumentStorage` / `FileDocumentStorage` |
   | Service Stub | `data.file.InMemoryDocumentStorage` (tests) |
   | Transactions | `Database.inTransaction` |
   | Composite | `ProjectNode`/`Folder`/`Document`/`Menu` |
   | Factory | `data.DataAccessFactory` |
   | Factory Method | `NodeCreator` + `FolderCreator`/`MenuCreator`/`DocumentCreator` |
   | Singleton | `data.oracle.Database` |
   | Facade | `business.service.*Service` |
   | Strategy | `business.pricing.PriceStrategy` (s/bez DPH, desktop + web `?dph=1`) |
   | Observer | `MenuRevisionListener`, which refreshes the editor totals |
   | Command | `business.command.*Command` + `MenuEditor` (undo "Zpět") |
4. **Tests** – `mvn clean test` (2026-11-13): **192 tests, 0 failures, BUILD SUCCESS**:
   - business unit tests 125 (fakes in `src/test/.../business/fake`, Service Stub storage)
   - `FileDocumentStorageTest` 4
   - Oracle mapper tests 12
   - Oracle end-to-end UC tests 31 (`business/e2e`: UC02 6, UC08 5, UC11 6, UC13 4, UC16 5, UC19 5; each asserts the post-state with JDBC)
   - web `WebServerTest` 5 (real HTTP + Oracle)
   - desktop `DesktopFlowTest` 11 + `UsersWindowTest` 2 (real JavaFX windows + Oracle; need a display, otherwise skipped)
   - `QrCodeTest` 2

   All Oracle tests create their own data and delete it; afterwards the DB is at the seed counts (10 projects, 77 nodes, 10 users, 12 menus).
5. GUI launched; screenshots in `VIS/projekt/app/screenshots/` (main, menu editor, comments, users).

## Session 2026-11-15 (John: "continue the implementation with subagents") – finished

- **All 19 UCs implemented.** The new ones:
  - UC03 edit/archive project (`EditProjectDialog`)
  - UC04 search (`ProjectSearchDialog`, `ProjectService.searchProjects`)
  - UC05 customers (`CustomerService`, `CustomersWindow`)
  - UC06 catalogue with allergens, photo BLOB ≤ 100 kB and price history (`CatalogService`, `CatalogWindow`)
  - UC09 older revisions are read-only (`MenuRevision.markHistorical`)
  - UC10 compare revisions (`RevisionComparison`, `CompareRevisionsWindow`, green/red/amber rows)
  - UC14 PDF export (`MenuDocumentRenderer` port, `PdfMenuRenderer` with PDFBox 3.0.3 + embedded DejaVu fonts, `MenuExportService`; stored as a project document)
  - UC15 manager web, read only (`/manager`, MANAGER role, HttpOnly session cookie)
  - UC18 daily expiry of share links (`SharingService.deactivateExpiredLinks`, `ExpiredLinkScheduler` with `ScheduledExecutorService`, started by DesktopApp and WebMain)
- PBKDF2 passwords (schema `users.password_hash` VARCHAR2(200), seed precomputed for `heslo123`).
- Optimistic check on overwriting a WORKING revision (UC08 7.1).
- `mvn clean test` on a freshly rebuilt DB: **268 tests, 0 failures**. Afterwards the DB is at seed counts.
- Screenshots in `projekt/app/screenshots/`: desktop-{main, menu-editor, comments, users, customers, catalog, compare, edit-project}.png, web-{manager-list, manager-project, manager-menu, customer-menu}.png, sample-menu.pdf + sample-menu-pdf.png.
- Note: starting the app runs UC18 at once, so expired-but-active seed links become inactive (correct behaviour). For the UC11 alternative-flow demo this does not matter (W2 still shows). Re-run 02_seed to get the original flags back.

## Done after John's answers to Q22–24 (2026-11-15)

- **Archived project is fully read-only (UC03).** `Project.requireNotArchived()` is checked by DocumentService, MenuService, SharingService (share, resolve, customer comment), and the PDF export goes through `createDocument`, so it is covered too.
  - Desktop: write buttons disabled, menu editor read-only with a banner.
  - Web W1: comment form hidden.
- **Entire UI in Czech.** Units are shown as ks/porce/kg/l/sada; dialog buttons OK/Zrušit; Czech table placeholders and error texts.
  - Still English, out of our control: the JavaFX text-field context menu and the native file chooser (OS locale).
- `mvn clean test`: **276 tests, 0 failures**. The DB is at seed counts; share link flags were restored.
- Screenshots refreshed, plus the new `desktop-archived.png`.

## A5–A7 documentation – done (2026-11-16)

- Backup made before the work: `projekt/docs-backup-2026-11-15`.
- **A5** `a5-navrh-domenoveho-modelu.tex` → `VIS_ZAM0074_C06_A5.pdf`, 28 pages:
  - 6 business-layer class diagrams (`class-domain-project/menu/users`, `class-services`, `class-ports`, `class-gof`) + DAL diagram `class-dal`, each class described.
  - 9 sequence diagrams redone from the code (`seq-uc02-a/b/c`, `seq-uc08-a/b`, `seq-uc11-a/b`, `seq-uc13`, `seq-uc16`).
  - 17 enterprise + 8 GoF patterns, each with its class.
- **A6** `a6-architektura.tex` → `VIS_ZAM0074_C06_A6.pdf`, 11 pages:
  - component diagrams `component`, `component-data`
  - deployment `deployment-a6`
  - installation steps
- **A7** `a7-implementace.tex` → `VIS_ZAM0074_C06_A7.pdf`, 15 pages:
  - 19 UCs with demo of main and alternative flows and the storage check
  - 12 screenshots
  - storages, tests (276), build and run
- **`main.tex`** now includes A5–A7. `main.pdf` has 90 pages, 0 errors, 0 undefined references. 9 overfull warnings, likely in the existing A1–A4 (the separate A5–A7 builds have none).
- Doc rules checked in A5–A7: no semicolons, no dashes inside sentences, no TDS mentions.
- **A2/A3/A4 aligned to the code** (John 2026-11-16: "change the artefacts"):
  - A3: WORKING/SHARED states, HTTPS via reverse proxy (app HTTP 8080), jar `catering-is.jar`, `deployment.puml` redrawn (proxy node).
  - A2: UC02 6.2 rename dialog, UC08 step 4 `MenuEditor.execute()` + `ChangeItemCommand`, UC13 step 1 button text.
  - A4: matching sentences (D3 rename dialog, comments button).
  - Activity diagrams act-uc02/act-uc13 re-rendered.
- The 9 overfull lines were in A5–A7 (not A1–A4) and are fixed.
- **Rebuilt 2026-11-16, all with 0 errors / 0 undefined references / 0 overfull:**

  | PDF | Pages |
  |---|---|
  | A1 | 4 |
  | A2 | 21 |
  | A3 | 10 |
  | A4 | 12 |
  | A234 | 39 |
  | A5 | 28 |
  | A6 | 11 |
  | A7 | 15 |
  | main | 90 |

## Audit 2026-11-16 – done

- **Report:** `projekt/notes/AUDIT-2026-11-16.md`. 70 findings (high 5, medium 15, low 50): 46 fixed, 9 decisions (7 applied, A-02 + A-20 open for John), 15 won't fix with reasons. Area files and fix progress are in `audit-2026-11-16/`.
- **Tests:** `mvn clean test` gives 330 tests, 0 failures. JaCoCo before the fixes: 88.8 % lines, 79.4 % branches.
- **Roles (John):**
  - roles are additive, rules in `AccessPolicy`
  - W = desktop
  - A = admin mode only
  - M = `/manager` with only their own workers' projects (`supervisor_id`); M-only is refused in the desktop
  - project reactivation ("Obnovit projekt")
- **TDS:**
  - scripts pass: 8 integrity checks = 0, 11 rejection tests OK
  - new `04_queries.sql`, which covers every SQL category
  - LOCALTIMESTAMP defaults
  - self-supervisor check, `ix_comments_item`
  - seed org chart: mkral manages jzamostny
- **Docs:** A2–A7 synced with the code and John's decisions. All PDFs build with 0 errors, 0 undefined references and 0 overfull boxes. Pages: A1 4, A2 22, A3 11, A4 12, A234 41, A5 28, A6 11, A7 15, main 92.
- **Hand-in:** `projekt/pack-handin.sh` → `projekt/hand-in/` with `VIS_ZAM0074_C06_IS.zip` (229 files: src, pom, config example, db/01+02), `VIS_ZAM0074_C06_A1-A7.pdf`, A5/A6/A7 PDFs.
- **Open for John:**
  - draw the Data Modeler models (A-02)
  - ask the teacher about the folder storage before 5.12 (A-20, OPEN-QUESTIONS 25)
  - run 01→04 once on dbsys before the TDS hand-in
  - external table and public synonym in 04 need privileges there

## Next

1. **John reviews** the app (run it, see below) and answers the remaining `OPEN-QUESTIONS.md` items 15, 16, 18, 19, 21 (A3 wording vs code).
2. **Artefacts 5–6** (class diagrams of the real code incl. separate DAL diagram, sequence diagrams,
   component and deployment diagrams) and **A7**, written from the code in `projekt/app`.
3. **TDS SQL query catalogue** (`TDS/db/04_queries.sql`).
4. **Final hand-in** by 05.12.2026: PDF of all docs + zip of the IS (no `target/`, no `config/db.properties`).


## Rebuild and rerun

- Env: `export JAVA_HOME=~/.jdks/openjdk-27.0.1.0.3` (system Java 21 is a JRE) and
  `M=/home/jan/Applications/idea-IU-262.10968.63/plugins/maven-plugin/lib/maven3/bin/mvn`.
- DB: `systemctl --user start docker-desktop`, then `DOCKER_CONTEXT=desktop-linux docker start vis-oracle`
  (wait for "DATABASE IS READY TO USE!"). Rebuild the schema: see `TDS/README.md` (01 → 02 → 03).
- App config: `projekt/app/config/db.properties` (git-ignored; template `db.properties.example`).
- Tests: `cd projekt/app && $M clean test` (Oracle must run; desktop tests need a display, else skipped).
- Desktop app (also starts the customer web on :8080): `$M javafx:run`.
  Dev only: `-Dcatering.autologin=mkral:heslo123`, `-Dcatering.screenshot=<dir>` (saves PNGs and exits).
- Web only: `$M exec:java`. Then open the link from "Sdílet se zákazníkem", e.g. `http://localhost:8080/menu/<token>`.
- Seed logins: password `heslo123` (admins: mkral, jzamostny, kpokorny).
- PDF: `cd projekt/docs/artefacts && latexmk -pdf VIS_ZAM0074_C06_A234.tex main.tex`; diagram: `plantuml -tpng diagrams/<name>.puml`.

Deadlines: **A5–A7 due 05.12.2026 23:59** to martin.radvansky1@vsb.cz, one file per artefact plus one
final PDF with all artefacts (cv08 p.26).
- 2026-11-17: `cv3` is a plain folder in the VIS repo (commit `78a543b`). Its own git history is backed up in `../cv3-git-backup-2026-11-17.bundle` and `../cv3-dotgit-original-2026-11-17`. The VIS history was also cleaned of backups, hand-in and `.idea` (force-push `db0eb94`, `.git` 97 MB → 79 MB; backup `../VIS-backup-2026-11-17b.bundle`).
