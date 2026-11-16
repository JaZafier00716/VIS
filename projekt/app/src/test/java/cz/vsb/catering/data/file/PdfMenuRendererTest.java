package cz.vsb.catering.data.file;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Allergen;
import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.fake.Fixtures;
import cz.vsb.catering.business.pricing.PriceStrategy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfMenuRendererTest {
    @TempDir Path tmp;

    private static Product product(long id, String name, Allergen... allergens) {
        Product p = new Product(name, null, "porce", Money.of(100), new BigDecimal("12"), List.of(allergens));
        p.setId(id);
        return p;
    }

    @Test void pdfContainsCzechNamesPricesAndRevision() throws Exception {
        MenuRevision r = new MenuRevision(5, 3, RevisionState.WORKING, null, 1L, Fixtures.NOW, true);
        MenuCategory c = r.addCategory("Hlavní chody");
        r.addItem(c, product(1, "Svíčková na smetaně", new Allergen(1, "Lepek"), new Allergen(7, "Mléko")), new BigDecimal("40"), Money.of("189.50"), null);
        r.addItem(c, product(2, "Řízek s bramborovým salátem", new Allergen(3, "Vejce")), new BigDecimal("2.5"), Money.of(150), null);
        Path pdf = tmp.resolve("menu.pdf");

        new PdfMenuRenderer().render(r, "Svatba Novákovi – Svatební menu", 40, PriceStrategy.WITHOUT_VAT, pdf);

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertEquals(1, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Svatba Novákovi – Svatební menu"), text);
            assertTrue(text.contains("Revize 3 – PRACOVNÍ"), text);
            assertTrue(text.contains("Svíčková na smetaně"), text);
            assertTrue(text.contains("Řízek s bramborovým salátem"), text);
            assertTrue(text.contains("2,5 porce"), text);
            assertTrue(text.contains(r.totalPrice(PriceStrategy.WITHOUT_VAT).format()), text);  // 7 955,00 Kč
            assertTrue(text.contains("198,88 Kč"), text);                                      // per guest
            assertTrue(text.contains("Alergeny: Lepek, Mléko, Vejce"), text);
        }
    }

    @Test void longMenuContinuesOnNextPages() throws Exception {
        MenuRevision r = new MenuRevision(5, 1, RevisionState.WORKING, null, null, null, false);
        for (int k = 0; k < 4; k++) {
            MenuCategory c = r.addCategory("Kategorie " + k);
            for (int i = 0; i < 30; i++)
                r.addItem(c, product(k * 100 + i, "Produkt " + k + "-" + i), BigDecimal.ONE, Money.of(10), null);
        }
        Path pdf = tmp.resolve("long.pdf");
        new PdfMenuRenderer().render(r, "Dlouhé menu", 0, PriceStrategy.WITH_VAT, pdf);
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertTrue(doc.getNumberOfPages() >= 3);
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Produkt 3-29"));
            assertTrue(text.contains("Celkem"));
        }
    }

    @Test void longAllergenLineIsWrapped() throws Exception {
        MenuRevision r = new MenuRevision(5, 1, RevisionState.WORKING, null, null, null, false);
        Allergen[] all = new Allergen[14];
        for (int i = 0; i < 14; i++) all[i] = new Allergen(i + 1, "Alergen s dlouhým názvem číslo " + (i + 1));
        r.addItem(r.addCategory("Vše"), product(1, "Talíř", all), BigDecimal.ONE, Money.of(10), null);
        Path pdf = tmp.resolve("allergens.pdf");
        new PdfMenuRenderer().render(r, "Alergeny", 10, PriceStrategy.WITHOUT_VAT, pdf);
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("číslo 9"), text);
            assertTrue(text.lines().filter(l -> l.contains("Alergen s dlouhým")).count() > 1, text);
        }
    }

    @Test void unsupportedAndControlCharactersDoNotBreakRendering() throws Exception {
        MenuRevision r = new MenuRevision(5, 1, RevisionState.WORKING, null, null, null, false);
        MenuCategory c = r.addCategory("Koktejly \uD83C\uDF79\tbar");
        r.addItem(c, product(1, "Pizza \uD83C\uDF55\nsyrová\u0001"), BigDecimal.ONE, Money.of(10), null);
        Path pdf = tmp.resolve("emoji.pdf");
        new PdfMenuRenderer().render(r, "Menu \uD83C\uDF7D", 0, PriceStrategy.WITH_VAT, pdf);
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Pizza ? syrová"), text);
        }
    }

    @Test void longTitleAndCategoryAreWrappedOrCut() throws Exception {
        MenuRevision r = new MenuRevision(5, 1, RevisionState.WORKING, null, null, null, false);
        r.addItem(r.addCategory("Kategorie ".repeat(6)), product(1, "Talíř"), BigDecimal.ONE, Money.of(10), null);
        Path pdf = tmp.resolve("longtitle.pdf");
        new PdfMenuRenderer().render(r, "Velmi dlouhý název ".repeat(16), 0, PriceStrategy.WITH_VAT, pdf);
        new PdfMenuRenderer().render(r, "x".repeat(300), 0, PriceStrategy.WITH_VAT, tmp.resolve("oneword.pdf"));
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertTrue(new PDFTextStripper().getText(doc).lines().filter(l -> l.contains("Velmi dlouhý")).count() > 1);
        }
    }

    @Test void tableHeaderRepeatsAfterPageBreakInCategory() throws Exception {
        MenuRevision r = new MenuRevision(5, 1, RevisionState.WORKING, null, null, null, false);
        MenuCategory c = r.addCategory("Velká");
        for (int i = 0; i < 70; i++) r.addItem(c, product(i, "Produkt " + i), BigDecimal.ONE, Money.of(10), null);
        Path pdf = tmp.resolve("twopages.pdf");
        new PdfMenuRenderer().render(r, "Dvě stránky", 0, PriceStrategy.WITH_VAT, pdf);
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertTrue(doc.getNumberOfPages() >= 2);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            assertTrue(stripper.getText(doc).contains("Množství"));
        }
    }
}
