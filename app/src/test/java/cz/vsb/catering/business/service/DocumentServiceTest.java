package cz.vsb.catering.business.service;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.DuplicateNameException;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.ProjectNode;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeProjectMapper;
import cz.vsb.catering.business.fake.FakeProjectNodeMapper;
import cz.vsb.catering.data.file.InMemoryDocumentStorage;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentServiceTest {
    @TempDir Path tmp;
    private final FakeProjectNodeMapper nodes = new FakeProjectNodeMapper();
    private final FakeProjectMapper projects = new FakeProjectMapper();
    private final InMemoryDocumentStorage storage = new InMemoryDocumentStorage();
    private final DocumentService service = new DocumentService(nodes, projects, storage, new DirectTransactionRunner());
    private Folder root;

    @BeforeEach void setUp() {
        root = new Folder("Svatba");
        root.setProjectId(1L);
        root.setProjectId(7L);
        nodes.insert(root);
        nodes.inserted.clear();
    }

    private Path file(String name, String content) throws IOException {
        return Files.writeString(tmp.resolve(name), content);
    }

    // ------------------------------------------------------------ folders

    @Test void createFolderInsertsIt() {
        Folder f = service.createFolder(root, "Podklady");
        assertEquals(List.of(f), nodes.inserted);
        assertNotNull(f.getId());
        assertEquals(7L, f.getProjectId());
        assertEquals(List.of(f), service.listContent(root));
    }

    @Test void duplicateFolderInsertsNothing() {
        service.createFolder(root, "Podklady");
        nodes.inserted.clear();
        DuplicateNameException e = assertThrows(DuplicateNameException.class, () -> service.createFolder(root, "Podklady"));
        assertEquals("Podklady (2)", e.getSuggestedName());
        assertTrue(nodes.inserted.isEmpty());
        assertEquals(1, root.getChildren().size());
    }

    @Test void failedFolderInsertRemovesNodeFromParent() {
        nodes.failOnInsert = new IllegalStateException("db down");
        assertThrows(IllegalStateException.class, () -> service.createFolder(root, "Podklady"));
        assertTrue(root.getChildren().isEmpty());
    }

    // ------------------------------------------------------------ documents

    @Test void createDocumentStoresFileAndMetadata() throws IOException {
        Document d = service.createDocument(root, file("smlouva.txt", "obsah smlouvy"), "smlouva.txt");
        assertEquals(List.of(d), nodes.inserted);
        assertEquals(13, d.getSizeBytes());
        assertNotNull(d.getContentType());
        assertTrue(d.getStorageKey().startsWith("p7/"), d.getStorageKey());
        assertTrue(d.getStorageKey().endsWith("-smlouva.txt"));
        assertEquals("obsah smlouvy", new String(storage.content(d.getStorageKey())));
        assertEquals(List.of(d), root.getChildren());
    }

    @Test void storageKeyIsAscii() throws IOException {
        Document d = service.createDocument(root, file("a.txt", "x"), "Příloha č. 1.pdf");
        assertTrue(d.getStorageKey().matches("p7/[0-9a-f-]{36}-Priloha_c._1.pdf"), d.getStorageKey());
    }

    @Test void slashInDocumentNameIsRejectedBeforeStoring() throws IOException {
        assertThrows(ValidationException.class, () -> service.createDocument(root, file("a.txt", "x"), "../a.txt"));
        assertEquals(0, storage.size());
    }

    @Test void badOldNameWritesNothingOutsideFolder() throws IOException {
        Document bad = new Document("../x.pdf", "application/pdf", 1, "p7/key");     // old row, bypasses NodeCreator
        storage.store("p7/key", file("src.pdf", "X"));
        Path target = Files.createDirectory(tmp.resolve("download"));
        assertThrows(ValidationException.class, () -> service.downloadDocuments(List.of(bad), target));
        assertFalse(Files.exists(tmp.resolve("x.pdf")));
        try (var list = Files.list(target)) { assertEquals(0, list.count()); }
    }

    @Test void sameNamesAreBothSavedAndExistingFileIsKept() throws IOException {
        Folder other = new Folder("Jiná");
        root.add(other);
        Document a = service.createDocument(root, file("1.txt", "AAA"), "x.pdf");
        Document b = service.createDocument(other, file("2.txt", "BBB"), "x.pdf");
        Path target = Files.createDirectory(tmp.resolve("download"));
        Files.writeString(target.resolve("x.pdf"), "STARÝ");
        assertEquals(2, service.downloadDocuments(List.of(a, b), target));
        assertEquals("STARÝ", Files.readString(target.resolve("x.pdf")));
        assertEquals("AAA", Files.readString(target.resolve("x (2).pdf")));
        assertEquals("BBB", Files.readString(target.resolve("x (3).pdf")));
    }

    @Test void failedMetadataInsertDeletesStoredFile() throws IOException {
        nodes.failOnInsert = new IllegalStateException("db down");
        assertThrows(IllegalStateException.class, () -> service.createDocument(root, file("a.txt", "x"), "a.txt"));
        assertEquals(0, storage.size());
        assertTrue(root.getChildren().isEmpty());
    }

    @Test void failedStoreRemovesNodeAndInsertsNothing() throws IOException {
        InMemoryDocumentStorage broken = new InMemoryDocumentStorage() {
            @Override public void store(String key, Path source) throws IOException { throw new IOException("disk full"); }
        };
        DocumentService s = new DocumentService(nodes, projects, broken, new DirectTransactionRunner());
        assertThrows(UncheckedIOException.class, () -> s.createDocument(root, file("a.txt", "x"), "a.txt"));
        assertTrue(root.getChildren().isEmpty());
        assertTrue(nodes.inserted.isEmpty());
    }

    @Test void duplicateDocumentNameStoresNothing() throws IOException {
        service.createDocument(root, file("a.pdf", "x"), "a.pdf");
        nodes.inserted.clear();
        DuplicateNameException e = assertThrows(DuplicateNameException.class,
                () -> service.createDocument(root, file("b.pdf", "y"), "a.pdf"));
        assertEquals("a (2).pdf", e.getSuggestedName());
        assertEquals(1, storage.size());
        assertTrue(nodes.inserted.isEmpty());
    }

    @Test void tooLargeDocumentIsRejected() throws IOException {
        Path big = tmp.resolve("big.bin");
        try (RandomAccessFile f = new RandomAccessFile(big.toFile(), "rw")) {
            f.setLength(Document.MAX_SIZE_BYTES + 1);          // sparse file, no real 20 MB written
        }
        ValidationException e = assertThrows(ValidationException.class, () -> service.createDocument(root, big, "big.bin"));
        assertTrue(e.getFieldErrors().containsKey("file"));
        assertEquals(0, storage.size());
        assertTrue(root.getChildren().isEmpty());
    }

    @Test void unreadableFileIsValidationError() {
        assertThrows(ValidationException.class, () -> service.createDocument(root, tmp.resolve("missing.txt"), "x.txt"));
    }

    @Test void downloadDocumentsCopiesContent() throws IOException {
        Document a = service.createDocument(root, file("a.txt", "AAA"), "a.txt");
        Document b = service.createDocument(root, file("b.txt", "BB"), "b.txt");
        Path target = Files.createDirectory(tmp.resolve("download"));
        assertEquals(2, service.downloadDocuments(List.of(a, b), target));
        assertEquals("AAA", Files.readString(target.resolve("a.txt")));
        assertEquals("BB", Files.readString(target.resolve("b.txt")));
    }

    @Test void downloadOfMissingContentFails() throws IOException {
        Document a = service.createDocument(root, file("a.txt", "AAA"), "a.txt");
        storage.delete(a.getStorageKey());
        assertThrows(UncheckedIOException.class, () -> service.downloadDocuments(List.of(a), tmp));
    }

    // ------------------------------------------------------------ UC19 delete

    @Test void nonEmptyFolderBlocksWholeDeletion() throws IOException {
        Folder sub = service.createFolder(root, "Sub");
        service.createDocument(sub, file("in.txt", "x"), "in.txt");
        Document doc = service.createDocument(root, file("a.txt", "x"), "a.txt");
        ValidationException e = assertThrows(ValidationException.class, () -> service.deleteNodes(List.of(doc, sub)));
        assertTrue(e.getMessage().startsWith("Nic nebylo smazáno."));
        assertTrue(e.getMessage().contains("„Sub“ není prázdná"));
        assertTrue(nodes.deleted.isEmpty());
        assertEquals(2, storage.size());
        assertTrue(storage.exists(doc.getStorageKey()));
        assertEquals(2, root.getChildren().size());
    }

    @Test void menuWithActiveLinkBlocksWholeDeletion() throws IOException {
        Document doc = service.createDocument(root, file("a.txt", "x"), "a.txt");
        Menu menu = new Menu("Menu");
        root.add(menu);
        nodes.insert(menu);
        menu.setActiveShareLink(true);
        ValidationException e = assertThrows(ValidationException.class, () -> service.deleteNodes(List.of(doc, menu)));
        assertTrue(e.getMessage().contains("aktivní sdílený odkaz"));
        assertTrue(nodes.deleted.isEmpty());
        assertTrue(storage.exists(doc.getStorageKey()));
    }

    @Test void rootFolderCannotBeDeleted() {
        assertThrows(ValidationException.class, () -> service.deleteNodes(List.of(root)));
        assertTrue(nodes.deleted.isEmpty());
    }

    @Test void deletableNodesAreAllDeleted() throws IOException {
        Document doc = service.createDocument(root, file("a.txt", "x"), "a.txt");
        Folder empty = service.createFolder(root, "Prázdná");
        Menu menu = new Menu("Menu");
        root.add(menu);
        nodes.insert(menu);
        List<ProjectNode> selected = List.of(doc, empty, menu);
        service.deleteNodes(selected);
        assertEquals(selected, nodes.deleted);
        assertFalse(storage.exists(doc.getStorageKey()));
        assertTrue(root.getChildren().isEmpty());
        assertNull(doc.getParent());
    }

    @Test void emptySelectionDoesNothing() {
        service.deleteNodes(List.of());
        assertTrue(nodes.deleted.isEmpty());
    }
}
