# Audit 2026-11-16 – vis-compliance

## Progress
Done: guiderails, slides cv02–cv09, A1–A7 .tex + built PDFs (timestamps, page counts), UC/activity/class/component/deployment .puml,
cross-check of all 25 `\svc{}` calls in A2 against code, all 296 attributes and methods in the A5 class diagrams against code
(script: 0 real mismatches), A3/A5/A6/A7 pattern and class names against code, layer imports, e2e test names for the 5 detailed UCs.
Next: nothing. Tests not re-run, because the DB is shared (see note N2).

## Overall verdict
The project meets the VIS assignment in content and form. A1 states who uses which UI and how many users there are. A2 has 19 numbered UCs, a Čas actor, no System actor, and include/extend with extension points. It has 5 detailed UCs in the required field form (not Login). Each has an alternative flow of at least 5 steps, numbered 3.1…, a post-state you can check in the DB, and an activity diagram with swimlanes. A3 has the size table, reasons for the storage choice, versions, and EA + GoF patterns. A4 covers both UIs with screen sequences and UC-step references. A5 has private typed attributes, public business methods, multiplicities, navigation and diamonds. It also has facades, port interfaces, a separate DAL diagram, sequence diagrams with a UI object, and a short text per class and per pattern. A6 has UML 2 component diagrams (lollipop/socket) and a deployment diagram. A7 covers 2 UIs, 2 storages and all 19 UCs.

Every `\svc{Class.method()}` in A2 exists with that name and role. Every class and member in the A5 diagrams exists. Every pattern named in A3/A5/A6/A7 has its class in the code. `business` imports nothing from `data` or `presentation`. The e2e tests cover the main flow and each alternative flow of UC02/08/11/13/16 with JDBC post-state checks. The findings below are about hand-in form and small doc-versus-code drift.

## Findings

### VIS-1 Hand-in zip has no DB scripts, so the examiner cannot create the database
- Severity: medium
- Evidence: a7-implementace.tex:271 "Archiv ZIP obsahuje složku `src` …, soubor `pom.xml` a šablonu `config/db.properties.example`". a6-architektura.tex:118 step 1 "spustit skripty, které vytvoří tabulky, sekvence a výchozí data" does not name any script or say where it is. The scripts exist only in `../TDS/db/01_schema.sql`, `02_seed.sql`, `03_checks.sql`. A7 demos rely on seed data ("Svatba Novákovi", user `mkral`).
- Why: guiderails A7 says "zip s naprogramovaným IS". Without the schema and seed, the IS cannot be run, and none of the A7 demo steps can be reproduced. A6 is supposed to let a technician install the system.
- Fix: when packaging, copy `TDS/db/01_schema.sql` and `02_seed.sql` into the zip as `db/`. This is a copy, not a runtime path, so the CLAUDE.md rule still holds. Name them in A6 step 1 and in A7 "Odevzdaný archiv". State the seed password `heslo123` in A7 next to the demo accounts.
- Confidence: confirmed

### VIS-2 The final complete PDF is named `main.pdf`, and its title page has no login or group
- Severity: medium
- Evidence: cv08 p.26 (Odevzdání 5–7): "finále dohromady 1 PDF se všema artefaktama … pojmenování VIS_login_CXX_AX". The final document is built as `docs/artefacts/main.pdf`. main.tex:7 `\titulni{Kompletní dokumentace}`, while each artefact file adds "Jan Zámostný (ZAM0074), skupina C06".
- Why: the teacher sorts mail by file name and asked for this exact pattern. A file named `main.pdf` with no login is easy to lose or to mis-assign.
- Fix: build or copy it as e.g. `VIS_ZAM0074_C06_A1-A7.pdf` (or `..._final.pdf`). Use `\titulni{Kompletní dokumentace (artefakty 1--7)\\[2mm]Jan Zámostný (ZAM0074), skupina C06}`. Send A5, A6 and A7 as separate `VIS_ZAM0074_C06_A5/6/7.pdf` (already built) plus that file. Optionally also attach A2, A3 and A4 separately, because the 11.11 hand-in was one file, A234, and cv04 p.2 says "Každý artefakt 1 soubor = 3 soubory" (OPEN-QUESTIONS 6).
- Confidence: confirmed

