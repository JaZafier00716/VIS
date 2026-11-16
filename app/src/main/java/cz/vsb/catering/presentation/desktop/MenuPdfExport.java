package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.pricing.PriceStrategy;
import cz.vsb.catering.business.service.MenuExportService;
import java.util.Optional;

/** UC14 action "Exportovat do PDF" of the menu editor: the PDF goes into the menu's folder;
 *  a name clash is handled like an upload (UC02 8.1). */
final class MenuPdfExport {
    private MenuPdfExport() {}

    /** Returns the stored document, or empty when the user cancelled the rename or an error was shown. */
    static Optional<Document> exportAndReport(AppContext ctx, Menu menu, int revisionNo, PriceStrategy strategy) {
        try {
            Optional<Document> doc = UploadConflictDialog.createWithRename(menu.getParent(),
                    MenuExportService.fileName(menu, revisionNo),
                    name -> ctx.menuExport.exportPdf(menu, revisionNo, menu.getParent(), strategy, name));
            doc.ifPresent(d -> Ui.info("PDF „" + d.getName() + "“ bylo uloženo do složky „" + menu.getParent().getName() + "“."));
            return doc;
        } catch (Exception e) {
            Ui.error(e);
            return Optional.empty();
        }
    }
}
