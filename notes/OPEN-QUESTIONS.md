# Open questions

Single source of truth for anything John still has to decide or check. Remove an item once answered
(and record the answer in `DECISIONS.md` if it is a decision).

## Artefacts 2–4

1. **Values chosen by Claude**: 20 MB upload, 14-day share links, 2000-character comments,
   8-character passwords, 300 projects/year kept 5 years, the size table volumes. OK?
2. **UC10 Porovnat revize** changes nothing in storage, but guiderails asks for a post-state the
   examiner can check in storage → weakest detailed UC. Swap for UC13 (Zpracovat komentáře) or UC14
   (Exportovat menu do PDF)?
3. **Oracle version on dbsys** unknown; A3 says "19c nebo novější (ověřit)".
4. **QR code via ZXing** (a library, not a framework) – acceptable?
5. **A3 domain model vs schema**: the schema has extra attributes (`supervisor_id`, `city`,
   `event_duration`, `retention_period`, `vat_rate`, timestamps) and the comment ARC. Add them and an
   `{xor}` on Comment to `domain-model.puml`?
6. **⚡ Hand-in format**: cv04 p.2 and cv08 p.26 say "Každý artefakt 1 soubor = 3 soubory" (cv08 adds one
   final PDF with all artefacts); John chose one A234 file. Keep, or also produce A2/A3/A4 separately?
7. Wireframes are black and white; the diff colours and the yellow "shared" banner are only
   described in the text. Enough?

## Implementation (2026-11-13) – A3/A5 texts to align with the code

14. ~~PBKDF2~~ done 2026-11-15: PBKDF2WithHmacSHA256 with salt per user (`users.password_hash` VARCHAR2(200)); A3 text now matches.
15. ~~A3 lists `RevisionState` DRAFT/SHARED; code and schema use WORKING/SHARED (UI PRACOVNÍ/SDÍLENÁ). **To do in A3/A5 text: say WORKING.**~~ answered 2026-11-16: A3/A5 now say WORKING (see STATUS).
16. ~~A3 says `HttpsServer` on 443; the app runs plain HTTP on 8080. **Proposed A3/A6 wording: HTTPS is a deployment matter (reverse proxy with the company certificate in front of the app).**~~ answered 2026-11-16: A3/A6 now say so, the reverse proxy is nginx (see STATUS).
17. ~~CatalogService/CustomerService, UC14 PDF, UC18 scheduler~~ done 2026-11-15 (CustomerService, CatalogService, PDFBox export, ExpiredLinkScheduler).
18. Comments window of a WORKING revision shows the comments of the newest older SHARED revision (that is where customers comment). OK as reading of UC13?
19. UC19: a folder is deletable only when empty in the database, even if all its children are selected in the same request. OK?
20. ~~Last save wins~~ done 2026-11-15: optimistic check on overwrite (save time must match), the second editor gets the UC08 7.1 conflict message.
21. Web W2 (expired link) answers HTTP 410, unknown link 404; the page text follows A2. OK?
22. ~~Archive rule~~ John 2026-11-15: an archived project is fully read-only, content included – done.
23. ~~Manager web roles~~ John 2026-11-15: yes, MANAGER only.
24. ~~Units~~ John 2026-11-15: Czech labels (ks, porce, kg, l, sada) everywhere, and the entire UI is Czech – done.

25. For the teacher (A-20): Je souborové úložiště obsahu dokumentů za rozhraním Gateway dostatečné jako druhé datové úložiště?

## TDS

8. **dbsys privileges**: the scripts need CREATE TABLE, SEQUENCE, TRIGGER, VIEW; not tested on dbsys.
9. `STANDARD_HASH` (seed) and `DEFAULT seq.NEXTVAL` (schema) need Oracle 12c+.
10. **16 tables** (12–14 was the aim). Drop `allergens` + `product_allergens` to get 14? They are
    used by the A3 model and the W1 web sketch.
11. **Seed share links are mostly expired** (revisions dated 8–40 days ago, links valid 14 days):
    good for the UC11 alternative flow, but create a fresh link for the main-flow demo.
12. ~~TDS PDF location~~ John 2026-11-17: moved to `TDS/` (old VIS copy deleted).
    updated.)
13. TDS hand-in format: the assignment PDF is all John has; it names no format or deadline. Hand in the scripts `TDS/db/01–04`, the design notes and `TDS/model` (`catering.dmd` + folder, `Logical.pdf`, `relational.pdf`) as the teacher asks. Still ask the teacher for the deadline.
