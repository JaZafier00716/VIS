# VIS cv3 audit re-check after the fix plan (2026-10-27)

Re-run of the audit checklist from `VIS-cv3-audit.md` against the code at commit `d8df3a8` plus the Phase 6
documentation commit. Build: `mvn clean test` → **88 tests, 0 failures** (JDK 27, Lombok 1.18.48, `--release 21`);
`--demo` exits 0 on H2 and JSON, with CV05 scenarios A/B/C and all six CV06 scenarios printing the expected output.

Status: ✅ done and correct · ⚠️ present but deviates · ❌ missing · ❓ cannot be judged from the folder · ➖ not required.

## 1. Updated checklist

| # | Requirement | Slide | Before | Now | Evidence | Why it remains (if ⚠️/❌) |
|---|---|---|---|---|---|---|
| **CV03** | | | | | | |
| 1–4 | Transaction Script, Domain Model, Table Module, CV03 deliverables | cv03 p.7–15 | ❌/⚠️ | ➖ | – | You confirmed CV03 is not part of this hand-in. |
| 5 | Use case: confirm **or cancel** the order | cv03 p.5–6 | ⚠️ | ⚠️ | `OrderStatus` has `CEKA_NA_POTVRZENI`, `POTVRZENA` only | Not in the fix plan (P-m1). Add `ZRUSENA` + `Order.cancel()` if you want it. |
| **CV04** | | | | | | |
| 6 | UI → BL → DAL; DTOs between UI and BL | cv04 p.4–5 | ⚠️ | ⚠️ | `PlaceOrderService.CustomerData/AddressData` are input DTOs; other menu items still build `OrderSpecification`/`ProductConfiguration` in `Main` | Value objects built in the UI validate themselves; converting every menu item to DTOs was not in the plan. Defensible. |
| 7 | TDG: one gateway per table, Insert/FindById/FindAll/Update, DTOs | cv04 p.7–8 | ⚠️ | ✅ | `cv04.tdg.dataaccess.{Customer,Material,Order}TableGateway` | – |
| 8 | TDG use case in a BL service | cv04 p.8 st. 4–5 | ❌ | ✅ | `cv04.tdg.business.TdgOrderService.placeOrder` | – |
| 9 | RDG: row objects with Insert/Update/Delete + static Find/FindAll | cv04 p.10 | ⚠️ | ✅ | `cv04.rdg.dataaccess.*RowGateway`, `OrderRowGateway.insert()` validates | – |
| 10 | AR: `Order.Save/Delete/Find`, `Customer.Save`, `Material.Exists`, `Validate()` from `Save()` | cv04 p.11–12 | ⚠️ | ✅ | `cv04.activerecord.OrderRecord.save()` → `validate()`, `MaterialRecord.exists` | – |
| 11 | DM: clean entities, repository interfaces **in BL**, mapper converts entity ⇄ record | cv04 p.13–14 st. 2, 6 | ⚠️ | ✅ | `business.repository.*Repository` (domain objects only), `dataaccess.mapper.*DataMapper`; `grep dataaccess src/business` → nothing | – |
| 12 | DM Add/ById/Update/All with validation and enum mapping | cv04 p.15–16 | ✅ | ⚠️ | `OrderMapper.toDomain` restores `POTVRZENA` by calling `order.confirm()`; `GatewayEntityPersister` calls `validate()` | P-M1 was not in the plan. Works for the two states; breaks once a third state (e.g. cancel) exists. |
| 13 | ID strategy chosen and unique | cv04 p.14 st. 4 | ✅ | ✅ | `IdGenerator` via `…Repository.nextId()`; PKs in `H2Schema`, `Cv04Schema` | – |
| 14 | Atomic file write; DB transactions | cv04 p.16 st. 10 | ✅ | ✅ | `JsonFiles`, `H2Database`, `H2Transaction` | – |
| 15 | Same tests against JSON and SQL | cv04 p.16 st. 12 | ✅ | ✅ | `StorageContractTest`; now also `PlaceOrderUnitOfWorkTest`, `IdentityMapIntegrationTest`, `LazyLoadingTest`, CV04 DM tests on both | The older per-pattern CV06 service tests still run on JSON only (P-M3). |
| 16 | Artefacts 2–4 submitted by 11.11.2026 | cv04 p.2 | ❓ | ❓ | not in folder | Check separately. |
| **CV05** | | | | | | |
| 17 | UoW tracks New / Modified / Removed | cv05 p.6–7 | ✅ | ✅ | `UnitOfWork.registerNew/Dirty/Deleted` | – |
| 18 | `commit()` via existing data access; rollback on failure | cv05 p.7 | ✅ | ✅ | `UnitOfWork.commit()`, `H2Transaction`, `JsonFileTransaction` | – |
| 19 | **Scenario A: customer + address + order in one UoW** | cv05 p.3, 8, 13 | ❌ | ✅ | `PlaceOrderService.placeOrder`; `PlaceOrderUnitOfWorkTest` (1 begin / 1 commit, failure → nothing stored, rejected input → no changes, existing customer) on JSON + H2 | – |
| 20 | Insert order customer → address → order | cv05 p.8 | ⚠️ | ✅ | `UnitOfWork.dependencyRank`; `UnitOfWorkTest.customerAddressAndOrderAreOrderedByTheUnitOfWorkNotByRegistration` | – |
| 21 | Identity Map keyed by type + ID, same instance | cv05 p.9–10 | ✅ | ✅ | `IdentityMap`, `IdentityMapIntegrationTest` | – |
| 22 | Identity Map in DAL, **lifetime one UoW** | cv05 p.5, 9, 10 | ⚠️ | ✅ | checks in every `…DataMapper` finder incl. `findAll`; `commit()`/`rollback()` clear it; tests for both | Slides don't define where a read-only UoW starts; here it runs from one commit to the next. |
| 23–24 | Lazy Load `Order.customer`, `Customer.addresses` | cv05 p.12 | ✅ | ✅ | `Order.getCustomer`, `Customer.getAddresses` | – |
| 25 | Lazy-load mechanism in DAL | cv05 p.5, 11 | ⚠️ | ✅ | loaders attached in `OrderDataMapper`, `CustomerDataMapper` | – |
| 26 | Lazy Load uses the Identity Map | cv05 p.12 | ✅ | ✅ | `OrderDataMapper` → `CustomerDataMapper.findById`; asserted in `LazyLoadingTest` | – |
| 27 | Demonstrate the 3 situations / 8-row test matrix | cv05 p.3, 15 | ⚠️ | ⚠️ | demo scenarios A/B/C (`Cv05Demonstrations`); read-count tests on both storages | "Chyba při SQL zápisu" is proven with a simulated failure inside a real H2 transaction (`PlaceOrderUnitOfWorkTest`, `UnitOfWorkFailureRecoveryTest`), not with a real SQL constraint error. |
| 28 | Artefact 2 (UC, activity, sequence diagrams) | cv05 p.16–20 | ❓ | ❓ | not in folder | Check separately. |
| **CV06** | | | | | | |
| 29 | Identity Field | cv06 p.5–6 | ✅ | ✅ | `*Id` fields, `VARCHAR(64) PRIMARY KEY`, `IdentityFieldTest` | – |
| 30 | FK Mapping + delete policy | cv06 p.7–8 | ✅ | ✅ | `fk_orders_customer/material`, RESTRICT/CASCADE | – |
| 31 | FK test: the **database** rejects an invalid FK | cv06 p.8, 17 | ⚠️ | ⚠️ | `H2DatabaseTest` covers the customer FK only; service tests are rejected before the DB | P-M2 was not in the plan. |
| 32 | Association Table Mapping | cv06 p.9–10 | ✅ | ✅ | `material_finishes`, `H2MaterialFinishGateway.synchronize`, `MaterialDataMapper` | – |
| 33 | Dependent Mapping | cv06 p.11–12 | ✅ | ✅ | `order_items`, `H2OrderGateway` | Order gateway covers `orders` + dependent `order_items` (documented). |
| 34 | Embedded Value `Dimensions` (mm) | cv06 p.13 | ⚠️ | ⚠️ | `OrderSpecification` with cm, plus `designNotes` | P-M5 not in the plan. Be ready to say "OrderSpecification is my Dimensions". |
| 35 | Serialized LOB | cv06 p.14 | ✅ | ✅ | `ProductConfigurationSerializer`, `configuration_json CLOB` | – |
| 36 | Per-pattern documentation | cv06 p.16 | ✅ | ✅ | README CV04/CV05/CV06 tables | – |
| 37 | Artefact 3 | cv06 p.18–26 | ❓ | ❓ | not in folder | Check separately. |
| **CV07** | | | | | | |
| 38 | Customer inheritance (STI/CTI/Concrete), one implemented | cv07 p.3–16 | ❌ | ❌ | – | Deferred on purpose: next step once everything else works. Gateways are one-per-table, ready for CTI. |
| 39 | Artefact 4 (UI sketches) | cv07 p.18 | ❓ | ❓ | not in folder | Check separately. |
| **Your constraints** | | | | | | |
| 40 | No ORM/framework | brief | ✅ | ✅ | `pom.xml`: h2, jackson-databind, lombok, junit | – |
| 41 | Strict three layers, one direction | brief | ⚠️ | ⚠️ | `business` imports nothing from `dataaccess` (fixed); presentation never imports `dataaccess` | **Still open:** `Cv05Demonstrations.clean()` / `Cv06Demonstrations.clean()` delete files from the presentation layer (P-H5; I added the CV05 copy in Phase 2 following the CV06 one); `UnitOfWork` keeps two JSON-defaulting constructors (rest of P-H6); the persister calls `validate()` (P-M1). |
| 42 | Clean domain | brief | ✅ | ✅ | `business/domainmodel/*` | Cosmetic: `transient` fields, public `Order.restoreItem` (P-m2, P-m3). |
| 43 | Custom UoW, one JDBC connection per commit | brief | ✅ | ✅ | `H2Database.begin()`, all gateways join `transactionConnection` | – |

