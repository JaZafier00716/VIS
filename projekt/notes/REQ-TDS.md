# TDS requirements digest

Condensed from `TDS/Semestrální projekt TDS I.pdf` ("Semestrální projekt TDS I.", Oracle
Academy Database Design & Programming with SQL). The PDF gives no deadlines or grading.

## General

- At least 10 tables; everything in English (tables, attributes …); all data types incl. BLOB;
  non-trivial amount of data.
- Logical and relational model drawn in Oracle SQL Developer Data Modeler.
- Queries must work on the school Oracle server `dbsys.cs.vsb.cz` (the PDF misspells it
  `dbsys.cs.vb.cz`); use only Oracle SQL Developer.
- Every item below must appear; one task may cover several items.

## Database Design part

Where covered = section of `TDS/db/tds-design-notes.md` (checklist table unless noted).

| Item | Where covered |
|---|---|
| DD S01 L02 data vs information | checklist table |
| DD S02 L02 entities, instances, attributes, identifiers | checklist table |
| DD S03 L01 every relationship in two sentences | "Relationships" |
| DD S03 L02 ER diagram | Data Modeler (John) – to do |
| DD S30 L04 matrix diagram | "Relationship matrix" |
| DD S04 L01 supertype/subtype | `project_nodes` STI |
| DD S04 L02 business rules | "Business rules" |
| DD S05 L01 transferable + non-transferable relationship | responsible worker / triggers |
| DD S05 L03 M:N without and with attributes | `user_roles`, `product_allergens` / `menu_items` |
| DD S06 L01 identifying 1:N (FK in PK) | `menu_revisions` |
| DD S06 L02–04 1NF, 2NF, 3NF | "Normal forms" |
| DD S07 L01 ARC | `comments` |
| DD S07 L02 hierarchical and recursive relationships | `project_nodes.parent_id`, `users.supervisor_id` |
| DD S07 L03 historical data | `menu_revisions` |
| DD S09 L01 changes over time | `menu_revisions`, `share_links` |
| DD S09 L02 journaling | `product_price_history` + trigger |
| DD S10 L01 readability conventions | checklist table |
| DD S10 L02 generic modelling | checklist table |
| DD S11 L01 integrity constraints | checklist table |
| DD S11 L02–04 relational schema from the conceptual model | checklist table |
| all data types | "data types" table |

## SQL part – done 2026-11-16: query catalogue (`TDS/db/04_queries.sql`, every category below; external table and public synonym are commented out locally because CATERING lacks the privilege)

| Item | Needed |
|---|---|
| DD S15–S17 | `||`, CONCAT, DISTINCT; WHERE; LOWER/UPPER/INITCAP; BETWEEN, LIKE (`%`, `_`), IN, IS [NOT] NULL; AND/OR/NOT with parentheses; ORDER BY one/more columns ASC/DESC; single-row and MIN/MAX/AVG/SUM/COUNT |
| SQL S01 | CONCAT, SUBSTR, LENGTH, INSTR, LPAD, RPAD, TRIM, REPLACE, DUAL; ROUND, TRUNC (2 decimals, thousands), MOD; MONTHS_BETWEEN, ADD_MONTHS, NEXT_DAY, LAST_DAY, ROUND/TRUNC on dates, SYSDATE |
| SQL S02 | TO_CHAR, TO_NUMBER, TO_DATE; NVL, NVL2, NULLIF, COALESCE; DECODE, CASE, IF-THEN-ELSE |
| SQL S03 | NATURAL JOIN, CROSS JOIN; JOIN USING, JOIN ON; LEFT/RIGHT/FULL OUTER JOIN; self-join with aliases (supervisor); START WITH / CONNECT BY PRIOR / LEVEL |
| SQL S04–S05 | AVG, COUNT, MIN, MAX, SUM, VARIANCE, STDDEV; COUNT(DISTINCT), NVL in aggregates, COUNT(*) vs COUNT(col); GROUP BY, HAVING; ROLLUP, CUBE, GROUPING SETS; UNION, UNION ALL, INTERSECT, MINUS with ORDER BY |
| SQL S06 | subqueries: single value, multi-column, EXISTS/NOT EXISTS, single-row, multi-row IN/ANY/ALL, NULLs in subqueries, WITH … AS |
| SQL S07 | INSERT (VALUES, column list, from SELECT); UPDATE, DELETE; DEFAULT, MERGE, multi-table INSERT |
| SQL S08 | objects (table, index, constraint, view, sequence, synonym); CREATE/ALTER/DROP/RENAME/TRUNCATE; external table (ORGANIZATION EXTERNAL, ORACLE_LOADER …); TIMESTAMP variants, INTERVAL types, CHAR/VARCHAR2/CLOB/NUMBER/BLOB; ALTER TABLE ADD/MODIFY/DROP; FLASHBACK TABLE … TO BEFORE DROP (USER_RECYCLEBIN); COMMENT ON TABLE; SET UNUSED |
| SQL S10 | NOT NULL/UNIQUE/PK/FK/CHECK; ON DELETE options; named vs system-named constraints; CREATE TABLE AS SELECT; USER_CONSTRAINTS |
| SQL S11 | CREATE VIEW, FORCE/NOFORCE, WITH CHECK OPTION, WITH READ ONLY, simple vs complex view; inline view |
| SQL S12 | CREATE/ALTER SEQUENCE with all options; CREATE INDEX, PK, UNIQUE, FK |
| SQL S13 | GRANT … TO PUBLIC, REVOKE, which privileges on which objects; REGEXP_LIKE/REPLACE/INSTR/SUBSTR/COUNT |
| SQL S14–S16 | COMMIT, ROLLBACK, SAVEPOINT; joins in WHERE and `(+)` outer joins; recap of anything not covered |

Already present in `01_schema.sql` (reuse in the catalogue): sequences, named constraints, CHECK,
ON DELETE CASCADE, indexes, a WITH READ ONLY complex view, COMMENT ON TABLE, all data types,
REGEXP_LIKE in a CHECK; `03_checks.sql` has a CONNECT BY query and SAVEPOINT/ROLLBACK.
