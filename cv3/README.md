# VIS – zakázková výroba nábytku (CV04–CV09)

A three-layer Java application for custom furniture orders. It uses no ORM and no framework: persistence is
written by hand, either as JSON files or as plain JDBC over an H2 database.

> **Reconstructed project.** This version was rebuilt on 2026-10-27 because the original was on a computer
> I couldn't reach. The 19 files listed in [Reconstruction notes](#reconstruction-notes) are the original
> code. Everything else was rewritten to fit them.

## Build and run

Requirements: a **full JDK 21** (with `javac`, not only a JRE) and Maven 3.9+. The code compiles with
`--release 21`, so a newer JDK (tested with 25 and 27) works too. Lombok is **1.18.48**: 1.18.34 crashes
inside `javac` 24+ and 1.18.40 still crashes on `javac` 27.

```bash
mvn clean test                                                  # all tests (JSON + H2, CTI + STI, CV04 comparison)
mvn compile exec:java                                           # console, JSON, CTI: ./data/json-cti/
mvn compile exec:java -Dexec.args="--storage=h2"                # console, H2, CTI: ./data/vis-h2-cti.mv.db
mvn compile exec:java -Dexec.args="--storage=h2 --mapping=sti"  # console, H2, STI: ./data/vis-h2-sti.mv.db
mvn compile exec:java -Dexec.args="--demo"                      # CV05–CV09 demonstrations on JSON
mvn compile exec:java -Dexec.args="--storage=h2 --demo"         # CV05–CV09 demonstrations on H2
mvn compile exec:java -Dexec.args="--shipping=stub"             # CV08: shipping Service Stub (default gateway)
```

`--mapping=cti|sti` (or `-Dvis.customerMapping=cti|sti`) selects the CV07 inheritance strategy, default `cti`.
Each demonstration writes its data to `data/demo/cv05/<scenario>/`, `data/demo/cv06/<n>-<pattern>/` and
`data/demo/cv07/<strategy>/`, so you can open the stored records afterwards. Menu item 2 creates a person or a
company, menu item 13 runs the CV05 scenario A interactively.

**IntelliJ IDEA:** open `pom.xml` as a project, then

1. *File → Project Structure → SDKs*: add a full JDK 21 (`sudo apt install openjdk-21-jdk`, or *+ → Download
   JDK → 21*), and in *Project* set it as SDK with language level 21;
2. *Settings → Build → Compiler → Annotation Processors*: enable annotation processing;
3. reload the Maven project, so the Lombok 1.18.48 processor path is picked up.

If `mvn` is not on the PATH, IntelliJ's bundled Maven is in `<idea>/plugins/maven-plugin/lib/maven3/bin/mvn`.

**Data from an older version.** The stored layout has a version: a `schema_version` table in the H2 file and a
`schemaVersion.json` marker next to the JSON files (`dataaccess.SchemaVersion`, currently 3 = CV07 inheritance
mapping, one store per strategy). `CREATE TABLE IF NOT EXISTS` would not change an old layout, so the application
refuses version 2 and older data, an old `data/vis-h2.mv.db` and JSON files directly in `data/` with
*"Data jsou ze starší verze, smažte složku data/ (kromě .gitkeep)"*. Delete the files in `data/` (keep
`.gitkeep`) and start again.

## Architecture