## 2. Findings fixed

| Audit ID | Fix | Phase / commit |
|---|---|---|
| P-B1 | Lombok 1.18.48 (1.18.40 still failed on JDK 27); README build + IntelliJ steps | 1 / `cce692b` |
| P-B2 | `PlaceOrderService`, one commit, rollback; tests on both storages; demo A + menu 13 | 2 / `cce692b` |
| P-M6 | Address ranked after Customer in the UoW itself | `cce692b` |
| P-H1 | Identity Map cleared by `commit()`/`rollback()` | 3 / `d6ef299` |
| P-H2 | Identity Map + Lazy Load wiring moved into `…DataMapper` | 3 / `d6ef299` |
| P-H3 | Repository interfaces in BL, domain objects only; DTO-level classes renamed to `…Gateway` | 3 / `9cbf7d6`, `d6ef299` |
| P-H4 | CV04 order use case for TDG/RDG/AR (H2, own tables) and DM | 4 / `d8df3a8` |
| P-H6 (part) | JSON-only service constructors removed | 3 |
| P-M4 | CV05 demo A/B/C; end-to-end Lazy Load read-count tests | 2–3 |
| P-m4 | Loader setters only store the loader | 3 |
| P-m5 | `findAll` maps the records it has (no N+1), reuses Identity Map instances | 3 |
| P-m7 (part) | `DataMapperDemonstration` wrapper replaced | 4 |
| P-m9 | README, class diagram (renders with PlantUML), guardrails updated | 6 |

