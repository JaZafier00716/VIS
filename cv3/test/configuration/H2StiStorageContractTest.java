package configuration;

import java.nio.file.Path;

/** H2 with Single Table Inheritance. */
class H2StiStorageContractTest extends StorageContractTest {
    @Override
    protected ApplicationContext newContext(Path dataDirectory) {
        return ApplicationContext.create(StorageType.H2, CustomerMapping.STI, dataDirectory);
    }
}
