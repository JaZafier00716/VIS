package configuration;

import java.nio.file.Path;

/** H2 with Class Table Inheritance. */
class H2StorageContractTest extends StorageContractTest {
    @Override
    protected ApplicationContext newContext(Path dataDirectory) {
        return ApplicationContext.create(StorageType.H2, CustomerMapping.CTI, dataDirectory);
    }
}
