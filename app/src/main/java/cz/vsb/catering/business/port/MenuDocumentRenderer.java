package cz.vsb.catering.business.port;

import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.pricing.PriceStrategy;
import java.io.IOException;
import java.nio.file.Path;

/** Renders a menu revision into a printable document (UC14); the DAL implements it with PDFBox. */
public interface MenuDocumentRenderer {
    /** Writes the document for the revision to target. title is "<project> – <menu>". */
    void render(MenuRevision revision, String title, int guestCount, PriceStrategy strategy, Path target)
            throws IOException;
}