### VIS-3 Use-case diagram writes CRUD as "(CRUD)" text, not as a «CRUD» stereotype
- Severity: low
- Evidence: diagrams/uc-overview.puml:18–19 `usecase "05 - Spravovat zákazníky\n(CRUD)"`. The PNG shows "(CRUD)". a2-funkcni-specifikace.tex:56 and :74–75 say "stereotypem «CRUD»".
- Why: guiderails A2 says "lze umístit do jednoho UC se stereotypem CRUD" and asks for UML 2.5.1 notation. The text and the diagram disagree.
- Fix: `usecase "05 - Spravovat zákazníky" as UC05 <<CRUD>>`, and the same for UC06. Then re-render with `plantuml -tpng` and rebuild the PDFs.
- Confidence: confirmed

### VIS-4 A3 (and one A6 sentence) differ from the code
- Severity: low
- Evidence:
  - a3:169 Facade: "`MenuService` menu, jeho revize a export do PDF". In the code the export is `business/service/MenuExportService.java`. The A3 list also leaves out `AuthService` and `MenuExportService`; A5 and A6 list all 9.
  - a3:138 `catering-is.jar`, but pom.xml (artifactId `catering-is`, version `1.0`) builds `catering-is-1.0.jar`, which is the name A6:93 uses.
  - a3:160 "přes sdílené připojení `Database`" and a6:79 "Singleton se sdíleným připojením JDBC". `Database.inTransaction` (data/oracle/Database.java:39–56) opens a new connection per transaction and binds it to the thread. A5 describes this correctly.
- Why: the rule is "docs must match the code", and the examiner compares A3 against A5 and A6.
- Fix: A3:169 `MenuService` menu a revize, `MenuExportService` export do PDF, `AuthService` přihlášení. A3:138 `catering-is-1.0.jar`. A3:160 and A6:79: "jediná instance, která pro každou transakci otevře připojení JDBC a drží ho pro aktuální vlákno".
- Confidence: confirmed

### VIS-5 A3 gives no version for the reverse proxy (web server)
- Severity: low
- Evidence: a3:117 and :138 mention the reverse proxy with no product or version. a6:93 says "například nginx". Guiderails A3: "jaký webový server ve které jeho verzi požadujete pro běh a opět zdůvodnění".
- Why: HTTPS for customers depends on this component. Every other component in A3 has a version.
- Fix: add a row to the A3 technology table: "Reverzní proxy | nginx 1.18+ (balíček Ubuntu 22.04) | ukončuje HTTPS s certifikátem společnosti, malý a běžně nasazovaný". In A6, replace "například nginx" with "nginx".
- Confidence: confirmed

### VIS-6 The UC08 activity diagram ends the 7.1 conflict branch with `stop`, but the text continues at step 2
- Severity: low
- Evidence: diagrams/act-uc08.puml (after `repeat while`): `:Nic neuloží, zobrazí zprávu\na načte nejnovější revizi; stop`. a2:244 says "7.3 Systém načte nejnovější revizi menu a pokračuje se krokem 2". The code does the same (MenuEditorWindow reloads the latest revision).
- Why: guiderails A2 says the activity diagram must contain the main and alternative flows. Here the diagram and the UC text disagree.
- Fix: replace the `stop` with a loop back to "Načte nejnovější revizi menu" (for example, wrap the flow in an outer `repeat`), or label the end "→ krok 2". Then re-render.
- Confidence: confirmed

### VIS-7 A4 D4 says quantity and price are edited in the table cells, but the app uses a dialog
- Severity: low
- Evidence: a4-skica-ui.tex:131 "množství a cena mění přímo v buňkách tabulky". The code uses the "Upravit položku" button, `MenuItemDialog` and `ChangeItemCommand` (MenuEditorWindow.java:325), and A7:84 describes the dialog.
- Why: A4 may differ from the final app ("nemusí stoprocentně odpovídat"), but A2, A3 and A4 were explicitly realigned to the code on 2026-11-16, so this is the one sentence left over.
- Fix: "Množství, cenu a poznámku položky pracovník změní tlačítkem \uv{Upravit položku} v dialogu (UC08/3)".
- Confidence: confirmed

