package cz.vsb.catering.data.file;

import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.pricing.PriceStrategy;
import cz.vsb.catering.business.port.MenuDocumentRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.TreeSet;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** UC14: PDF of a menu revision with Apache PDFBox 3. DejaVu Sans is embedded because the standard
 *  PDF fonts have no Czech letters. A new page starts whenever the current one is full. */
public class PdfMenuRenderer implements MenuDocumentRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm");
    private static final float MARGIN = 50;
    private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
    private static final float LINE = 16;
    // right edges of the number columns; the product column ends where the quantity starts
    private static final float QTY_RIGHT = MARGIN + 320, UNIT_RIGHT = MARGIN + 410, PRICE_RIGHT = MARGIN + WIDTH;

    @Override
    public void render(MenuRevision revision, String title, int guestCount, PriceStrategy strategy, Path target)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            Writer w = new Writer(doc, font(doc, "DejaVuSans.ttf"), font(doc, "DejaVuSans-Bold.ttf"));
            try {
                w.wrapped(w.bold, 16, title);
                w.y -= 6;
                String saved = revision.getCreatedAt() == null ? "neuloženo" : DATE.format(revision.getCreatedAt());
                w.wrapped(w.regular, 10, "Revize " + revision.getRevisionNo() + " – " + revision.getState().label()
                        + " – " + saved + " – ceny " + strategy.label());
                w.y -= 8;
                for (MenuCategory category : revision.getCategories()) {
                    w.need(3 * LINE);
                    w.wrapped(w.bold, 12, category.getName());
                    w.header();
                    for (MenuItem item : category.getItems()) {
                        w.itemRow( item.getProduct().getName(),
                                qty(item.getQuantity()) + " " + item.getProduct().getUnitLabel(),
                                item.getUnitPrice().format(), item.price(strategy).format());
                    }
                    w.row(w.bold, "Mezisoučet " + category.getName(), "", "", category.subtotal(strategy).format());
                    w.y -= 6;
                }
                w.need(4 * LINE);
                w.line();
                w.row(w.bold, "Celkem", "", "", revision.totalPrice(strategy).format());
                if (guestCount > 0)
                    w.row(w.regular, "Cena na hosta (" + guestCount + " hostů)", "", "",
                            revision.pricePerGuest(strategy, guestCount).format());
                TreeSet<String> allergens = new TreeSet<>();
                revision.getCategories().forEach(c -> c.getItems().forEach(i -> allergens.addAll(i.getProduct().getAllergens())));
                w.y -= 6;
                w.wrapped(w.regular, 10, "Alergeny: " + (allergens.isEmpty() ? "žádné" : String.join(", ", allergens)));
            } finally {
                w.close();
            }
            doc.save(target.toFile());
        }
    }

    private static PDType0Font font(PDDocument doc, String file) throws IOException {
        try (InputStream in = PdfMenuRenderer.class.getResourceAsStream("fonts/" + file)) {
            if (in == null) throw new IOException("Font " + file + " chybí v resources.");
            return PDType0Font.load(doc, in);
        }
    }

    private static String qty(BigDecimal q) { return q.stripTrailingZeros().toPlainString().replace('.', ','); }

    /** Cursor over the pages: y is the baseline of the next line. */
    private static final class Writer {
        final PDDocument doc;
        final PDType0Font regular, bold;
        PDPageContentStream out;
        float y;

        Writer(PDDocument doc, PDType0Font regular, PDType0Font bold) throws IOException {
            this.doc = doc;
            this.regular = regular;
            this.bold = bold;
            newPage();
        }

        void newPage() throws IOException {
            if (out != null) out.close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            out = new PDPageContentStream(doc, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        void need(float height) throws IOException { if (y - height < MARGIN) newPage(); }

        void text(PDType0Font font, float size, float x, String s) throws IOException {
            need(LINE);
            show(font, size, x, fit(font, size, s, WIDTH - (x - MARGIN)));
            y -= LINE + (size > 11 ? 4 : 0);
        }

        /** Text broken into lines at spaces so it fits the page width (a single overlong word is cut by text()). */
        void wrapped(PDType0Font font, float size, String s) throws IOException {
            StringBuilder line = new StringBuilder();
            for (String word : clean(font, s).split(" ")) {
                if (!line.isEmpty() && width(font, size, line + " " + word) > WIDTH) {
                    text(font, size, MARGIN, line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
            text(font, size, MARGIN, line.toString());
        }

        void header() throws IOException { row(bold, "Produkt", "Množství", "Cena/jedn.", "Cena"); }

        /** Item row; after a page break inside a category the table header is repeated. */
        void itemRow(String product, String qty, String unitPrice, String price) throws IOException {
            if (y - LINE < MARGIN) {
                newPage();
                header();
            }
            row(regular, product, qty, unitPrice, price);
        }

        void row(PDType0Font font, String product, String qty, String unitPrice, String price) throws IOException {
            need(LINE);
            show(font, 10, MARGIN, fit(font, 10, product, QTY_RIGHT - 70 - MARGIN));
            right(font, QTY_RIGHT, qty);
            right(font, UNIT_RIGHT, unitPrice);
            right(font, PRICE_RIGHT, price);
            y -= LINE;
        }

        void line() throws IOException {
            out.moveTo(MARGIN, y + LINE - 4);
            out.lineTo(MARGIN + WIDTH, y + LINE - 4);
            out.stroke();
        }

        private void right(PDType0Font font, float rightEdge, String s) throws IOException {
            if (!s.isEmpty()) show(font, 10, rightEdge - width(font, 10, s), s);
        }

        private void show(PDType0Font font, float size, float x, String s) throws IOException {
            out.beginText();
            out.setFont(font, size);
            out.newLineAtOffset(x, y);
            out.showText(clean(font, s));
            out.endText();
        }

        /** Cuts a long product name so it does not run into the quantity column. */
        private static String fit(PDType0Font font, float size, String s, float max) throws IOException {
            if (width(font, size, s) <= max) return s;
            while (!s.isEmpty() && width(font, size, s + "…") > max) s = s.substring(0, s.length() - 1);
            return s + "…";
        }

        /** Keeps characters the font has, control characters (TAB, newline...) become a space, the rest "?". */
        private static String clean(PDType0Font font, String s) throws IOException {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                i += Character.charCount(cp);
                if (Character.isISOControl(cp)) out.append(' ');
                else if (hasGlyph(font, cp)) out.appendCodePoint(cp);
                else out.append('?');
            }
            return out.toString();
        }

        /** PDType0Font.hasGlyph(int) treats the code point as a glyph id (Identity-H) and is wrong here;
         *  encode() throws exactly when the font has no glyph for the character. */
        private static boolean hasGlyph(PDType0Font font, int codePoint) {
            try {
                font.encode(new String(Character.toChars(codePoint)));
                return true;
            } catch (IOException | IllegalArgumentException e) {
                return false;
            }
        }

        private static float width(PDType0Font font, float size, String s) throws IOException {
            return font.getStringWidth(clean(font, s)) / 1000 * size;
        }

        void close() throws IOException { out.close(); }
    }
}