```
presentation   Main, Cv05/Cv06/Cv07/Cv08/Cv09Demonstrations – console UI, calls services only
business       domainmodel.*  (BaseEntity ← Customer ← PersonCustomer, CompanyCustomer; BillingAddress, Order, OrderItem,
                               OrderSpecification, ProductConfiguration, Material, Finish, Address,
                               OrderStatus; CV08: Money, DiscountPolicy ← PercentageDiscount, NoDiscount,
                               ShippingQuote)  – no persistence code
               service.*      CustomerService, MaterialService, OrderService, PlaceOrderService,
                              OrderPricingService + IShippingService (CV08), OrderDocumentService (CV09)
               service.numbering  CV09 Singleton: OrderNumberGenerator
               pricing        CV09 Strategy: IPricingStrategy ← StandardPricing, CompanyPricing
               document       CV09 Factory Method: DocumentCreator ← Confirmation/DeliveryNote/InvoiceCreator,
                              OrderDocument ← OrderConfirmation, DeliveryNote, Invoice
               observer       CV09 Observer: IOrderObserver ← ProductionObserver, ExpeditionObserver
               command        CV09 Command: IOrderCommand ← Confirm/StartProduction/PrepareExpeditionCommand,
                              OrderCommandExecutor, CommandLogEntry
               repository.*   repository interfaces (domain objects only) + UnitOfWorkControl
dataaccess     mapper         …DataMapper implements business.repository.* (Identity Map, Lazy Load),
                              domain ⇄ DTO mappers (OrderMapper, ProductConfigurationSerializer, …),
                              DTO ⇄ JSON row (…RowMapper)
               mapper.customer  CV07 Inheritance Mappers + CtiCustomerTables / StiCustomerTables
               unitofwork     UnitOfWork, GatewayEntityPersister, StorageTransaction
               identity       IdentityMap, IdGenerator
               gateway        Table Data Gateway interfaces (DTOs only, one per table) + Gateways
               persistence    JSON implementation (JsonFiles, Json…Gateway, JsonFileTransaction)
               h2             JDBC implementation (H2Database, H2Schema, H2…Gateway, H2Transaction)
               dto, row       transfer records
               shipping       CV08: ShippingGateway, ShippingServiceStub, ShippingMapper, SimulatedShippingApi
configuration  ApplicationContext (composition root), StorageType, CustomerMapping (CTI/STI),
               ShippingMode (STUB/GATEWAY)
cv04           isolated CV04 comparison, not part of the app: tdg, rdg, activerecord (plain JDBC on their own
               H2 file data/cv04-h2, tables cv04_*), datamapper (uses the app's services)
```

Calls flow in one direction: Presentation → Business → Data Access. **No class in `business` imports anything
from `dataaccess`:** the business layer declares the repository interfaces and `UnitOfWorkControl`, the data
access layer implements them, and only `ApplicationContext` wires both. Domain classes contain no SQL, JDBC or
persistence annotations. Lombok only generates getters, constructors and builders at compile time.
The order status `OrderStatus.CEKA_NA_POTVRZENI` corresponds to the slides' `WAITING_FOR_CONFIRMATION` (cv05).

**Transactions:** All services share one `UnitOfWork`; one business operation ends with one `commit()`, which
opens a `StorageTransaction`:

- **H2:** one JDBC `Connection` with `autoCommit=false`. Every gateway joins it, then the connection
  commits or rolls back.
- **JSON:** each file is journaled before its first write and restored if the commit fails.

Writes run in dependency order (customer/finish → material/address → order), decided by the Unit of Work
itself. Deletes run in reverse.

## CV04 patterns – the order use case (package `cv04`)

One use case for all four (cv04 p.2): validate the input (dimensions > 0, the material exists), store the order
in `CEKA_NA_POTVRZENI`, read it back by ID. Tests: `Cv04PatternsRegressionTest` (valid order, dimensions ≤ 0,
missing material per pattern, plus `update`/`findAll` for TDG and RDG).

| Pattern | BL role | Data access | Storage |
|---|---|---|---|
| Table Data Gateway | `cv04.tdg.business.TdgOrderService` builds DTOs, validates, sets the status | `cv04.tdg.dataaccess.{Customer,Material,Order}TableGateway`: `insert/findById/findAll/update`, DTOs only | H2 `cv04_*` |
| Row Data Gateway | `cv04.rdg.business.RdgOrderService` | `cv04.rdg.dataaccess.*RowGateway`: one object per row, `insert/update/delete`, static `find/findAll`; `OrderRowGateway.insert()` checks the minimum rules itself | H2 `cv04_*` |
| Active Record | – (domain + persistence in one class) | `cv04.activerecord.CustomerRecord`, `MaterialRecord.exists`, `OrderRecord.save()` calls `validate()` | H2 `cv04_*` |
| Data Mapper | the application's `PlaceOrderService` via `cv04.datamapper.DataMapperDemonstration` | `business.repository.*` implemented by `dataaccess.mapper.*DataMapper` | the app's JSON or H2 |

