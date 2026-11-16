package cz.vsb.catering.business.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NodeCreatorTest {
    private final Folder parent = new Folder("Root");

    @Test void eachCreatorReturnsItsType() {
        assertInstanceOf(Folder.class, new NodeCreator.FolderCreator().createIn(parent, "F"));
        assertInstanceOf(Menu.class, new NodeCreator.MenuCreator().createIn(parent, "M"));
        ProjectNode doc = new NodeCreator.DocumentCreator("application/pdf", 123).createIn(parent, "D.pdf");
        assertInstanceOf(Document.class, doc);
        assertEquals(123, ((Document) doc).getSizeBytes());
        assertEquals("application/pdf", ((Document) doc).getContentType());
        assertNull(((Document) doc).getStorageKey());
        assertEquals(3, parent.getChildren().size());
        assertEquals(NodeType.DOCUMENT, doc.type());
    }

    @Test void trimsName() {
        assertEquals("Menu", new NodeCreator.MenuCreator().createIn(parent, "  Menu ").getName());
    }

    @Test void rejectsEmptyAndLongNames() {
        NodeCreator c = new NodeCreator.FolderCreator();
        for (String bad : new String[] {null, "", "   ", "x".repeat(101)}) {
            ValidationException e = assertThrows(ValidationException.class, () -> c.createIn(parent, bad));
            assertTrue(e.getFieldErrors().containsKey("name"));
        }
        assertTrue(parent.getChildren().isEmpty());
        c.createIn(parent, "x".repeat(100));
    }

    @Test void rejectsPathLikeAndControlNames() {
        NodeCreator c = new NodeCreator.FolderCreator();
        for (String bad : new String[] {"a/b", "a\\b", "..", ".", " .. ", "a\tb", "a\nb", "a\u0001b", "../x.pdf"}) {
            ValidationException e = assertThrows(ValidationException.class, () -> c.createIn(parent, bad), bad);
            assertTrue(e.getFieldErrors().containsKey("name"), bad);
        }
        assertTrue(parent.getChildren().isEmpty());
        c.createIn(parent, "..a");                                   // dots are fine inside a name
    }

    @Test void duplicateNameFromCreator() {
        new NodeCreator.FolderCreator().createIn(parent, "A");
        assertThrows(DuplicateNameException.class, () -> new NodeCreator.MenuCreator().createIn(parent, "a"));
    }

    @Test void documentCreatorRejectsOver20Mb() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> new NodeCreator.DocumentCreator("application/pdf", Document.MAX_SIZE_BYTES + 1));
        assertTrue(e.getFieldErrors().containsKey("file"));
        assertDoesNotThrow(() -> new NodeCreator.DocumentCreator("application/pdf", Document.MAX_SIZE_BYTES));
    }

    @Test void documentFormattedSize() {
        assertEquals("500 B", new Document("a", "x", 500, null).formattedSize());
        assertEquals(String.format("%.1f MB", 1.5), new Document("a", "x", 1024 * 1024 * 3 / 2, null).formattedSize());
    }
}