### VIS-8 Every workstation also starts the web server and the daily scheduler; the deployment diagram does not show this
- Severity: low
- Evidence: presentation/desktop/DesktopApp.java:41 `web = new WebServer(ctx, ctx.webPort)` and :48 `new ExpiredLinkScheduler(...)`. a6:last paragraph admits it ("Na pracovních stanicích je port 8080 zvenku zablokován"). diagrams/deployment-a6.puml puts `HttpServer`/`ExpiredLinkScheduler` only in the application-server artifact.
- Why: an examiner reading A6 may ask why 30 desktops each run an HTTP server and UC18. Guiderails A6 says the deployment diagram must show "kde se nachází které části".
- Fix (KISS): start the web server and scheduler from `DesktopApp` only when `web.embedded=true` is set in `config/db.properties` (default false; true in the dev config). In A6, say "jen ve vývojové variantě". Alternatively, leave the code as it is and add "(na stanici zablokován)" to the workstation artifact in the diagram.
- Confidence: confirmed

## Risks of accepted decisions (not findings; prepare for the defence)

- **Second storage = document folder via Gateway.** John decided this. Guiderails A7 asks for "Podpora dvou různých datových uložišť … pomocí vzorů pro přístup k datům", and A5 suggests "jeden Maper do DB, jedno DTO a další Mapper do XML". Here only the document content (bytes) goes to the folder; no domain entity is mapped to the second storage. This is the requirement the examiner is most likely to question. Defence: guiderails A3 says "Podle velikosti zabraného místa zvolte správné uložiště". The A3 size table shows about 30 GB of documents against about 400 MB of structured data. Both storages are behind ports defined in `business.port` and created by `DataAccessFactory`, and `InMemoryDocumentStorage` is a second implementation of the same port. If you want certainty, add a specific question to OPEN-QUESTIONS for the teacher ("Je souborové úložiště obsahu dokumentů za rozhraním Gateway dostatečné jako druhé úložiště?") before 05.12.
- **No DTO.** A5:197 says so openly. `SharedMenuView` is effectively a view DTO from BL to the web UI; you can mention it if asked.
- **"Factory" listed as a GoF pattern** (`DataAccessFactory` is a simple factory). It is fine because guiderails A3 itself says "Factory pro vytváření tříd", and the real GoF Factory Method (`NodeCreator`) is also present.

## Notes
- N1 Per-UC check of the 5 detailed UCs: every UC has at least one alternative flow of 5 or more steps (UC02 7.1–7.12 and 8.1–8.5, UC08 2.1–2.5, UC11 5.1–5.6, UC13 3.1–3.5 and 4.1–4.5, UC16 7.1–7.5). There are 19 UCs in the diagram (limit 15–25). The activity diagrams act-uc02/08/11/13/16 all use `|Pracovník|/|Systém|` swimlanes.
- N2 `app/target/surefire-reports` (17:53 today) shows 238 run and 12 errors, all in `business.e2e` with `ORA-12514 Service FREEPDB1 is not registered`. The DB was down or rebuilding during someone's run, so this is not a code defect. Re-run `mvn clean test` under db.lock before relying on the A7 figure "276 testů, všechny projdou".
- N3 The working folder `docs/artefacts` contains clutter that must not go into any hand-in: `tmp1–4.tmp`, `main.tex.bak-*`, `AUDIT-*.md`, `notes.md`, `backup-a4-*`, and the unused `act-uc10.*`, `seq-uc10.*`, `deployment.png` (deployment.png is still used by A3). `app/` contains `config/db.properties` (password), `target/` and `documents/`. `.gitignore` excludes them, but a manual zip does not respect it, so zip with an explicit list (`src pom.xml config/db.properties.example db/`).

## Seen outside my area
- OPEN-QUESTIONS.md items 15 and 16 are still listed as "to do", but STATUS.md says both were done on 2026-11-16 (WORKING in A3; HTTPS via reverse proxy). The notes are stale.
- a2/a3/a4 `.tex` were modified today at 17:49–17:50 during the read-only audit phase; check that this was John's edit.

## Summary
high: 0 · medium: 2 (VIS-1, VIS-2) · low: 6 (VIS-3 … VIS-8)
