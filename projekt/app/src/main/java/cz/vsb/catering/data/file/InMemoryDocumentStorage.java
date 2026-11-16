package cz.vsb.catering.data.file;

import cz.vsb.catering.business.port.DocumentStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Service Stub (Fowler): keeps document contents in memory instead of the real document folder,
 *  so the business layer can be tested without touching the disk storage. */
public class InMemoryDocumentStorage implements DocumentStorage {
    private final Map<String, byte[]> files = new HashMap<>();

    @Override public void store(String key, Path source) throws IOException {
        files.put(key, Files.readAllBytes(source));
    }

    @Override public void retrieve(String key, Path target) throws IOException {
        byte[] content = files.get(key);
        if (content == null) throw new NoSuchFileException(key);
        Files.write(target, content);
    }

    @Override public void delete(String key) { files.remove(key); }

    @Override public boolean exists(String key) { return files.containsKey(key); }

    /** Content stored under the key, or null (for test assertions). */
    public byte[] content(String key) { return files.get(key); }

    public int size() { return files.size(); }
}
