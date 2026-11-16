package cz.vsb.catering.business.service;

import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.port.MenuDocumentRenderer;
import cz.vsb.catering.business.pricing.PriceStrategy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Facade for UC14 Exportovat menu do PDF: renders a revision and stores the PDF as a project document
 *  through DocumentService, so name uniqueness (DuplicateNameException), storage and rollback are shared
 *  with uploads (UC02 7.4-7.6). */
public class MenuExportService {
    public static final String PDF = "application/pdf";

    private final MenuService menus;
    private final DocumentService documents;
    private final MenuDocumentRenderer renderer;

    public MenuExportService(MenuService menus, DocumentService documents, MenuDocumentRenderer renderer) {
        this.menus = menus;
        this.documents = documents;
        this.renderer = renderer;
    }

    /** Default document name, e.g. "Svatební menu – revize 2.pdf". */
    public static String fileName(Menu menu, int revisionNo) {
        return menu.getName().replaceAll("[/\\\\]", "_") + " – revize " + revisionNo + ".pdf";
    }

    public Document exportPdf(Menu menu, int revisionNo, Folder target, PriceStrategy strategy) {
        return exportPdf(menu, revisionNo, target, strategy, fileName(menu, revisionNo));
    }

    /** name lets the UI retry with DuplicateNameException.getSuggestedName(). */
    public Document exportPdf(Menu menu, int revisionNo, Folder target, PriceStrategy strategy, String name) {
        long menuId = menu.getMenuId();
        MenuRevision revision = menus.openRevision(menuId, revisionNo);
        Path tmp = null;
        try {
            tmp = Files.createTempFile("menu-", ".pdf");
            renderer.render(revision, menus.title(menuId), menus.guestCount(menuId), strategy, tmp);
            return documents.createDocument(target, tmp, name, PDF);
        } catch (IOException e) {
            throw new UncheckedIOException("PDF se nepodařilo vytvořit.", e);
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
        }
    }
}
