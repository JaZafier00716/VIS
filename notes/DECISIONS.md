# Decisions

Single source of truth for agreed decisions (2026-10-29). Read before changing architecture,
technology, patterns or the document structure; change a decision only with John's OK.
"Accepted" = Claude recommended, John agreed. "Coordinator" = set in the project assistant's brief,
which said John approved it.

## Principles

| Decision | Reason | Who |
|---|---|---|
| KISS and SOLID, take the easier way | semester project, must be defensible in VIS and TDS, not production code | John |
| One shared domain: catering IS from the Vision (`a1-vize.tex`) | Vision already submitted; fits the TDS checklist | Accepted |
| cv3 (furniture lab) is reference only, no code copied | different domain; lab work is graded separately | Accepted |

## Technology

| Decision | Reason | Who |
|---|---|---|
| Java 21 | John knows Java from cv3 | Accepted |
| JavaFX 21 desktop app for workers and admin mode | John's choice | John |
| Customer web UI on JDK `com.sun.net.httpserver`, server-rendered plain HTML, no JS | matches the Vision (customers on the web); frameworks are banned | Accepted |
| Oracle is the VIS database and the TDS schema at once | one schema serves both courses | John |
| Local Oracle 23ai Free in Docker: container `vis-oracle`, service `FREEPDB1`, user `CATERING` (connection details only in `TDS/db/README-local-oracle.md`) | APEX cloud has no JDBC; same scripts run on `dbsys.cs.vsb.cz` | John |
| SQL stays Oracle 19c compatible (no 23ai-only syntax) | must also run on dbsys | Coordinator |
| QR code via ZXing core | library, not a framework | Claude (in A3, ❓ see OPEN-QUESTIONS) |

## Storage and data access

| Decision | Reason | Who |
|---|---|---|
| Second storage = document folder on disk behind `DocumentStorage`; only metadata in Oracle | Vision says so; documents are ~30 GB vs ~400 MB structured | Accepted |
| Small product photo as BLOB in Oracle | TDS requires BLOB | Coordinator |
| Data Mapper per aggregate over plain JDBC, one JDBC transaction per business operation | clean domain, simple to defend | Coordinator (John accepted) |
| No Unit of Work, no Identity Map | not required by guiderails; cv04 marks them optional; done in cv3 lab | Accepted |
| One Lazy Load: comments of a revision | something real to point at, ~15 lines | Accepted |
| Single Table Inheritance `project_nodes` (FOLDER/DOCUMENT/MENU, `node_type`, `parent_id`) + Composite in the domain; `menus.node_id` links a menu to its MENU node (2026-11-13) | CTI gave no benefit; A2 puts menus in folders | John |

## Patterns and UI

| Decision | Reason | Who |
|---|---|---|
| GoF: Composite (folder tree), Factory (mappers, storage), Singleton (`Database` connection provider), Facade (services shared by both UIs) | each has a natural place; must exist in code | Coordinator |
| Revision rule: saving a WORKING revision overwrites it (number stays); a SHARED revision is read-only and its working copy is saved as N+1 | as in A2 UC08 | John |
| Revision diff (UC10) = one table with colour highlights added/removed/changed | simplest UI | Coordinator |
| 5 detailed UCs: UC02, UC08, UC10, UC11, UC16; 18 UCs in the diagram | 5 so none must be invented at the defence | John (count), Coordinator (set) |
| UI split: UC02, UC08, UC10, UC16 and the sharing part of UC11 in JavaFX; customer part of UC11 on the web | both UIs used | Coordinator |
| Deployment: 3 nodes (worker PC with JavaFX + embedded web server + document folder; Oracle server; customer browser) | realistic, DB is remote anyway | Accepted |

## Documents and folders

| Decision | Reason | Who |
|---|---|---|
| Diagrams in PlantUML, UI sketches in PlantUML Salt | text, regenerable | Accepted |
| Diagrams rendered as PNG, not PDF | installed PlantUML 1.2020 drops Czech glyphs (č, ě, ř) in PDF | Claude |
| Czech prose, English identifiers (classes, tables, columns) | TDS requires English names | Coordinator |
| Artefacts 2–4 handed in as one file `VIS_ZAM0074_C06_A234.pdf` (login ZAM0074, group C06) | John's wish | John |
| `main.tex` + `preamble.tex` master, one `.tex` per artefact via `\input` | each artefact editable alone | Coordinator |
| TDS lives in its own course folder `tretak/TDS`; VIS has no includes or paths into it; the app reaches the DB only at runtime via a JDBC config file (planned `VIS/projekt/app/config/db.properties`, example committed, real one ignored) | John's wish | John |

## Values chosen by Claude (used in A2/A3 and the seed)

Document upload ≤ 20 MB · share link valid 14 days, a new share deactivates older links of the menu ·
comment ≤ 2000 characters · password ≥ 8 characters · 300 projects/year kept 5 years
(≈ 400 MB structured data, ≈ 30 GB documents). Pending John's review (see `OPEN-QUESTIONS.md`).

## Implementation (2026-11-13, approved plan)

| Decision | Reason | Who |
|---|---|---|
| App in `projekt/app`, three layer packages: `presentation.{desktop,web}`, `business.{domain,command,pricing,port,service}`, `data.{oracle,file}`; `AppContext` = composition root | John asked for layer folders; presentation → business ← data (BL defines mapper interfaces in `port`) | John |
| Patterns in code: Layer Supertype `DomainObject`, Value Object `Money`, Special Case `UnknownShareLink`, Service Stub `InMemoryDocumentStorage` (tests), Gateway `DocumentStorage`, Lazy Load comments, Factory Method `NodeCreator`, Strategy `PriceStrategy` (s/bez DPH), Observer `MenuRevisionListener`, Command `MenuEditCommand` + `MenuEditor` (undo) | cv08 + cv09 slides, each at a natural place | John (approved plan) |
| `comments` store `resolution_note`, `resolved_by`, `resolved_at` | UC13 post-state | John (approved plan) |
| Passwords stay unsalted SHA-256 hex (seed compatible) | KISS; A3 text says PBKDF2 → fix A3 or upgrade later (OPEN-QUESTIONS) | Claude |
| Web runs plain HTTP locally (A3 says HTTPS) | local demo; HttpsServer needs a certificate | Claude |
