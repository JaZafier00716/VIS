package dataaccess.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import dataaccess.dto.PersonCustomerDto;
import dataaccess.gateway.PersonCustomerGateway;
import dataaccess.row.PersonCustomerRow;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * CTI: Table Data Gateway used by the Data Mapper (JSON file person_customers.json). Same rules as the H2 table:
 * the ID is a primary key and must exist in customers.json (PK + FK), the names and birth date are NOT NULL.
 */
public class JsonPersonCustomerGateway implements PersonCustomerGateway {
    static final String FILE = "person_customers.json";
    private static final String ENTITY = "Fyzická osoba";
    private static final TypeReference<List<PersonCustomerRow>> TYPE = new TypeReference<>() {};

    @Override
    public void insert(PersonCustomerDto person) {
        String id = RecordIds.require(person.customerId(), ENTITY);
        check(person);
        List<PersonCustomerRow> rows = JsonFiles.read(FILE, TYPE);
        if (rows.stream().anyMatch(row -> row.customerId().equals(id))) {
            throw RecordIds.duplicate(ENTITY, id);
        }
        rows.add(toRow(person));
        JsonFiles.write(FILE, rows);
    }

    @Override
    public void update(PersonCustomerDto person) {
        String id = RecordIds.require(person.customerId(), ENTITY);
        check(person);
        List<PersonCustomerRow> rows = JsonFiles.read(FILE, TYPE);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).customerId().equals(id)) {
                rows.set(i, toRow(person));
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
    public Optional<PersonCustomerDto> findById(String id) {
        return JsonFiles.read(FILE, TYPE).stream().filter(row -> row.customerId().equals(id)).findFirst()
                .map(row -> new PersonCustomerDto(row.customerId(), row.firstName(), row.lastName(),
                        row.birthDate() == null ? null : LocalDate.parse(row.birthDate())));
    }

    /** Also used as the ON DELETE CASCADE from customers.json. */
    static void deleteRow(String id) {
        List<PersonCustomerRow> rows = JsonFiles.read(FILE, TYPE);
        if (rows.removeIf(row -> row.customerId().equals(id))) {
            JsonFiles.write(FILE, rows);
        }
    }

    private static void check(PersonCustomerDto person) {
        if (!JsonCustomerGateway.exists(person.customerId())) {
            throw new IllegalArgumentException("Osoba " + person.customerId() + " nemá záznam v customers.");
        }
        if (person.firstName() == null || person.lastName() == null || person.birthDate() == null) {
            throw new IllegalArgumentException("Jméno, příjmení a datum narození nesmí být prázdné.");
        }
    }

    private static PersonCustomerRow toRow(PersonCustomerDto dto) {
        return new PersonCustomerRow(dto.customerId(), dto.firstName(), dto.lastName(), dto.birthDate().toString());
    }
}
