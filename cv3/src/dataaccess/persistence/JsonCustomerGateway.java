package dataaccess.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import dataaccess.dto.CustomerDto;
import dataaccess.gateway.CustomerGateway;
import dataaccess.row.CustomerRow;

import java.util.List;
import java.util.Optional;

/**
 * CTI: Table Data Gateway used by the Data Mapper (JSON file customers.json, the common attributes).
 * Enforces the same rules as the H2 table: type P/C, billing address all or none, orders block the delete, and
 * the delete cascades to the subtype row and the addresses.
 */
public class JsonCustomerGateway implements CustomerGateway {
    static final String FILE = "customers.json";
    private static final String ENTITY = "Zákazník";
    private static final TypeReference<List<CustomerRow>> TYPE = new TypeReference<>() {};

    @Override
    public void insert(CustomerDto customer) {
        String id = RecordIds.require(customer.customerId(), ENTITY);
        checkColumns(customer);
        List<CustomerRow> rows = JsonFiles.read(FILE, TYPE);
        if (rows.stream().anyMatch(row -> row.customerId().equals(id))) {
            throw RecordIds.duplicate(ENTITY, id);
        }
        rows.add(toRow(customer));
        JsonFiles.write(FILE, rows);
    }

    @Override
    public void update(CustomerDto customer) {
        String id = RecordIds.require(customer.customerId(), ENTITY);
        checkColumns(customer);
        List<CustomerRow> rows = JsonFiles.read(FILE, TYPE);
        rows.set(indexOf(rows, id), toRow(customer));
        JsonFiles.write(FILE, rows);
    }

    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        List<CustomerRow> rows = JsonFiles.read(FILE, TYPE);
        int index = indexOf(rows, id);
        JsonCustomerReferences.requireNoOrders(id);
        rows.remove(index);
        JsonFiles.write(FILE, rows);
        JsonPersonCustomerGateway.deleteRow(id);
        JsonCompanyCustomerGateway.deleteRow(id);
        JsonCustomerReferences.deleteAddresses(id);
    }

    @Override
    public Optional<CustomerDto> findById(String id) {
        return JsonFiles.read(FILE, TYPE).stream().filter(row -> row.customerId().equals(id))
                .findFirst().map(JsonCustomerGateway::toDto);
    }

    @Override
    public List<CustomerDto> findAll() {
        return JsonFiles.read(FILE, TYPE).stream().map(JsonCustomerGateway::toDto).toList();
    }

    static boolean exists(String id) {
        return JsonFiles.read(FILE, TYPE).stream().anyMatch(row -> row.customerId().equals(id));
    }

    private static void checkColumns(CustomerDto customer) {
        JsonCustomerReferences.checkType(customer.customerType());
        JsonCustomerReferences.checkBilling(customer.billingStreet(), customer.billingCity(),
                customer.billingZipCode(), customer.billingCountry());
    }

    private static CustomerRow toRow(CustomerDto dto) {
        return new CustomerRow(dto.customerId(), dto.customerType(), dto.email(), dto.phone(), dto.billingStreet(),
                dto.billingCity(), dto.billingZipCode(), dto.billingCountry());
    }

    private static CustomerDto toDto(CustomerRow row) {
        return new CustomerDto(row.customerId(), row.customerType(), row.email(), row.phone(), row.billingStreet(),
                row.billingCity(), row.billingZipCode(), row.billingCountry());
    }

    private static int indexOf(List<CustomerRow> rows, String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).customerId().equals(id)) {
                return i;
            }
        }
        throw RecordIds.missing(ENTITY, id);
    }
}
