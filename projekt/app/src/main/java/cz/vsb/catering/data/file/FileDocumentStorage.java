package cz.vsb.catering.data.file;

import cz.vsb.catering.business.port.DocumentStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Gateway implementation: documents are plain files under the root folder (key = relative path). */
public class FileDocumentStorage implements DocumentStorage {
    private final Path root;

    public FileDocumentStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override public void store(String key, Path source) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override public void retrieve(String key, Path target) throws IOException {
        Files.copy(resolve(key), target);
    }

    @Override public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override public boolean exists(String key) {
        try {
            return Files.exists(resolve(key));
        } catch (IOException e) {
            return false;
        }
    }

    /** Rejects keys that would escape the root folder (e.g. "../x"). */
    private Path resolve(String key) throws IOException {
        Path path;
        try {
            path = root.resolve(key).normalize();
        } catch (InvalidPathException e) {
            throw new IOException("Neplatný klíč v úložišti dokumentů: " + key, e);
        }
        if (!path.startsWith(root)) throw new IllegalArgumentException("Neplatný klíč v úložišti dokumentů: " + key);
        return path;
    }
}
