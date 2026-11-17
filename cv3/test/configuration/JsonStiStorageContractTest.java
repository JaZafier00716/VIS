package configuration;

import org.junit.jupiter.api.AfterEach;

import java.nio.file.Path;

/** JSON with Single Table Inheritance. */
class JsonStiStorageContractTest extends StorageContractTest {
    private final String previous = System.getProperty("vis.data.dir");

    @Override
    protected ApplicationContext newContext(Path dataDirectory) {
        return ApplicationContext.create(StorageType.JSON, CustomerMapping.STI, dataDirectory);
    }

    @AfterEach
    void restoreDataDirectory() {
        if (previous == null) System.clearProperty("vis.data.dir");
        else System.setProperty("vis.data.dir", previous);
    }
}
