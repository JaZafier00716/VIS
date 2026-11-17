# Project guardrails (reconstructed summary)

> The original guardrails file was not available when this project was rebuilt. This is a short
> reconstruction of the rules the code follows. Replace it with your original file if you still have it.

1. **Three layers, one direction:** presentation → business → data access. The presentation layer never
   touches gateways, SQL or files.
   **Dependency rule:** no class in `business` imports anything from `dataaccess`. The business layer owns
   the repository interfaces and `UnitOfWorkControl` (`business.repository`), which depend only on domain
   classes; `dataaccess` implements them (Data Mappers, `UnitOfWork`). Only `configuration.ApplicationContext`
   knows both sides and wires them. Check: `grep -rn "dataaccess" src/business` must find nothing.
2. **Clean domain:** domain classes contain no SQL, no JDBC, no file I/O and no persistence annotations.
   Persistence-only concerns (IDs as strings, serialization, row layout) live in mappers and gateways.
3. **No ORM, no framework:** persistence is hand-written JDBC (`java.sql.*`) or JSON files. `ApplicationContext`
   wires dependencies by hand.
4. **One business operation = one Unit of Work commit.** Services change domain objects, hand them to the
   repositories (which register them in the Unit of Work) and call `commit()` once at the end of the operation.
   On failure they call `rollback()` and rethrow. Gateways never commit on their own while a Unit of Work
   transaction is open. The Identity Map lives for one unit of work only: `commit()` and `rollback()` clear it.
5. **Data Mapper on top of Table Data Gateways.** Each `…Gateway` maps one table (or JSON file; the order
   gateway includes its dependent `order_items`), takes and returns DTOs only and holds no state, Identity Map
   or Unit of Work logic. The `…DataMapper` classes build domain objects from the gateways' records, check the
   Identity Map and attach the Lazy Load loaders.
6. **Both storages behave the same.** Integrity rules enforced by the H2 schema (PK, FK, CASCADE, CHECK) are
   also enforced by the JSON gateways. `StorageContractTest` must pass for both.
7. **Every pattern is proven by a test.** Each CV04–CV09 pattern has a test class that shows its behaviour,
   not just its existence.
8. **Inheritance mapping stays in the data access layer (CV07).** The business layer knows only the hierarchy
   `Customer` → `PersonCustomer` / `CompanyCustomer` and `CustomerRepository`; it never sees the strategy or the
   table layout. The strategy (CTI or STI) is chosen in `ApplicationContext` only, each strategy lives in its own
   classes (`Cti…` / `Sti…` plus its gateways) and its own store, and the Inheritance Mappers (common part +
   one per subtype) are shared by both. CTI writes both rows in the Unit of Work transaction and enforces exactly
   one subtype row; STI relies on the discriminator and its CHECK constraint.
   `CustomerTables.tablesOf` / `ApplicationContext.customerTables` are a diagnostic aid for demonstrations only:
   nothing in `business` may use them.
9. **External services behind a business interface (CV08).** The business layer owns `IShippingService` and
   works with `ShippingQuote`/`Money` only; `ShippingGateway` (with `ShippingMapper`) and `ShippingServiceStub`
   live in `dataaccess.shipping` and are chosen by `ApplicationContext` (`ShippingMode`). Price rules of the
   carrier stay in the simulated external API, never in the Gateway; common entity attributes live in
   `BaseEntity`, money is always `Money`, and a missing discount is `NoDiscount`, never `null`.
10. **Small steps:** change one pattern at a time, run `mvn test`, then update the README table.
11. **GoF patterns live in the business layer (CV09).** Singleton (`business.service.numbering`), Strategy
   (`business.pricing`), Factory Method (`business.document`), Observer (`business.observer`) and Command
   (`business.command`) are business classes. The presentation only selects the command, strategy or creator
   object and prints the result. Observers are notified only after a successful commit, never for a rejected
   or unsaved change. The business layer has no if/switch chains that choose the algorithm or the document type:
   the caller passes the strategy or creator object.
