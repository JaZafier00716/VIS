/**
 * CV04 comparison of the four data-access patterns on one use case (cv04 p.2): "Zákazník zadá objednávku →
 * validace vstupu → uložení objednávky ve stavu CEKA_NA_POTVRZENI → potvrzení uživateli".
 *
 * <p>This package is an <b>isolated comparison, not part of the application</b>. Nothing in
 * {@code presentation}, {@code business}, {@code dataaccess} or {@code configuration} uses it, so the
 * application's rule "business imports nothing from dataaccess" is unaffected. Each pattern mirrors the slides'
 * layering inside its own package:
 * <ul>
 *   <li>{@code cv04.tdg} – Table Data Gateway: {@code business.TdgOrderService} (validation, status) calls
 *       {@code dataaccess.*TableGateway} (SQL per table, DTOs in and out).</li>
 *   <li>{@code cv04.rdg} – Row Data Gateway: {@code business.RdgOrderService} works with
 *       {@code dataaccess.*RowGateway} objects, one per row, each with its own SQL.</li>
 *   <li>{@code cv04.activerecord} – Active Record: data, domain rules ({@code validate()}) and SQL in one class.</li>
 *   <li>{@code cv04.datamapper} – Data Mapper: the application's own business services and repositories.</li>
 * </ul>
 *
 * <p>TDG, RDG and Active Record use plain JDBC on their own H2 file ({@code data/cv04-h2}) with their own tables
 * ({@code cv04_customers}, {@code cv04_materials}, {@code cv04_orders}, see {@link cv04.Cv04Schema}), so changes
 * to the application's schema (CV07 inheritance mapping of customers) cannot break them. The Data Mapper
 * demonstration uses the application's real storage.
 */
package cv04;
