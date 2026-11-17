package dataaccess.mapper.customer;

import business.domainmodel.CompanyCustomer;
import business.domainmodel.PersonCustomer;
import configuration.StorageType;
import dataaccess.dto.CompanyCustomerDto;
import dataaccess.dto.CustomerDto;
import dataaccess.dto.PersonCustomerDto;
import dataaccess.dto.StiCustomerDto;
import dataaccess.gateway.CompanyCustomerGateway;
import dataaccess.gateway.CustomerGateway;
import dataaccess.gateway.Gateways;
import dataaccess.gateway.PersonCustomerGateway;
import dataaccess.gateway.StiCustomerGateway;
import dataaccess.h2.H2CompanyCustomerGateway;
import dataaccess.h2.H2CustomerGateway;
import dataaccess.h2.H2Database;
import dataaccess.h2.H2PersistenceException;
import dataaccess.h2.H2PersonCustomerGateway;
import dataaccess.h2.H2Schema;
import dataaccess.h2.H2StiCustomerGateway;
import dataaccess.h2.H2Storage;
import dataaccess.identity.IdentityMap;
import dataaccess.persistence.JsonCompanyCustomerGateway;
import dataaccess.persistence.JsonCustomerGateway;
import dataaccess.persistence.JsonPersonCustomerGateway;
import dataaccess.persistence.JsonStiCustomerGateway;
import dataaccess.persistence.JsonStorage;
import dataaccess.unitofwork.GatewayEntityPersister;
import dataaccess.unitofwork.StorageTransaction;
import dataaccess.unitofwork.UnitOfWork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules each inheritance strategy must enforce itself (cv07 p.5–8). */
class InheritanceStrategyRulesTest {
    @TempDir
    Path dir;
    private IsolatedJsonStorage json;

    @BeforeEach
    void setUp() {
        json = new IsolatedJsonStorage(dir);
    }

    @AfterEach
    void tearDown() {
        json.close();
    }

    /** The three CTI table gateways of one storage. */
    private record CtiTables(CustomerGateway customers, PersonCustomerGateway persons,
                             CompanyCustomerGateway companies, StorageTransaction transaction, Gateways all) {}

    private CtiTables cti(StorageType type) {
        if (type == StorageType.H2) {
            H2Database database = new H2Database(dir.resolve("vis-h2-cti"), H2Schema.cti());
            return new CtiTables(new H2CustomerGateway(database), new H2PersonCustomerGateway(database),
                    new H2CompanyCustomerGateway(database), H2Storage.transaction(database),
                    H2Storage.gateways(database));
        }
        return new CtiTables(new JsonCustomerGateway(), new JsonPersonCustomerGateway(),
                new JsonCompanyCustomerGateway(), JsonStorage.transaction(), JsonStorage.gateways());
    }

    private StiCustomerGateway sti(StorageType type) {
        return type == StorageType.H2
                ? new H2StiCustomerGateway(new H2Database(dir.resolve("vis-h2-sti"), H2Schema.sti()))
                : new JsonStiCustomerGateway();
    }

    /** cv07 p.8 step 4: both writes in one transaction; a failing subtype insert leaves no orphan in customers. */
    @ParameterizedTest
    @EnumSource(StorageType.class)
    void ctiRollsBackTheCommonRowWhenTheSubtypeInsertFails(StorageType type) {
        CtiTables tables = cti(type);
        PersonCustomerGateway failingPersons = new PersonCustomerGateway() {
            @Override public void insert(PersonCustomerDto r) { throw new IllegalStateException("Simulovaná chyba"); }
            @Override public void update(PersonCustomerDto r) { tables.persons().update(r); }
            @Override public void delete(String id) { tables.persons().delete(id); }
            @Override public Optional<PersonCustomerDto> findById(String id) { return tables.persons().findById(id); }
        };
        CtiCustomerTables layout = new CtiCustomerTables(tables.customers(), failingPersons, tables.companies());
        Gateways all = tables.all();
        UnitOfWork unitOfWork = new UnitOfWork(new IdentityMap(), new GatewayEntityPersister(new Gateways(layout,
                all.addresses(), all.materials(), all.finishes(), all.materialFinishes(), all.orders())),
                tables.transaction());
        unitOfWork.registerNew(TestCustomers.person("C-1", "Jana", "jana@example.com"), "C-1");

        assertThrows(IllegalStateException.class, unitOfWork::commit);

        assertTrue(tables.customers().findAll().isEmpty(), "no orphan row in customers");
    }

