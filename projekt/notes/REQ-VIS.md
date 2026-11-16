# VIS requirements digest

Condensed from `VIS/projekt/guiderails.md` (the authority; read it for exact wording) and the lab
slides `VIS/presentations/VIS_cv0X.pdf`.

## General rules

- C# or Java only; no ready-made frameworks (MVC, MVVM, MVP, Hibernate/NHibernate, Entity
  Framework/ORM). Plain ASP.NET is allowed for the web; LINQ to SQL / ADO.NET / DataTable are allowed.
- Object design with GoF and enterprise patterns, top-down. Patterns named in the docs must exist
  in the code; the final docs must match the code.
- The DAIS project cannot be reused (its topic can). The Vision may be extended, not replaced.
- Only specific questions get answers; the last version sent by the deadline counts.

## Artefacts

| # | Content (must-haves) | Status |
|---|---|---|
| 1 Vize | customer's view, prose only; who uses which UI; user counts and concurrency; the implemented UCs described in more detail | done (`a1-vize.tex`) |
| 2 Funkční specifikace | UC diagram 15–25 UCs numbered "01 - …", UML 2.5.1, no System actor (use Čas / extend), CRUD stereotype allowed; 3 (ideally 5) detailed UCs, not Login, form: Název, Textový popis, Účastníci, Podmínky před spuštěním, Hlavní průběh, Alternativní průběh (numbered 3.1, 3.2…, ≥ 5 steps), Stav systému po ukončení (checkable in storage); each step = a call in code; activity diagram with swimlanes per UC; sequence diagrams (slides) | written |
| 3 Technická specifikace | every choice justified ≥ 1 sentence; entity size table (Unicode, rounded up to hundreds of bytes); storage chosen from sizes; conceptual model as class diagram with multiplicities (not ER); OS/browser/JRE/web server versions; EA patterns (domain model, data access, layers) and GoF patterns | written |
| 4 Skica UI | sketches for both UIs covering the selected UCs; screen sequences; labelled parts; mapping to UC steps | written |
| 5 Návrh doménového modelu | (cv08 p.21–25) extended conceptual model: private attributes (-) with implementation types, public business methods (+) with parameters and return types, no getters/setters, associations with multiplicity and navigation arrow, aggregation/composition diamonds, facades and Gateway/Mapper interfaces shown (BL defines, DAL implements), a short text per class; static class diagram = code (BL) + separate DAL diagram (one DB mapper, one DTO, one file mapper/TDG, connection classes); sequence diagrams incl. a UI object; no business logic in DAL; short pattern descriptions; white image backgrounds; show associations, composition/aggregation diamonds | to do |
| 6 Architektura | UML component diagram (PL/BL/DAL with inner components, DB server); deployment diagram (all on one PC + external web client, or realistic multi-node) | to do (first deployment idea is in A3) |
| 7 Implementace | demo of the UCs incl. alternative flows; **two different UIs**; **two different storages via data access patterns** (not a config file); full docs; hand in a PDF of all docs + zip of the IS | to do |

## Choices the slides offer

| Choice | Options | Source |
|---|---|---|
| Language | C# or Java | guiderails (Obecné); cv02 p.19 |
| Storage in labs | DB (SQL) or files (CSV/JSON/XML) | cv04 p.3, p.7–17 |
| Storage in the project | two different storages, e.g. SQL and XML | guiderails A3, A7 |
| Data access pattern | TDG, RDG, Active Record or Data Mapper | cv04; cv05 p.10; cv07 p.14 |
| Identity Map, Unit of Work | optional ("volitelné") | cv04 p.14 |
| Inheritance mapping | design STI, CTI and Concrete; implement one | cv07 p.16 |
| Number of detailed UCs | 3 (ideally 5); slides say five | guiderails A2; cv06 p.18 |
| UCs per UI | free split, e.g. UC 1–3 web and UC 5–6 CLI | guiderails A4; cv07 p.18 |
| Web server | IIS, Apache or Nginx (with versions) | cv06 p.25 |
| Client types | web, thick client, mobile | cv06 p.26 |
| GoF patterns | e.g. Singleton, publisher–subscriber, Factory, Facade | guiderails A3 |
| Deployment diagram | one PC or realistic multi-node | guiderails A6 |
| UML tool | any (Enterprise Architect, Visual Paradigm, Modelio, UMLet, Visio, PlantUML …) | guiderails A5 |

## Deadlines and submission

- A1 Vision: 29.9.2026 23:59 by mail (cv02 p.3).
- A2–A4: **11.11.2026 23:59** to martin.radvansky1@vsb.cz, file name `VIS_login_CXX_AX` (cv04 p.2);
  John hands in one file `VIS_ZAM0074_C06_A234.pdf`.
- **A5–A7: 05.12.2026 23:59**, one file per artefact (`VIS_login_CXX_AX`) plus the final single PDF with all
  artefacts (cv08 p.26).