## CV05 patterns – where to find them

| Pattern | Business layer | Data access layer | Test | Demo |
|---|---|---|---|---|
| Unit of Work | `PlaceOrderService.placeOrder`: validate everything, register customer (if new) + address + order, one `commit()`, `rollback()` on failure | `UnitOfWork` (new/dirty/removed, dependency order, one `StorageTransaction`), repositories' `add/update/remove` register | `PlaceOrderUnitOfWorkTest` (JSON + H2), `UnitOfWorkTest`, `UnitOfWorkFailureRecoveryTest` | scenario A |
| Identity Map | – | `IdentityMap` (key = hierarchy root type#ID, so a `PersonCustomer` is `Customer#id`) owned by the `UnitOfWork`, checked in every `…DataMapper` finder (`findAll` too); cleared by `commit()`/`rollback()` – one unit of work, not a global cache | `IdentityMapIntegrationTest` (JSON + H2) | scenario B |
| Lazy Load | uses `order.getCustomer()`, `customer.getAddresses()` when it needs them | loaders attached by `OrderDataMapper` (`Order.customer`, through the customer mapper and its Identity Map) and `CustomerDataMapper` (`Customer.addresses`) | `LazyLoadingTest` (read counts on JSON + H2) | scenario C |

## CV07 – inheritance mapping (customers)

`Customer` is abstract (ID, e-mail, phone, optional `BillingAddress`, lazy delivery addresses);
`PersonCustomer` (first/last name, birth date) and `CompanyCustomer` (company name, IČO, DIČ, contact person)
validate themselves (cv07 p.3, p.16). The business layer and `CustomerRepository` are the same for every
strategy; only the data access layer and the schema change (cv07 p.4, p.14). The design of all three strategies,
with DDL, the cv07 p.12 comparison, the final check of the tasks and the examiner questions, is in
[`docs/cv07-inheritance-mapping.md`](docs/cv07-inheritance-mapping.md).

### Switching the strategy

`--mapping=cti|sti` or `-Dvis.customerMapping=cti|sti`, default **cti**, handled only by `ApplicationContext`.
Each strategy has its own store, because `orders` and `addresses` can reference only one customer table:

| Strategy | H2 file | JSON folder | Customer tables / files |
|---|---|---|---|
| CTI | `data/vis-h2-cti.mv.db` | `data/json-cti/` | `customers`, `person_customers`, `company_customers` |
| STI | `data/vis-h2-sti.mv.db` | `data/json-sti/` | `customers_sti` |

The demonstration (`--demo`, section "Mapování dědičnosti") writes to `data/demo/cv07/cti/` and
`data/demo/cv07/sti/` and prints which tables or files each new customer was written to.

### Showing the rows in the H2 console (presentation)

Run the demo on H2 first (`mvn compile exec:java -Dexec.args="--storage=h2 --demo"`), then start the console that
comes with the H2 driver:

```bash
java -cp ~/.m2/repository/com/h2database/h2/2.2.224/h2-2.2.224.jar org.h2.tools.Console
```

Login: driver `org.h2.Driver`, **JDBC URL** `jdbc:h2:file:<absolute path to cv3>/data/demo/cv07/cti/vis-h2-cti`
(for STI `…/data/demo/cv07/sti/vis-h2-sti`; for your own data `…/data/vis-h2-cti`), **user** `sa`, **password**
empty. The URL has no `.mv.db` suffix. The application opens a connection only for each operation, so the file
is free while the console is connected; close the console before running the application again.

```sql
-- CTI: one person = customers + person_customers, one company = customers + company_customers
SELECT c.customer_id, c.customer_type, c.email, c.billing_city, p.first_name, p.last_name, p.birth_date
FROM customers c JOIN person_customers p ON p.customer_id = c.customer_id;
SELECT c.customer_id, c.customer_type, c.email, f.company_name, f.ico, f.dic
FROM customers c JOIN company_customers f ON f.customer_id = c.customer_id;
SELECT o.order_id, o.customer_id, c.customer_type FROM orders o JOIN customers c ON c.customer_id = o.customer_id;

-- STI: both in one table, the other type's columns are NULL
SELECT customer_id, customer_type, email, first_name, last_name, birth_date, company_name, ico, dic
FROM customers_sti;
SELECT o.order_id, o.customer_id, s.customer_type FROM orders o JOIN customers_sti s ON s.customer_id = o.customer_id;
```

### Tasks (cv07 p.16) → classes → tests

| Task | Classes | Tests |
|---|---|---|
| 1 – hierarchy in BL | `Customer` (abstract), `PersonCustomer`, `CompanyCustomer`, `BillingAddress`; `CustomerService.createPersonCustomer/createCompanyCustomer`; `PlaceOrderService.CustomerData` (caller chooses the type) | `CustomerHierarchyTest`, `PlaceOrderUnitOfWorkTest` |
| 2 – design of STI, CTI, Concrete | [`docs/cv07-inheritance-mapping.md`](docs/cv07-inheritance-mapping.md) (DDL checked in H2) | – |
| 3 – implementation: CTI (cv07 p.7–8) | `dataaccess.mapper.customer.CtiCustomerTables` over `CustomerGateway`, `PersonCustomerGateway`, `CompanyCustomerGateway` (`H2…` / `Json…`); `H2Schema.cti()` | `InheritanceMappingScenariosTest`, `InheritanceStrategyRulesTest` (rollback, exactly one subtype row), `H2StorageContractTest`, `JsonStorageContractTest` |
| 3 – implementation: STI (cv07 p.5–6) | `StiCustomerTables` over `StiCustomerGateway` (`H2…` / `Json…`); `H2Schema.sti()` with `ck_customers_sti_type` | `InheritanceMappingScenariosTest`, `InheritanceStrategyRulesTest` (CHECK, discriminator), `H2StiStorageContractTest`, `JsonStiStorageContractTest` |
| 3 – Inheritance Mappers (cv07 p.13) | `CustomerInheritanceMapper` (common: ID, e-mail, phone, billing) + `PersonCustomerMapper`, `CompanyCustomerMapper` | all of the above |
| 3 – orders reference the customer (cv07 p.11, p.15) | `H2Schema` FKs to the root table; `JsonOrderGateway`/`JsonAddressGateway` check the same file; `OrderDataMapper` lazy `getCustomer()` | `InheritanceMappingScenariosTest.anOrderReferencesAnExistingCustomer`, `…reloadedOrderHasItsCustomerWithTheRightTypeAndValues` |
| Identity Map over a hierarchy | `IdentityMap.keyFor` uses the hierarchy root | `IdentityMapTest` |
| Old data | `SchemaVersion` (version 3), `H2Schema`, `JsonFiles` | `CustomerStorageRulesTest` |

**CV04 comparison:** the isolated `cv04` package (TDG, RDG, Active Record) intentionally does **not** map the
customer hierarchy. It has its own `cv04_customers` table, and slide 13 says that with Gateway or Active Record
"nemusíte kvůli tomu zavádět Data Mapper"; the point of CV07 is shown on the application's Data Mapper.

### Removing one strategy (next lab)

**Keep only CTI** – delete:

- classes: `dataaccess/mapper/customer/StiCustomerTables`, `dataaccess/gateway/StiCustomerGateway`,
  `dataaccess/h2/H2StiCustomerGateway`, `dataaccess/persistence/JsonStiCustomerGateway`,
  `dataaccess/dto/StiCustomerDto`, `dataaccess/row/StiCustomerRow`
- DDL: `H2Schema.STI_CUSTOMERS` and `H2Schema.sti()`
- factories: `H2Storage.stiGateways`, `JsonStorage.stiGateways`
- config: the STI branch in `ApplicationContext.create` and `STI` in `CustomerMapping` (or remove the enum,
  `--mapping` in `Main` and the loop in `Cv07Demonstrations`)
- tests: `H2StiStorageContractTest`, `JsonStiStorageContractTest`, the `sti…` tests in
  `InheritanceStrategyRulesTest`, `STI` in `InheritanceMappingScenariosTest.combinations()`
- docs: the STI rows here and in `docs/cv07-inheritance-mapping.md` (keep the design section, task 2 asks for it)

**Keep only STI** – delete:

- classes: `CtiCustomerTables`, `CustomerGateway`, `PersonCustomerGateway`, `CompanyCustomerGateway`,
  `H2CustomerGateway`, `H2PersonCustomerGateway`, `H2CompanyCustomerGateway`, `JsonCustomerGateway`,
  `JsonPersonCustomerGateway`, `JsonCompanyCustomerGateway`, `PersonCustomerRow`, `CompanyCustomerRow`,
  `CustomerRow` (`CustomerDto`, `PersonCustomerDto`, `CompanyCustomerDto` stay: the Inheritance Mappers use them)
- DDL: `H2Schema.CTI_CUSTOMERS` and `H2Schema.cti()`; make `H2Schema.create(Connection)` and the
  `H2Database(Path)` default use STI
- factories: `H2Storage.gateways`/`JsonStorage.gateways` become the STI versions; the no-argument
  `JsonOrderGateway`/`JsonAddressGateway` constructors must use `JsonStiCustomerGateway::exists`
- config: the CTI branch in `ApplicationContext.create`, `CTI` in `CustomerMapping` (default becomes STI)
- tests: `H2StorageContractTest`, `JsonStorageContractTest` (or switch them to STI), the `cti…` tests in
  `InheritanceStrategyRulesTest`, `CTI` in `InheritanceMappingScenariosTest.combinations()`; tests that build
  gateways directly (`CustomerStorageRulesTest`, `H2DatabaseTest`, `UnitOfWorkFailureRecoveryTest`,
  `support.CountingStorage`) use the CTI defaults and must switch to the STI ones
- docs: the CTI rows here and in the design doc's implementation notes

## CV06 patterns – where to find them

| Pattern | Domain | Data access | Schema (`H2Schema`) | Test |
|---|---|---|---|---|
| Identity Field | `customerId`, `orderId`, … fields | `IdGenerator` (UUID, assigned before insert) via the repositories' `nextId()` | `VARCHAR(64) PRIMARY KEY` | `IdentityFieldTest` |
| Foreign Key Mapping | `Order.customerId` + lazy `getCustomer()` | `H2OrderGateway`, `OrderDataMapper` | `fk_orders_customer`, `fk_orders_material`, `fk_addresses_customer` | `ForeignKeyMappingTest` |
| Association Table Mapping | `Material.finishes` (M:N) | `H2MaterialFinishGateway.synchronize` (writes only the diff), `MaterialDataMapper` | `material_finishes`, composite PK + 2 FKs | `AssociationTableMappingTest` |
| Dependent Mapping | `Order` owns `OrderItem` (package-private constructor) | `H2OrderGateway` (delete + re-insert items in one transaction) | `order_items` PK `(order_id, line_number)`, `ON DELETE CASCADE` | `DependentMappingTest` |
| Embedded Value | `OrderSpecification` = the slides' `Dimensions` (width, height, depth in **cm**, plus design notes; no ID, equals by value) | `OrderMapper` flattens it into columns | `width, height, depth, design_notes` in `orders` | `EmbeddedValueTest` |
| Serialized LOB | `ProductConfiguration` (immutable snapshot) | `ProductConfigurationSerializer` (versioned JSON) | `order_items.configuration_json CLOB` | `SerializedLobTest` |

`StorageContractTest` runs the same scenarios against both storages (`JsonStorageContractTest`,
`H2StorageContractTest`). `H2DatabaseTest` checks the constraints directly through JDBC.

## CV08 patterns – where to find them

The price of an order (cv08 p.3, p.17): items (`Money`) → discount (`DiscountPolicy`) → shipping
(`IShippingService`) → total, in `business.service.OrderPricingService`. The business logic is the same in both
shipping modes; `ApplicationContext.orderPricingService(ShippingMode)` decides which implementation is used.
Console: `--shipping=stub|gateway` (or `-Dvis.shipping=…`, default `gateway`), menu item 14 prices an order with an
optional percentage discount; `--demo` runs `Cv08Demonstrations` (`data/demo/cv08/`). No new tables, no real
external service and no new persistence (cv08 p.18).

| Pattern | Layer | Classes | Test (cv08 p.19 scenario) |
|---|---|---|---|
| Layer Supertype | BL | `BaseEntity` (`id`, `createdAt`, `updatedAt`, `markUpdated()`); `Customer`, `Order`, `Material`, `Finish`, `Address` extend it, the Customer hierarchy stays; `Order` calls `markUpdated()` on every change | `LayerSupertypeTest` (change of an order → `updatedAt`) |
| Value Object | BL | `Money` (BigDecimal + `Currency`, two decimals, immutable, never negative, equal by value, `plus`, `times`, no mixing currencies); `OrderItem.unitPrice`, `OrderItem.totalPrice()` | `MoneyTest` (two equal prices, negative price) |
| Special Case | BL | `DiscountPolicy` ← `PercentageDiscount`, `NoDiscount` (used instead of `null`) | `DiscountPolicyTest` (no discount, 10 %) |
| Gateway | DAL | `dataaccess.shipping.ShippingGateway` implements the BL interface `business.service.IShippingService`; talks to `SimulatedShippingApi` (the external system: 1–3 pcs 120 Kč / 2 days, 4+ pcs 250 Kč / 4 days) | `ShippingTest` (2 pcs → 120 Kč, 2 days) |
| Service Stub | DAL | `ShippingServiceStub` (always 150 Kč, 3 days) | `ShippingTest` (150 Kč, 3 days) |
| Mapper | DAL | `ShippingMapper` (pieces → `{"item_count"}`, `{"cost_czk", "eta_days"}` → `ShippingQuote(Money, deliveryDays)`) | `ShippingTest` (external answer → correct `ShippingQuote`) |
| All together | BL | `OrderPricingService`, `OrderPriceSummary` | `OrderPricingServiceTest` (same order in both modes, with and without discount) |

Notes for the defence:

- `createdAt`/`updatedAt` of `BaseEntity` live in the domain model only. `Order.createdAt` was already stored;
  `updatedAt` is not, so a reloaded entity starts with `updatedAt = createdAt` (cv08 p.18: no new persistence).
- Storage keeps the item price as `NUMERIC(12,2)`; `OrderMapper` adds the currency CZK when loading.
- `IdentityMap.keyFor` stops the hierarchy root below `BaseEntity`, so a customer and an order with the same ID
  still have different keys.
- `IShippingService` keeps the slides' C#-style name on purpose.

## CV09 – GoF návrhové vzory

Five GoF patterns on the order life cycle Nová → Potvrzená → Ve výrobě → Připravena k expedici → Expedovaná →
Uzavřená (`OrderStatus`, Czech labels via `getLabel()`; the allowed transitions live in `OrderStatus`/`Order`, an
invalid one throws and changes nothing). All pattern classes are in the business layer; the console only picks
the command, strategy or document creator and prints the result. Console: menu 11 (confirm) and 15/16 (start
production, prepare for expedition) run commands through one `OrderCommandExecutor`, 17 prices an order with a
chosen strategy, 18 prints a document, 19 shows the command history; `ProductionObserver` and `ExpeditionObserver`
are registered at startup and write to the console. `--demo` runs `Cv09Demonstrations` (`data/demo/cv09/`).
No new tables and no change of the storage layout.

| Pattern | Business layer classes | Where it's used | Test | Demo (`Cv09Demonstrations`) |
|---|---|---|---|---|
| Singleton | `service.numbering.OrderNumberGenerator` (eager instance, private constructor, synchronized `getNextNumber()`) | `OrderService.newOrder` assigns `Order.assignNumber(...)` to every new order (also via `PlaceOrderService`) | `OrderNumberGeneratorTest` | same instance, three consecutive numbers |
| Strategy | `pricing.IPricingStrategy` ← `StandardPricing`, `CompanyPricing` (−10 %) | `OrderPricingService.calculatePrice(orderId, strategy)`; menu 17 | `PricingStrategyTest`, `OrderPricingStrategyServiceTest` | scenario A: 2 000 Kč / 1 800 Kč |
| Factory Method | `document.DocumentCreator` (`produce`/`render`, abstract `createDocument`) ← `ConfirmationCreator`, `DeliveryNoteCreator`, `InvoiceCreator`; products `OrderDocument` ← `OrderConfirmation`, `DeliveryNote`, `Invoice` | `service.OrderDocumentService.createDocument(orderId, creator)`; menu 18 | `DocumentFactoryMethodTest` | scenarios B, C: confirmation, delivery note, invoice (extension) |
| Observer | `observer.IOrderObserver` ← `ProductionObserver`, `ExpeditionObserver`; `Order` is the subject (`registerObserver`, `unregisterObserver`, `publishStatusChanges`) | `OrderService.registerObserver/unregisterObserver`, status changes in `OrderService` | `OrderObserverTest` | scenarios B, C, unregistering |
| Command | `command.IOrderCommand` ← `ConfirmOrderCommand`, `StartProductionCommand`, `PrepareExpeditionCommand`; invoker `OrderCommandExecutor` with `CommandLogEntry` log | menu 11, 15, 16, 19 | `OrderCommandTest`, `OrderStatusTransitionTest` | scenarios B, C, invalid transition, log |

Notes for the defence:

- **Singleton ≠ persistence.** The order number lives in memory only (the slide allows it); it restarts at 1001
  after a restart and an order loaded from storage has no number, so messages and documents then show its ID.
- **Observers are notified only after a successful commit** (slide "Důležité"): `Order` records the status change
  as pending, `OrderService` commits and only then publishes; a failed commit discards the change, a rejected
  transition throws before anything is recorded. The service attaches its observers to the order only for the
  announcement, so an unregistered observer is never reached through a cached order.
- `CompanyPricing` is not combined with the CV08 `DiscountPolicy`: the CV08 calculation (`calculate`) keeps
  using `StandardPricing` plus the discount, so a company price is never discounted twice.
- **Command has no Undo** (as on the slide): a rejected command (e.g. production of an unconfirmed order) is
  logged as a failure with the reason, not rethrown.
- Lombok removes boilerplate: `@RequiredArgsConstructor` + `@Getter` on the commands, `@RequiredArgsConstructor`
  on the observers and `OrderDocumentService`, `@Getter` on `OrderDocument`, `@Getter @RequiredArgsConstructor`
  on the `OrderStatus` labels.

## Reconstruction notes

These are the original files, unchanged except where noted:

- `business/domainmodel/`: `Customer`, `Order`, `OrderItem`, `OrderSpecification`, `ProductConfiguration`,
  `Material`, `Finish`
- `business/service/MaterialService` (added only `findAllMaterials()`)
- `dataaccess/h2/`: `H2Schema`, `H2Database`, `H2Transaction`, `H2OrderRepository`, `H2CustomerRepository`,
  `H2MaterialFinishRepository` (the last three are now `H2…Gateway`, see below)
- `dataaccess/mapper/`: `OrderMapper`, `ProductConfigurationSerializer`
- `dataaccess/identity/IdGenerator`, `dataaccess/dto/OrderDto`, `dataaccess/unitofwork/UnitOfWork`

Original files modified since the reconstruction (fix plan after the audit):

- `dataaccess/unitofwork/UnitOfWork`: `dependencyRank` puts Address after Customer (rank 1), so the Unit of Work
  itself inserts customer → address → order and deletes in reverse (cv05 p.8 "Proč záleží na pořadí")
  and, in Phase 3, implements `business.repository.UnitOfWorkControl` and clears the Identity Map when
  `commit()` ends the unit of work (cv05 p.9 "nejde o globální cache")
- `business/domainmodel/Customer`, `Order`: `setAddressesLoader` / `setCustomerLoader` only store the loader
  (they used to load eagerly when data was already loaded, audit P-m4)
- `business/domainmodel/Order`: new `Order.restore(...)` reconstitution factory (for loading only); the public
  `restoreItem` it replaces was removed (audit P-M1, P-m3)
- `dataaccess/mapper/OrderMapper`: uses `Order.restore` and maps the status by name instead of calling
  `confirm()` (P-M1)
- `dataaccess/unitofwork/UnitOfWork`: the two constructors that defaulted to JSON were removed (P-H6)
- CV07 phase 1: `business/domainmodel/Customer` is now the abstract base of `PersonCustomer`/`CompanyCustomer`
  (common email, phone, `BillingAddress`; `name` replaced by `getDisplayName()`; Lombok builder and `transient`
  removed); `dataaccess/h2/H2Schema` and `H2CustomerGateway` store both subtypes in `customers` with a
  `customer_type` discriminator (interim layout, CV07 phase 3 turns it into STI and adds CTI)
- CV07 phase 3: `dataaccess/h2/H2Schema` builds the customer tables of the active strategy (`cti()`: `customers`
  + `person_customers` + `company_customers`; `sti()`: `customers_sti`) and points the `orders`/`addresses` FKs at
  that root table; `H2CustomerGateway` now maps only the CTI common table `customers` (the interim 15-column
  version became `H2StiCustomerGateway`); schema version 3
- `H2OrderRepository`, `H2CustomerRepository`, `H2MaterialFinishRepository`: renamed to `H2OrderGateway`,
  `H2CustomerGateway`, `H2MaterialFinishGateway` (package `dataaccess.repository` → `dataaccess.gateway`); only the
  names, imports and Javadoc changed, the SQL and logic did not
- `dataaccess/h2/H2Database`: an extra constructor takes the schema to create, so the CV04 comparison can use
  its own tables (`cv04.Cv04Schema`); the existing constructor still creates `H2Schema`
- `MaterialService`: imports and type names updated for the rename; in Phase 3 it uses the business-layer
  `MaterialRepository`/`FinishRepository` instead of gateways, mappers and the Identity Map (audit P-H2/P-H3);
  the JSON-only convenience constructor was removed (P-H6)

- CV08 phase 1: `Customer`, `Order`, `OrderItem`, `Material`, `Finish` (and the non-original `Address`) use the
  Layer Supertype `BaseEntity` (their ID fields moved there, the `get…Id()` getters stay); `Order` calls
  `markUpdated()` on changes; `OrderItem.unitPrice` is `Money`; `OrderMapper` converts it to and from `BigDecimal`

All other classes, the tests, `pom.xml` and this README are new. Their names and packages follow the
original layout, but their contents are not the original contents. The following were not recreated:

- the original assignment text (`docs/h2-persistence-task.md`)
- the full `PROJECT_GUARDRAILS.md`
- the rendered class diagram SVG

## Known trade-offs (be ready to defend)

- **Jackson** is used for the JSON files and the Serialized LOB format. It is a serialization library, not an
  ORM, and it is only used in the data access layer.
- **Lombok** annotations on domain classes are compile-time code generation, not persistence metadata.
- **JSON transactions** recover at application level. They do not survive a process crash in the middle of
  a commit. H2 provides real ACID transactions.
