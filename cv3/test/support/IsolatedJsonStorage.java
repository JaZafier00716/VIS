package support;

import configuration.ApplicationContext;
import configuration.StorageType;

import java.nio.file.Path;

/**
 * Points the JSON storage at a test's temporary directory and restores the previous data directory
 * afterwards, so tests never touch the real {@code data} folder.
 */
public final class IsolatedJsonStorage implements AutoCloseable {
    private final String previous;
    private final Path directory;

    public IsolatedJsonStorage(Path directory) {
        this.previous = System.getProperty("vis.data.dir");
        this.directory = directory;
        System.setProperty("vis.data.dir", directory.toString());
    }

    /** A fresh application (new Unit of Work and Identity Map) over the same files. */
    public ApplicationContext newContext() {
        return ApplicationContext.create(StorageType.JSON, directory);
    }

    public Path directory() {
        return directory;
    }

    @Override
    public void close() {
        if (previous == null) {
            System.clearProperty("vis.data.dir");
        } else {
            System.setProperty("vis.data.dir", previous);
        }
    }
}
