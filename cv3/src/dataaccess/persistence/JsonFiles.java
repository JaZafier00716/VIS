package dataaccess.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import dataaccess.SchemaVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes the JSON "tables" (one file per table) in the data directory ({@code vis.data.dir},
 * default {@code data}).
 *
 * While a transaction is open, the original content of every file is saved before its first write, so
 * {@link #rollback()} can put all files back. This is application-level recovery, not crash-safe.
 */
public final class JsonFiles {
    static final String VERSION_FILE = "schemaVersion.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    /** Original file contents (empty = the file did not exist); null when no transaction is open. */
    private static Map<Path, Optional<byte[]>> journal;

    private JsonFiles() {}

    public static Path dataDirectory() {
        return Path.of(System.getProperty("vis.data.dir", "data"));
    }

    public static synchronized <T> List<T> read(String fileName, TypeReference<List<T>> type) {
        checkSchemaVersion(dataDirectory(), false);
        Path file = dataDirectory().resolve(fileName);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            List<T> rows = MAPPER.readValue(file.toFile(), type);
            return rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        } catch (IOException e) {
            throw new UncheckedIOException("Soubor " + file + " nelze přečíst.", e);
        }
    }

    public static synchronized <T> void write(String fileName, List<T> rows) {
        checkSchemaVersion(dataDirectory(), true);
        Path file = dataDirectory().resolve(fileName);
        try {
            Files.createDirectories(file.getParent() == null ? Path.of(".") : file.getParent());
            if (journal != null && !journal.containsKey(file)) {
                journal.put(file, Files.exists(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty());
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            MAPPER.writeValue(temp.toFile(), rows);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Soubor " + file + " nelze zapsat.", e);
        }
    }

    /**
     * The JSON counterpart of H2's {@code schema_version} table: a marker file {@value #VERSION_FILE} with
     * {@code {"schemaVersion": n}}. Table files without the marker, or with another version, are old data.
     * An empty directory gets the marker on the first write.
     */
    private static void checkSchemaVersion(Path directory, boolean writing) {
        Path marker = directory.resolve(VERSION_FILE);
        try {
            if (Files.exists(marker)) {
                int stored = MAPPER.readTree(marker.toFile()).path("schemaVersion").asInt(0);
                if (stored != SchemaVersion.CURRENT) {
                    throw SchemaVersion.outdated("JSON verze " + stored + ", očekávána " + SchemaVersion.CURRENT);
                }
                return;
            }
            if (Files.isDirectory(directory)) {
                try (DirectoryStream<Path> tables = Files.newDirectoryStream(directory, "*.json")) {
                    if (tables.iterator().hasNext()) {
                        throw SchemaVersion.outdated("JSON bez " + VERSION_FILE);
                    }
                }
            }
            if (writing) {
                Files.createDirectories(directory);
                MAPPER.writeValue(marker.toFile(), Map.of("schemaVersion", SchemaVersion.CURRENT));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Verzi dat v " + directory + " nelze ověřit.", e);
        }
    }

    public static synchronized void begin() {
        if (journal != null) {
            throw new IllegalStateException("JSON transakce je už otevřená.");
        }
        journal = new LinkedHashMap<>();
    }

    public static synchronized void commit() {
        journal = null;
    }

    public static synchronized void rollback() {
        if (journal == null) {
            return;
        }
        Map<Path, Optional<byte[]>> originals = journal;
        journal = null;
        IOException failure = null;
        for (Map.Entry<Path, Optional<byte[]>> entry : originals.entrySet()) {
            try {
                if (entry.getValue().isPresent()) {
                    Files.write(entry.getKey(), entry.getValue().get());
                } else {
                    Files.deleteIfExists(entry.getKey());
                }
            } catch (IOException e) {
                failure = e;
            }
        }
        if (failure != null) {
            throw new UncheckedIOException("Obnova JSON souborů po chybě selhala.", failure);
        }
    }

    public static synchronized boolean inTransaction() {
        return journal != null;
    }
}
