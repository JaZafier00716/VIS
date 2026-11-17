package dataaccess.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import dataaccess.dto.AddressDto;
import dataaccess.mapper.AddressRowMapper;
import dataaccess.gateway.AddressGateway;
import dataaccess.row.AddressRow;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Table Data Gateway used by the Data Mapper (JSON file(s) addresses). */
public class JsonAddressGateway implements AddressGateway {
    static final String FILE = "addresses.json";
    static final TypeReference<List<AddressRow>> TYPE = new TypeReference<>() {};
    private static final String ENTITY = "Adresa";
    /** The FK to the strategy's customer root table (customers.json for CTI, customers_sti.json for STI). */
    private final Predicate<String> customerExists;

    /** Addresses of a CTI store (customers.json). */
    public JsonAddressGateway() {
        this(JsonCustomerGateway::exists);
    }

    public JsonAddressGateway(Predicate<String> customerExists) {
        this.customerExists = customerExists;
    }

    @Override
    public void insert(AddressDto address) {
        String id = RecordIds.require(address.addressId(), ENTITY);
        requireCustomer(address.customerId());
        List<AddressRow> rows = JsonFiles.read(FILE, TYPE);
        if (rows.stream().anyMatch(row -> row.addressId().equals(id))) {
            throw RecordIds.duplicate(ENTITY, id);
        }
        rows.add(AddressRowMapper.toRow(address));
        JsonFiles.write(FILE, rows);
    }

    @Override
    public void update(AddressDto address) {
        String id = RecordIds.require(address.addressId(), ENTITY);
        requireCustomer(address.customerId());
        List<AddressRow> rows = JsonFiles.read(FILE, TYPE);
        rows.set(indexOf(rows, id), AddressRowMapper.toRow(address));
        JsonFiles.write(FILE, rows);
    }

    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        List<AddressRow> rows = JsonFiles.read(FILE, TYPE);
        rows.remove(indexOf(rows, id));
        JsonFiles.write(FILE, rows);
    }

    @Override
    public Optional<AddressDto> findById(String id) {
        return JsonFiles.read(FILE, TYPE).stream().filter(row -> row.addressId().equals(id))
                .findFirst().map(AddressRowMapper::toDto);
    }

    @Override
    public List<AddressDto> findAll() {
        return JsonFiles.read(FILE, TYPE).stream().map(AddressRowMapper::toDto).toList();
    }

    @Override
    public List<AddressDto> findByCustomerId(String customerId) {
        return JsonFiles.read(FILE, TYPE).stream().filter(row -> row.customerId().equals(customerId))
                .map(AddressRowMapper::toDto).toList();
    }

    private void requireCustomer(String customerId) {
        if (customerId == null || !customerExists.test(customerId)) {
            throw new IllegalArgumentException("Adresa odkazuje na neexistujícího zákazníka " + customerId + ".");
        }
    }

    private static int indexOf(List<AddressRow> rows, String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).addressId().equals(id)) {
                return i;
            }
        }
        throw RecordIds.missing(ENTITY, id);
    }
}