## 3. Still open, ranked

1. **P-H5 – file I/O in presentation** (`Cv05/Cv06Demonstrations.clean()`). Breaks guardrail 1. Fix: move the reset into `configuration` (e.g. `ApplicationContext.createEmpty(type, dir)`). About 3 files.
2. **P-M1 – DAL calls domain behaviour** (`OrderMapper` → `confirm()`, persister → `validate()`). Fix: an `Order` restore factory taking the status; validation only in services.
3. **P-M2 – FK test at DB level for `material_id`** and for JSON parity.
4. **P-H6 rest – `UnitOfWork(IdentityMap)` / `UnitOfWork(IdentityMap, EntityPersister)` default to JSON.** Only tests use them.
5. **P-M3 – parity gaps:** JSON `findAll` order, different exception types per storage, CV06 service tests on JSON only, JSON directory via system property.
6. **P-M5 – Embedded Value naming/units** (`Dimensions`, mm vs cm).
7. **Real SQL error in the CV05 rollback test** (row 27).
8. Minor: P-m1 (cancel state), P-m2/m3 (`transient`, public `restoreItem`), P-m6 (`RecordIds` in the JSON package), P-m7 (`H2Database.inDataDirectory()` unused), P-m8 (folder `cv3` vs CV04–CV06).
9. **CV07** – next phase, after your go-ahead.

