package dataaccess;

import configuration.ApplicationContext;
import configuration.CustomerMapping;
import configuration.StorageType;
import dataaccess.dto.CustomerDto;
import dataaccess.h2.H2CustomerGateway;
import dataaccess.h2.H2Database;
import dataaccess.h2.H2PersistenceException;
import dataaccess.dto.PersonCustomerDto;
import dataaccess.mapper.customer.PersonCustomerMapper;
import dataaccess.persistence.JsonCustomerGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Storage rules for the CV07 customer record: optional billing address and the data layout version. */
class CustomerStorageRulesTest {
    @TempDir
    Path dir;

    /** The common record of a person whose billing address has a street and a city but no zip code and country. */
    private static CustomerDto partlyFilledBilling() {
        return new CustomerDto("C-1", CustomerDto.PERSON, "jana@example.com", null, "Hlavní 1", "Ostrava", null, null);
    }

    @Test
    void loadRejectsAPartlyFilledBillingAddress() {
        PersonCustomerDto part = new PersonCustomerDto("C-1", "Jana", "Nováková", LocalDate.of(1990, 5, 14));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> PersonCustomerMapper.INSTANCE.toDomain(partlyFilledBilling(), part));
        assertTrue(e.getMessage().contains("částečně"));
    }

    @Test
    void h2CheckConstraintRejectsAPartlyFilledBillingAddress() {
        H2Database database = new H2Database(dir.resolve("vis-h2"));
        H2PersistenceException e = assertThrows(H2PersistenceException.class,
                () -> new H2CustomerGateway(database).insert(partlyFilledBilling()));
        assertTrue(e.isConstraintViolation());
    }

    @Test
    void jsonGatewayRejectsAPartlyFilledBillingAddress() {
        try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir)) {
            assertThrows(IllegalArgumentException.class,
                    () -> new JsonCustomerGateway().insert(partlyFilledBilling()));
            assertTrue(new JsonCustomerGateway().findAll().isEmpty());
        }
    }

    @Test
    void version2H2FileIsRefused() throws Exception {
        Path file = dir.resolve("vis-h2-cti");
        try (Connection connection = DriverManager.getConnection("jdbc:h2:file:" + file.toAbsolutePath(), "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE schema_version (version INT NOT NULL)");
            statement.execute("INSERT INTO schema_version VALUES (2)");
        }

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new H2CustomerGateway(new H2Database(file)).findAll());
        assertTrue(e.getMessage().startsWith(SchemaVersion.OUTDATED_MESSAGE), e.getMessage());
    }

    @Test
    void oldSingleStoreLayoutIsRefusedByTheApplication() throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("customers.json"), "[]");
        for (StorageType type : StorageType.values()) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> ApplicationContext.create(type, CustomerMapping.CTI, dir));
            assertTrue(e.getMessage().startsWith(SchemaVersion.OUTDATED_MESSAGE), e.getMessage());
        }
    }

    @Test
    void oldH2FileIsRefusedWithAClearMessage() throws Exception {
        Path file = dir.resolve("vis-h2");
        try (Connection connection = DriverManager.getConnection("jdbc:h2:file:" + file.toAbsolutePath(), "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE customers (customer_id VARCHAR(64) PRIMARY KEY, name VARCHAR(200), "
                    + "email VARCHAR(200))");
        }

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new H2CustomerGateway(new H2Database(file)).findAll());
        assertTrue(e.getMessage().startsWith(SchemaVersion.OUTDATED_MESSAGE), e.getMessage());
    }

    @Test
    void newH2FileRecordsTheCurrentVersion() {
        H2Database database = new H2Database(dir.resolve("vis-h2"));
        int version = database.run("verze", connection -> {
            try (Statement statement = connection.createStatement();
                 var rs = statement.executeQuery("SELECT version FROM schema_version")) {
                rs.next();
                return rs.getInt(1);
            }
        });
        assertEquals(SchemaVersion.CURRENT, version);
    }

    @Test
    void oldJsonFilesWithoutTheMarkerAreRefused() throws Exception {
        Files.writeString(dir.resolve("customers.json"),
                "[{\"customerId\":\"C-1\",\"name\":\"Jana\",\"email\":\"jana@example.com\"}]");
        try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir)) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new JsonCustomerGateway().findAll());
            assertTrue(e.getMessage().startsWith(SchemaVersion.OUTDATED_MESSAGE), e.getMessage());
        }
    }

    @Test
    void jsonMarkerWithAnotherVersionIsRefused() throws Exception {
        Files.writeString(dir.resolve("schemaVersion.json"), "{\"schemaVersion\":2}");
        try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir)) {
            assertThrows(IllegalStateException.class, () -> new JsonCustomerGateway().findAll());
        }
    }

    @Test
    void firstJsonWriteCreatesTheMarker() throws Exception {
        try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir)) {
            new JsonCustomerGateway().insert(new CustomerDto("C-1", CustomerDto.COMPANY, "info@firma.cz", null,
                    null, null, null, null));
        }
        assertEquals("{\"schemaVersion\":" + SchemaVersion.CURRENT + "}",
                Files.readString(dir.resolve("schemaVersion.json")).replaceAll("\\s", ""));
    }
}
