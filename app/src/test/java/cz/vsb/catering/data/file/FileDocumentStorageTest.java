package cz.vsb.catering.data.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDocumentStorageTest {
    @TempDir Path tmp;

    @Test void storeRetrieveDelete() throws IOException {
        FileDocumentStorage storage = new FileDocumentStorage(tmp.resolve("docs"));
        Path source = Files.writeString(tmp.resolve("in.txt"), "obsah");
        storage.store("p12/smlouva.txt", source);
        assertTrue(storage.exists("p12/smlouva.txt"));
        assertTrue(Files.exists(tmp.resolve("docs/p12/smlouva.txt")));

        Path target = tmp.resolve("out.txt");
        storage.retrieve("p12/smlouva.txt", target);
        assertEquals("obsah", Files.readString(target));

        storage.delete("p12/smlouva.txt");
        assertFalse(storage.exists("p12/smlouva.txt"));
        assertDoesNotThrow(() -> storage.delete("p12/smlouva.txt"));
    }

    @Test void storeOverwrites() throws IOException {
        FileDocumentStorage storage = new FileDocumentStorage(tmp);
        storage.store("a.txt", Files.writeString(tmp.resolve("1"), "one"));
        storage.store("a.txt", Files.writeString(tmp.resolve("2"), "two"));
        assertEquals("two", Files.readString(tmp.resolve("a.txt")));
    }

    @Test void retrieveMissingFails() {
        FileDocumentStorage storage = new FileDocumentStorage(tmp);
        assertThrows(IOException.class, () -> storage.retrieve("none.txt", tmp.resolve("x")));
    }

    @Test void rejectsKeysOutsideRoot() {
        FileDocumentStorage storage = new FileDocumentStorage(tmp.resolve("docs"));
        assertThrows(IllegalArgumentException.class, () -> storage.exists("../x"));
        assertThrows(IllegalArgumentException.class, () -> storage.store("../x", tmp.resolve("in")));
        assertThrows(IllegalArgumentException.class, () -> storage.delete("p1/../../x"));
    }

    @Test void storeLeavesNoTmpFile() throws IOException {
        FileDocumentStorage storage = new FileDocumentStorage(tmp.resolve("docs"));
        storage.store("p1/a.txt", Files.writeString(tmp.resolve("in"), "x"));
        try (var files = Files.list(tmp.resolve("docs/p1"))) {
            assertEquals(java.util.List.of("a.txt"), files.map(f -> f.getFileName().toString()).toList());
        }
    }

    @Test void retrieveDoesNotOverwrite() throws IOException {
        FileDocumentStorage storage = new FileDocumentStorage(tmp.resolve("docs"));
        storage.store("a.txt", Files.writeString(tmp.resolve("in"), "new"));
        Path target = Files.writeString(tmp.resolve("out"), "old");
        assertThrows(IOException.class, () -> storage.retrieve("a.txt", target));
        assertEquals("old", Files.readString(target));
    }

    @Test void invalidPathKeyIsIoException() {
        FileDocumentStorage storage = new FileDocumentStorage(tmp);
        assertThrows(IOException.class, () -> storage.store("a\0b", tmp.resolve("in")));
    }
}
