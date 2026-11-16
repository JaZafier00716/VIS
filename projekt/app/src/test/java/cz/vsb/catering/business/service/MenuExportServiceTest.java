package cz.vsb.catering.business.service;

import static cz.vsb.catering.business.fake.Fixtures.validRevision;
import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.DuplicateNameException;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeMenuMapper;
import cz.vsb.catering.business.fake.FakeProjectMapper;
import cz.vsb.catering.business.fake.FakeProjectNodeMapper;
import cz.vsb.catering.business.fake.Fixtures;
import cz.vsb.catering.business.port.MenuDocumentRenderer;
import cz.vsb.catering.business.pricing.PriceStrategy;
import cz.vsb.catering.data.file.InMemoryDocumentStorage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MenuExportServiceTest {
    private final FakeMenuMapper menus = new FakeMenuMapper();
    private final FakeProjectNodeMapper nodes = new FakeProjectNodeMapper();
    private final FakeProjectMapper projects = new FakeProjectMapper();
    private final InMemoryDocumentStorage storage = new InMemoryDocumentStorage();
    private final List<String> rendered = new ArrayList<>();
    private final MenuDocumentRenderer renderer = (revision, title, guests, strategy, target) -> {
        rendered.add(title + "|" + revision.getRevisionNo() + "|" + guests + "|" + strategy.label());
        Files.writeString(target, "%PDF-fake");
    };
    private final MenuExportService service = new MenuExportService(
            new MenuService(menus, projects, nodes, null, new DirectTransactionRunner(), Fixtures.CLOCK),
            new DocumentService(nodes, projects, storage, new DirectTransactionRunner()), renderer);
    private Folder root;
    private Menu menu;

    @BeforeEach void setUp() {
        root = new Folder("Svatba");
        root.setProjectId(1L);
        root.setProjectId(7L);
        nodes.insert(root);
        menu = new Menu("Svatební menu");
        menu.setMenuId(5L);
        root.add(menu);
        nodes.inserted.clear();
        menus.put(validRevision(5, 2, RevisionState.SHARED, true));
    }

    @Test void fileNameKeepsDiacriticsAndReplacesSlashes() {
        assertEquals("Svatební menu – revize 2.pdf", MenuExportService.fileName(new Menu("Svatební menu"), 2));
        assertEquals("Čaj_káva_ranní_ – revize 1.pdf", MenuExportService.fileName(new Menu("Čaj/káva\\ranní/"), 1)
                .replace("ranní_", "ranní_"));
    }

    @Test void exportStoresPdfDocumentInFolder() {
        Document d = service.exportPdf(menu, 2, root, PriceStrategy.WITH_VAT);
        assertEquals("Svatební menu – revize 2.pdf", d.getName());
        assertEquals("application/pdf", d.getContentType());
        assertEquals(List.of(d), nodes.inserted);
        assertTrue(root.getChildren().contains(d));
        assertEquals("%PDF-fake", new String(storage.content(d.getStorageKey())));
        assertEquals(List.of("Svatba Novákovi – Svatební menu|2|50|s DPH"), rendered);
    }

    @Test void duplicateNameStoresNothing() {
        service.exportPdf(menu, 2, root, PriceStrategy.WITHOUT_VAT);
        nodes.inserted.clear();
        DuplicateNameException e = assertThrows(DuplicateNameException.class,
                () -> service.exportPdf(menu, 2, root, PriceStrategy.WITHOUT_VAT));
        assertTrue(nodes.inserted.isEmpty());
        assertEquals(1, storage.size());
        Document renamed = service.exportPdf(menu, 2, root, PriceStrategy.WITHOUT_VAT, e.getSuggestedName());
        assertEquals(e.getSuggestedName(), renamed.getName());
    }
}
