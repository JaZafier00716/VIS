# Audit of A1–A5 (2026-11-10, evening)

Three reviewers checked the artefacts after today's changes: the UCC flows against their diagrams (Opus), the model and cross-artefact consistency (Opus), and links, style and the guiderails checklist (Sonnet). I checked each finding against the sources before changing anything.

**Clean:** every link has a target, there are no semicolons or dashes in sentences, there are no TDS mentions, and every guiderails item for A2–A4 is present.

## Fixed (A1, A2, A3, A5 and diagrams)

**Logic errors**
- UC02 "Stáhnout" could never happen, because the new subfolder only contained the new menu. Download is now steps 7.8–7.12, after an upload.
- UC13/3.2 copied the *shared* revision, so a second comment-driven edit would lose the first one. It now opens the newest revision.
- UC13 post-state said "unprocessed comments stay unchanged", but there was no way to stop early. Added 7.1–7.2 "pracovník ukončí zpracování dříve".

**Missing alternatives**
- UC02 4.1–4.3: invalid project data.
- UC02 6.1–6.2: folder name clash.
- UC16 4.x now covers invalid data as well as a duplicate username.

**Sequence diagrams**
- **seq-uc02:**
  - The name-clash dialog came after the menu was already saved. It now sits inside each branch and returns a DuplicateNameException.
  - Added the customer load through CustomerMapper.
  - Download now loads the documents from the DB before reading storage.
  - `createProject` now takes the current user.
  - Invalid data has a branch.
- **seq-uc11:**
  - Added the revision state UPDATE and one COMMIT in SharingService.
  - Added the revision load before `findActiveLink`, and the "no newer link" ending.
  - Comments are now loaded.
  - The token is checked again when a comment is posted.
- **seq-uc13:** reloads the list after "already resolved", and the final message is now conditional.
- **seq-uc16:**
  - `findById` and the domain change now come before the last-admin check.
  - Added the return arrows.
  - Invalid data has a branch.
- **seq-uc08:**
  - Added the catalog load (ProductMapper).
  - `pricePerGuest(guestCount)` now takes the guest count, because a revision cannot reach its project.

**Activity diagrams**
- act-uc02: loops for invalid data and for folder names, the rename loop re-checks the name, and the upload branch has its own clash path.
- act-uc13: early exit added.
- act-uc16: last-admin case drawn as a loop.

**A2 text**
- UC08: the `\svc` calls now match the sequence diagram (`saveAsNewRevision` at step 6, plus `findProducts` and `changeItem`).
- UC13 note limit is now 200 characters, matching the A3 size table.
- UC19 now deletes the menu's expired links too. "Active link" is defined as active and not expired.

**A3**
- Lists the mappers with their tables.
- The Facade list now includes CustomerService and CatalogService, and says which service runs UC14 and UC18.
- Added the enums (ProjectStatus, RevisionState) with their Czech display names.
- Renamed the Menu row of the size table to "ProjectNode -- menu".
- Web runs on HttpsServer on a public domain (customers open links from outside).
- Added a database server row (OS, RAM, disk).
- "Documents are not stored on the workstation" now excludes the user's own downloads.
- The technology table no longer overflows the page.

**Diagrams**
- Domain model: Folder–ProjectNode is now composition, and the enums are drawn.
- UC diagram: UC02 «include» UC07, because the main flow always creates the menu.
- Deployment: 1..30 stations, hardware on every node, a scheduler for UC18, HTTPS on port 443.

**A1 and A234**
- A1: one sentence listing the supporting features (customers, catalog, search and archiving, PDF export, link expiry).
- A234 numbered its sections 1–3. It now starts at 2.

## Sent to the A4 thread
- D7's edit form should only change roles and the active flag, as in UC16.
- D3 also serves upload name clashes.
- Soften the "no error windows" rule.
- D1 download now links UC02/7.8.
- Missing links, `~\ref`, and a note that UC19 is only briefly specified.
- Customer URLs should use HTTPS on a public domain.
- Add PRACOVNÍ to the state chips.

## Your decision, applied
- Saving a PRACOVNÍ revision now overwrites it, and its number stays the same. A SDÍLENÁ revision is locked, so its working copy is saved as a new revision (number + 1). This also means the empty first revision gets filled on the first save.
- UC08 is renamed "Upravit menu a uložit revizi". The button is "Uložit revizi" and the service method is `saveRevision`.
- UC11 can only share a revision that passed the UC08 check (at least one category).
- Updated: UC08, UC11 and UC13 text, A3, act-uc08, seq-uc08, and the UC diagram.

## Left as is
- Step 7 of UC02 holds two actions. Splitting it would renumber steps that A4 links to.
- Lazy Load on comments brings little benefit.
- Web UI covers one UC only. Guiderails suggests more, but does not require it.
- A5–A7 are skeletons, due 2026-12-05.
