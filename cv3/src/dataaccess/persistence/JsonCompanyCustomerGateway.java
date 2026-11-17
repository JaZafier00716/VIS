package dataaccess.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import dataaccess.dto.CompanyCustomerDto;
import dataaccess.gateway.CompanyCustomerGateway;
import dataaccess.row.CompanyCustomerRow;

import java.util.List;
import java.util.Optional;

/**
 * CTI: Table Data Gateway used by the Data Mapper (JSON file company_customers.json). Same rules as the H2 table:
 * the ID is a primary key and must exist in customers.json (PK + FK), company name and IČO are NOT NULL.
 */
public class JsonCompanyCustomerGateway implements CompanyCustomerGateway {
    static final String FILE = "company_customers.json";
    private static final String ENTITY = "Firma";
    private static final TypeReference<List<CompanyCustomerRow>> TYPE = new TypeReference<>() {};

    @Override
    public void insert(CompanyCustomerDto company) {
        String id = RecordIds.require(company.customerId(), ENTITY);
        check(company);
        List<CompanyCustomerRow> rows = JsonFiles.read(FILE, TYPE);
        if (rows.stream().anyMatch(row -> row.customerId().equals(id))) {
            throw RecordIds.duplicate(ENTITY, id);
        }
        rows.add(toRow(company));
        JsonFiles.write(FILE, rows);
    }

    @Override
    public void update(CompanyCustomerDto company) {
        String id = RecordIds.require(company.customerId(), ENTITY);
        check(company);
        List<CompanyCustomerRow> rows = JsonFiles.read(FILE, TYPE);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).customerId().equals(id)) {
                rows.set(i, toRow(company));
                JsonFiles.write(FILE, rows);
                return;
            }
        }
        throw RecordIds.missing(ENTITY, id);
    }

    @Override
    public void delete(String id) {
        deleteRow(id);
    }

    @Override
    public Optional<CompanyCustomerDto> findById(String id) {
        return JsonFiles.read(FILE, TYPE).stream().filter(row -> row.customerId().equals(id)).findFirst()
                .map(row -> new CompanyCustomerDto(row.customerId(), row.companyName(), row.ico(), row.dic(),
                        row.contactPerson()));
    }

    /** Also used as the ON DELETE CASCADE from customers.json. */
    static void deleteRow(String id) {
        List<CompanyCustomerRow> rows = JsonFiles.read(FILE, TYPE);
        if (rows.removeIf(row -> row.customerId().equals(id))) {
            JsonFiles.write(FILE, rows);
        }
    }

    private static void check(CompanyCustomerDto company) {
        if (!JsonCustomerGateway.exists(company.customerId())) {
            throw new IllegalArgumentException("Firma " + company.customerId() + " nemá záznam v customers.");
        }
        if (company.companyName() == null || company.ico() == null) {
            throw new IllegalArgumentException("Název firmy a IČO nesmí být prázdné.");
        }
    }

    private static CompanyCustomerRow toRow(CompanyCustomerDto dto) {
        return new CompanyCustomerRow(dto.customerId(), dto.companyName(), dto.ico(), dto.dic(), dto.contactPerson());
    }
}
