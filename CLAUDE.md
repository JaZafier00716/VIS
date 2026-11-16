# VIS – catering IS (semester project) + cv3 lab

`projekt/` is the semester project (documentation now, Java app later in `projekt/app/`).
`cv3/` is the separate lab project (furniture orders, CV04–CV07); treat it as reference only and
leave it unchanged unless John asks.

Notes in `projekt/notes/` – read the one that matches the task:
- `MAP.md` – where every file is; start here when orienting.
- `DECISIONS.md` – before changing architecture, technology, a pattern or the document structure.
- `STATUS.md` – before starting work: what is done, what is next, how to rebuild.
- `OPEN-QUESTIONS.md` – before asking John anything; add new questions there.
- `REQ-VIS.md` / `REQ-TDS.md` – when writing an artefact or checking coverage.

Rules:
- Keep it simple (KISS, SOLID); it is a semester project to defend, not production code.
- Hand-write everything: plain Java, JavaFX, JDK HttpServer, JDBC with Data Mapper. Frameworks and
  ORMs are forbidden by the assignment.
- Every pattern named in the docs must exist in the code, and the docs must match the code.
- `projekt/guiderails.md` is the authority for artefact form, numbering and counts.
- Write docs in Czech, identifiers (classes, tables) in English.
- The database scripts live in the separate TDS folder (`../TDS`); reach the DB only at runtime via
  the JDBC config file, never by file includes or paths into TDS.
- Before a phase touching more than ~5 files, post a short plan and wait for John's OK.

Build:
- Docs: `cd projekt/docs/artefacts && latexmk -pdf VIS_ZAM0074_C06_A234.tex main.tex`.
- Diagrams: `plantuml -tpng diagrams/<name>.puml` – PNG only, PDF export loses Czech letters.
- Maven/JDK quirks: see `projekt/notes/STATUS.md` ("Rebuild and rerun").
