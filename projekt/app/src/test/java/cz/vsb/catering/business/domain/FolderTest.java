package cz.vsb.catering.business.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FolderTest {
    private Folder root() {
        Folder root = new Folder("Svatba");
        root.setId(1L);
        root.setProjectId(7L);
        return root;
    }

    @Test void addSetsParentAndProject() {
        Folder root = root();
        Folder sub = new Folder("Podklady");
        root.add(sub);
        assertSame(root, sub.getParent());
        assertEquals(7L, sub.getProjectId());
        assertEquals("Svatba / Podklady", sub.path());
        assertEquals(1, root.getSubfolders().size());
    }

    @Test void duplicateNameIsRejectedWithSuggestion() {
        Folder root = root();
        root.add(new Menu("Svatební menu"));
        DuplicateNameException e = assertThrows(DuplicateNameException.class, () -> root.add(new Folder("svatební menu ")));
        assertEquals("svatební menu (2)", e.getSuggestedName());
        assertEquals(1, root.getChildren().size());
        root.add(new Folder("Svatební menu (2)"));
        assertEquals("Svatební menu (3)", root.suggestFreeName("Svatební menu"));
    }

    @Test void suggestionKeepsFileExtension() {
        Folder root = root();
        root.add(new Document("file.pdf", "application/pdf", 10, null));
        DuplicateNameException e = assertThrows(DuplicateNameException.class,
                () -> root.add(new Document("file.pdf", "application/pdf", 10, null)));
        assertEquals("file (2).pdf", e.getSuggestedName());
    }

    @Test void findSearchesWholeSubtree() {
        Folder root = root();
        Folder sub = new Folder("A");
        sub.setId(2L);
        root.add(sub);
        Document doc = new Document("d.txt", "text/plain", 1, null);
        doc.setId(3L);
        sub.add(doc);
        assertSame(root, root.find(1).orElseThrow());
        assertSame(doc, root.find(3).orElseThrow());
        assertTrue(root.find(99).isEmpty());
    }

    @Test void deletableOnlyWhenEmptyAndCountsDocuments() {
        Folder root = root();
        assertTrue(root.isDeletable());
        Folder sub = new Folder("A");
        root.add(sub);
        sub.add(new Document("1.pdf", "application/pdf", 1, null));
        sub.add(new Document("2.pdf", "application/pdf", 1, null));
        root.add(new Document("3.pdf", "application/pdf", 1, null));
        root.add(new Menu("Menu"));
        assertFalse(root.isDeletable());
        assertEquals(3, root.countDocuments());
        assertEquals(2, sub.countDocuments());
    }

    @Test void removeDetachesChild() {
        Folder root = root();
        Folder sub = new Folder("A");
        root.add(sub);
        root.remove(sub);
        assertNull(sub.getParent());
        assertTrue(root.getChildren().isEmpty());
    }

    @Test void menuDeletableWithoutActiveLink() {
        Menu menu = new Menu("M");
        assertTrue(menu.isDeletable());
        menu.setActiveShareLink(true);
        assertFalse(menu.isDeletable());
        assertEquals(0, menu.countDocuments());
    }
}
