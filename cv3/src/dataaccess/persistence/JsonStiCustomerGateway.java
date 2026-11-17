package dataaccess.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import dataaccess.dto.StiCustomerDto;
import dataaccess.gateway.StiCustomerGateway;
import dataaccess.row.StiCustomerRow;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * STI: Table Data Gateway used by the Data Mapper (JSON file customers_sti.json, both subtypes). Enforces the same
 * rules as the H2 table, including ck_customers_sti_type: a person row has the person columns and no company
 * columns, and the other way round.
 */
public class JsonStiCustomerGateway implements StiCustomerGateway {
    static final String FILE = "customers_sti.json";
    private static final String ENTITY = "Zákazník";
    private static final TypeReference<List<StiCustomerRow>> TYPE = new TypeReference<>() {};

    @Override
    public void insert(StiCustomerDto customer) {
        String id = RecordIds.require(customer.customerId(), ENTITY);
        checkColumns(customer);
        List<StiCustomerRow> rows = JsonFiles.read(FILE, TYPE);
        if (rows.stream().anyMatch(row -> row.customerId().equals(id))) {
            throw RecordIds.duplicate(ENTITY, id);
        }
        rows.add(toRow(customer));
        JsonFiles.write(FILE, rows);
    }

    @Override
    public void update(StiCustomerDto customer) {
        String id = RecordIds.require(customer.customerId(), ENTITY);
        checkColumns(customer);
        List<StiCustomerRow> rows = JsonFiles.read(FILE, TYPE);
        rows.set(indexOf(rows, id), toRow(customer));
        JsonFiles.write(FILE, rows);
    }

    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        List<StiCustomerRow> rows = JsonFiles.read(FILE, TYPE);
        int index = indexOf(rows, id);
        JsonCustomerReferences.requireNoOrders(id);
        rows.remove(index);
        JsonFiles.write(FILE, rows);
        JsonCustomerReferences.deleteAddresses(id);
    }

    @Override
    public Optional<StiCustomerDto> findById(String id) {
        return JsonFiles.read(FILE, TYPE).stream().filter(row -> row.customerId().equals(id))
                .findFirst().map(JsonStiCustomerGateway::toDto);
    }

    @Override
    public List<StiCustomerDto> findAll() {
        return JsonFiles.read(FILE, TYPE).stream().map(JsonStiCustomerGateway::toDto).toList();
    }

    static boolean exists(String id) {
        return JsonFiles.read(FILE, TYPE).stream().anyMatch(row -> row.customerId().equals(id));
    }

    /** ck_customers_type, ck_customers_sti_billing and ck_customers_sti_type of the H2 schema. */
    private static void checkColumns(StiCustomerDto c) {
        JsonCustomerReferences.checkType(c.customerType());
        JsonCustomerReferences.checkBilling(c.billingStreet(), c.billingCity(), c.billingZipCode(),
                c.billingCountry());
        boolean personFilled = c.firstName() != null && c.lastName() != null && c.birthDate() != null;
        boolean noPerson = c.firstName() == null && c.lastName() == null && c.birthDate() == null;
        boolean companyFilled = c.companyName() != null && c.ico() != null;
        boolean noCompany = Stream.of(c.companyName(), c.ico(), c.dic(), c.contactPerson()).allMatch(v -> v == null);
        boolean valid = "P".equals(c.customerType()) ? personFilled && noCompany : companyFilled && noPerson;
        if (!valid) {
            throw new IllegalArgumentException("Řádek customers_sti typu " + c.customerType()
                    + " nemá vyplněné správné sloupce.");
        }
    }

    private static StiCustomerRow toRow(StiCustomerDto dto) {
        return new StiCustomerRow(dto.customerId(), dto.customerType(), dto.email(), dto.phone(),
                dto.billingStreet(), dto.billingCity(), dto.billingZipCode(), dto.billingCountry(), dto.firstName(),
                dto.lastName(), dto.birthDate() == null ? null : dto.birthDate().toString(), dto.companyName(),
                dto.ico(), dto.dic(), dto.contactPerson());
    }

    private static StiCustomerDto toDto(StiCustomerRow row) {
        return new StiCustomerDto(row.customerId(), row.customerType(), row.email(), row.phone(),
                row.billingStreet(), row.billingCity(), row.billingZipCode(), row.billingCountry(), row.firstName(),
                row.lastName(), row.birthDate() == null ? null : LocalDate.parse(row.birthDate()),
                row.companyName(), row.ico(), row.dic(), row.contactPerson());
    }

    private static int indexOf(List<StiCustomerRow> rows, String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).customerId().equals(id)) {
                return i;
            }
        }
        throw RecordIds.missing(ENTITY, id);
    }
}
