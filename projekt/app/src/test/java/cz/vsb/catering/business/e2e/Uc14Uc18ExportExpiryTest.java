package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.DuplicateNameException;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.pricing.PriceStrategy;
import cz.vsb.catering.business.service.MenuExportService;
import cz.vsb.catering.data.file.PdfMenuRenderer;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** UC14 Exportovat menu do PDF and UC18 Zneplatnit prošlé odkazy against Oracle. */
class Uc14Uc18ExportExpiryTest extends E2eBaseTest {
    private static final Path SCREENSHOTS = Path.of("screenshots");

    @TempDir Path tmp;

    private MenuExportService export() { return new MenuExportService(menus, documents, new PdfMenuRenderer()); }

    @Test
    void uc14ExportStoresPdfAsProjectDocument() throws Exception {
        Project project = newProject("E2E UC14 Export – Žluťoučký kůň");
        Menu menu = savedMenu(project, "Svatební menu");
        long filesBefore = storedFiles();

        Document doc = export().exportPdf(menu, 1, project.getRootFolder(), PriceStrategy.WITH_VAT);

        Map<String, Object> node = row("SELECT * FROM project_nodes WHERE node_id = ?", doc.getId());
        assertEquals("DOCUMENT", node.get("NODE_TYPE"));
        assertEquals("Svatební menu – revize 1.pdf", node.get("NAME"));
        assertEquals("application/pdf", node.get("CONTENT_TYPE"));
        assertEquals(project.getRootFolder().getId(), node.get("PARENT_ID"));
        assertEquals(filesBefore + 1, storedFiles());
        Path file = storageDir.resolve(doc.getStorageKey());
        assertEquals(Files.size(file), node.get("SIZE_BYTES"));
        try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("E2E UC14 Export – Žluťoučký kůň – Svatební menu"), text);
            assertTrue(text.contains("Předkrmy"), text);
            MenuRevision revision = menus.openRevision(menu.getMenuId(), 1);
            assertTrue(text.contains(revision.totalPrice(PriceStrategy.WITH_VAT).format()), text);
        }

        // same name again: nothing is stored (UC02 8.1 like an upload)
        assertThrows(DuplicateNameException.class,
                () -> export().exportPdf(menu, 1, project.getRootFolder(), PriceStrategy.WITH_VAT));
        assertEquals(filesBefore + 1, storedFiles());
        assertEquals(1, count("SELECT COUNT(*) FROM project_nodes WHERE project_id = ? AND node_type = 'DOCUMENT'",
                project.getId()));
    }

    @Test
    void uc18DeactivatesExpiredActiveLinks() {
        Project project = newProject("E2E UC18 Prošlé odkazy");
        long menuId = savedMenu(project, "Menu").getMenuId();
        ShareLink expired = sharing.shareRevision(menus.openLatestRevision(menuId));
        exec("UPDATE share_links SET created_at = SYSTIMESTAMP - INTERVAL '20' DAY, "
                + "valid_until = SYSTIMESTAMP - INTERVAL '1' HOUR WHERE link_id = ?", expired.getId());
        long otherMenuId = savedMenu(project, "Jiné menu").getMenuId();
        ShareLink valid = sharing.shareRevision(menus.openLatestRevision(otherMenuId));
        // the seed also has expired but active links: they are switched back afterwards
        List<Object> seedActive = rows("SELECT link_id FROM share_links WHERE active = 'Y' AND link_id NOT IN (?, ?)",
                expired.getId(), valid.getId()).stream().map(r -> r.get("LINK_ID")).toList();
        try {
            sharing.deactivateExpiredLinks();
            // only own rows are asserted: the job is global and another test run may share the database
            assertEquals("N", row("SELECT active FROM share_links WHERE link_id = ?", expired.getId()).get("ACTIVE"));
            assertEquals("Y", row("SELECT active FROM share_links WHERE link_id = ?", valid.getId()).get("ACTIVE"));
            assertEquals(1, count("SELECT COUNT(*) FROM share_links WHERE menu_id = ? AND valid_until > SYSTIMESTAMP",
                    otherMenuId));
        } finally {
            for (Object id : seedActive) exec("UPDATE share_links SET active = 'Y' WHERE link_id = ?", id);
        }
    }

    /** Sample for the documentation: latest revision of seed menu 1 as PDF + PNG of page 1 (nothing is written to the DB). */
    @Test
    void writesSampleOfSeedMenu() throws Exception {
        MenuRevision revision = menus.openLatestRevision(1);
        Path pdf = tmp.resolve("sample-menu.pdf");
        new PdfMenuRenderer().render(revision, menus.title(1), menus.guestCount(1), PriceStrategy.WITH_VAT, pdf);
        Files.createDirectories(SCREENSHOTS);
        Files.copy(pdf, SCREENSHOTS.resolve("sample-menu.pdf"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, 110);
            ImageIO.write(page, "png", SCREENSHOTS.resolve("sample-menu-pdf.png").toFile());
            assertTrue(new PDFTextStripper().getText(doc).contains("Revize " + revision.getRevisionNo()));
        }
    }
}