Ambiguous in the slides (not fixable by code): whether Jackson/Lombok are allowed (the slides are silent), where a read-only unit of work begins, TDG/RDG/AR have no transaction across the customer and order insert (validation runs first, so rejected input stores nothing), `CEKA_NA_POTVRZENI` vs `WAITING_FOR_CONFIRMATION`.

## 4. Five likely examiner questions (updated)

**1. "Which data-access pattern does your application use, and where are the other three?"**
Data Mapper. The business layer declares `CustomerRepository`, `OrderRepository`, … in `business.repository`; they take and return domain objects only, and no business class imports `dataaccess`. `dataaccess.mapper.CustomerDataMapper`/`OrderDataMapper`/… implement them on top of one Table Data Gateway per table (`H2…Gateway` or `Json…Gateway`), so swapping the storage swaps only the gateways. TDG, RDG and Active Record are in the isolated `cv04` package with their own H2 tables, each implementing the same order use case; `cv04.datamapper.DataMapperDemonstration` runs that use case through the real app.

**2. "Show me the CV05 scenario: customer, address and order in one Unit of Work. What happens on an error?"**
`PlaceOrderService.placeOrder` first validates everything (existing customer or valid new data, address, material exists, specification) without registering anything. Then it calls `customers.add`, `addresses.add`, `orders.add`, which register the objects in the `UnitOfWork`, and calls `commit()` once. The UoW sorts the inserts customer → address → order by `dependencyRank`, opens one `StorageTransaction` (one JDBC connection with `autoCommit=false` on H2) and commits. If any insert fails, the transaction is rolled back and `placeOrder` calls `rollback()`. `PlaceOrderUnitOfWorkTest` proves exactly one `begin`/`commit` and that a failing order insert leaves neither customer nor address, on JSON and H2.

**3. "Where is your Identity Map, and how long does it live?"**
In the data access layer: the `UnitOfWork` owns the `IdentityMap` (key `type#id`), and every `…DataMapper` finder checks it before reading a gateway, `findAll` included. It is not a global cache: `commit()` and `rollback()` clear it, so its lifetime is one unit of work. `IdentityMapIntegrationTest` shows two finds = one read and the same instance, and a new instance after commit or rollback, on both storages.

**4. "How does Lazy Load work, and how do you prove that it is lazy?"**
`Order` keeps `customerId` and a loader; `Customer` keeps a loader for its addresses. The data mappers attach the loaders (`OrderDataMapper` uses `CustomerDataMapper.findById`, so the Identity Map is used too). The first `getCustomer()`/`getAddresses()` runs the loader and caches the result. `LazyLoadingTest` counts gateway reads: loading an order reads the customer 0 times, the first `getCustomer()` once, the second not at all; the same for addresses; on JSON and H2. Demo scenario C prints `zákazník načten: false → true`.

**5. "Explain Dependent Mapping, Embedded Value and Serialized LOB in your schema."**
Items have no life without their order: table `order_items`, PK `(order_id, line_number)`, `ON DELETE CASCADE`; the order gateway deletes and re-inserts an order's items in the same transaction. The dimensions are a value without identity: `OrderSpecification` is flattened into `orders.width/height/depth/design_notes` and rebuilt in `OrderMapper`. The free-form configuration is never queried: `ProductConfiguration` is versioned JSON in `order_items.configuration_json CLOB`. Expect a follow-up on `OrderSpecification` vs the slides' `Dimensions` in mm (P-M5).