    /** cv07 p.8 "Důležité": the FK alone does not guarantee exactly one subtype row; loading does. */
    @ParameterizedTest
    @EnumSource(StorageType.class)
    void ctiRefusesACustomerWithoutOrWithTwoSubtypeRows(StorageType type) {
        CtiTables tables = cti(type);
        CtiCustomerTables layout = new CtiCustomerTables(tables.customers(), tables.persons(), tables.companies());
        tables.customers().insert(new CustomerDto("C-0", CustomerDto.PERSON, "x@example.com", null, null, null,
                null, null));

        IllegalStateException none = assertThrows(IllegalStateException.class, () -> layout.findById("C-0"));
        assertTrue(none.getMessage().contains("žádný"), none.getMessage());

        tables.persons().insert(new PersonCustomerDto("C-0", "Jana", "Nováková", LocalDate.of(1990, 5, 14)));
        tables.companies().insert(new CompanyCustomerDto("C-0", "Firma", "12345679", null, null));
        IllegalStateException two = assertThrows(IllegalStateException.class, () -> layout.findById("C-0"));
        assertTrue(two.getMessage().contains("dva"), two.getMessage());
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void ctiRefusesASubtypeRowThatDoesNotMatchTheDiscriminator(StorageType type) {
        CtiTables tables = cti(type);
        CtiCustomerTables layout = new CtiCustomerTables(tables.customers(), tables.persons(), tables.companies());
        tables.customers().insert(new CustomerDto("C-0", CustomerDto.COMPANY, "x@example.com", null, null, null,
                null, null));
        tables.persons().insert(new PersonCustomerDto("C-0", "Jana", "Nováková", LocalDate.of(1990, 5, 14)));

        assertThrows(IllegalStateException.class, () -> layout.findById("C-0"));
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void ctiInsertRefusesAnIdThatAlreadyHasTheOtherSubtypeRow(StorageType type) {
        CtiTables tables = cti(type);
        CtiCustomerTables layout = new CtiCustomerTables(tables.customers(), tables.persons(), tables.companies());
        tables.customers().insert(new CustomerDto("C-2", CustomerDto.COMPANY, "x@example.com", null, null, null,
                null, null));
        tables.companies().insert(new CompanyCustomerDto("C-2", "Firma", "12345679", null, null));

        assertThrows(IllegalStateException.class,
                () -> layout.insert(TestCustomers.person("C-2", "Jana", "jana@example.com")));
    }

    @Test
    void ctiSubtypeTablesNeedTheCommonRow() {
        CtiTables h2 = cti(StorageType.H2);
        H2PersistenceException e = assertThrows(H2PersistenceException.class, () -> h2.persons()
                .insert(new PersonCustomerDto("C-404", "Jana", "Nováková", LocalDate.of(1990, 5, 14))));
        assertTrue(e.isConstraintViolation(), "PK + FK to customers");
        assertThrows(IllegalArgumentException.class, () -> cti(StorageType.JSON).persons()
                .insert(new PersonCustomerDto("C-404", "Jana", "Nováková", LocalDate.of(1990, 5, 14))));
    }

    /** cv07 p.5: the CHECK constraint keeps the right columns filled for each customer_type. */
    @ParameterizedTest
    @EnumSource(StorageType.class)
    void stiCheckConstraintRejectsWrongColumnsForTheType(StorageType type) {
        StiCustomerGateway gateway = sti(type);
        StiCustomerDto personWithCompanyName = new StiCustomerDto("C-1", "P", "jana@example.com", null, null, null,
                null, null, "Jana", "Nováková", LocalDate.of(1990, 5, 14), "Firma", null, null, null);
        StiCustomerDto companyWithoutIco = new StiCustomerDto("C-2", "C", "info@firma.cz", null, null, null, null,
                null, null, null, null, "Firma", null, null, null);
        StiCustomerDto personWithoutBirthDate = new StiCustomerDto("C-3", "P", "jana@example.com", null, null, null,
                null, null, "Jana", "Nováková", null, null, null, null, null);
        StiCustomerDto unknownType = new StiCustomerDto("C-4", "X", "jana@example.com", null, null, null, null, null,
                "Jana", "Nováková", LocalDate.of(1990, 5, 14), null, null, null, null);

        for (StiCustomerDto row : new StiCustomerDto[]{personWithCompanyName, companyWithoutIco,
                personWithoutBirthDate, unknownType}) {
            RuntimeException e = assertThrows(RuntimeException.class, () -> gateway.insert(row), row.customerId());
            if (type == StorageType.H2) {
                assertTrue(((H2PersistenceException) e).isConstraintViolation(), row.customerId());
            }
        }
        assertTrue(gateway.findAll().isEmpty());

        StiCustomerTables layout = new StiCustomerTables(gateway);
        layout.insert(TestCustomers.person("C-5", "Jana", "jana@example.com"));
        layout.insert(new CompanyCustomer("C-6", "info@firma.cz", null, null, "Firma", "12345679", null, null));
        assertEquals(2, gateway.findAll().size());
    }

    /** cv07 p.6 step 5: the discriminator decides which class is built. */
    @ParameterizedTest
    @EnumSource(StorageType.class)
    void stiBuildsTheClassFromTheDiscriminator(StorageType type) {
        StiCustomerTables layout = new StiCustomerTables(sti(type));
        layout.insert(TestCustomers.person("C-5", "Jana", "jana@example.com"));
        layout.insert(new CompanyCustomer("C-6", "info@firma.cz", null, null, "Firma", "12345679", null, null));

        assertTrue(layout.findById("C-5").orElseThrow() instanceof PersonCustomer);
        assertTrue(layout.findById("C-6").orElseThrow() instanceof CompanyCustomer);
    }
}
