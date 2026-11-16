package cz.vsb.catering.presentation.desktop;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.Customer;
import cz.vsb.catering.business.domain.Product;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Properties;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** UC05 and UC06 in their windows against Oracle: create with field errors, delete, refused deletes.
 *  Records are created by the test and removed (also in @AfterAll if a step fails).
 *  With -Dcatering.screenshot=dir the windows with seed data are saved as PNGs. */
class CustomersCatalogWindowTest {
    private static final String SUFFIX = String.valueOf(System.currentTimeMillis() % 100000);
    private static final String EMAIL = "uitest" + SUFFIX + "@example.com", PRODUCT = "UI test produkt " + SUFFIX;
    private static ScriptedPrompter ui;
    private static AppContext ctx;

    @BeforeAll
    static void setUp() {
        Fx.start();
        ui = new ScriptedPrompter();
        Ui.prompter = ui;
        ctx = AppContext.load();
    }

    @AfterAll
    static void cleanUp() throws Exception {
        Ui.prompter = new Prompter.Dialogs();
        Properties config = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("config/db.properties"))) {
            config.load(in);
        }
        try (Connection c = DriverManager.getConnection(config.getProperty("db.url"),
                config.getProperty("db.user"), config.getProperty("db.password"))) {
            for (String[] sql : new String[][]{{"DELETE FROM customers WHERE email = ?", EMAIL},
                                               {"DELETE FROM products WHERE name = ?", PRODUCT}})
                try (PreparedStatement ps = c.prepareStatement(sql[0])) {
                    ps.setString(1, sql[1]);
                    ps.executeUpdate();
                }
        }
    }

    @Test
    void customersCreateDeleteAndRefusedDelete() {
        CustomersWindow w = Fx.call(() -> new CustomersWindow(ctx.customers));
        Fx.run(() -> w.stage().show());
        screenshot(w.stage(), w.table, "desktop-customers.png");
        Fx.run(() -> w.search.setText("zadny-takovy-zakaznik"));
        pause();                                           // the search is debounced (A-40)
        assertTrue(w.table.getItems().isEmpty());
        Fx.run(() -> w.search.setText(""));
        pause();
        Fx.run(() -> {
            w.newCustomer.fire();
            w.name.setText("UI Tester");
            w.email.setText("neplatny");
            w.phone.setText("12");
            w.save.fire();
        });
        assertTrue(w.email.getStyleClass().contains("field-error"));
        assertTrue(w.phone.getStyleClass().contains("field-error"));
        assertTrue(ctx.customers.search(EMAIL).isEmpty());

        Fx.run(() -> {
            w.email.setText(EMAIL);
            w.phone.setText("+420 600 100 200");
            w.city.setText("Ostrava");
            w.save.fire();
        });
        assertEquals("Zákazník byl uložen.", ui.lastMessage());
        Customer saved = ctx.customers.search(EMAIL).get(0);
        assertEquals("Ostrava", saved.getCity());
        assertEquals(EMAIL, Fx.call(() -> w.table.getSelectionModel().getSelectedItem().getEmail()));

        ui.confirmAnswer = true;
        Fx.run(w.delete::fire);
        assertEquals("Zákazník byl smazán.", ui.lastMessage());
        assertTrue(ctx.customers.search(EMAIL).isEmpty());

        Fx.run(() -> {                                     // customer 1 has a project in the seed
            w.table.getItems().stream().filter(c -> c.getId() == 1L).findFirst().ifPresent(w.table.getSelectionModel()::select);
            w.delete.fire();
        });
        assertTrue(ui.lastMessage().contains("má projekty"), ui.lastMessage());
        assertTrue(ctx.customers.findAll().stream().anyMatch(c -> c.getId() == 1L));
        Fx.run(() -> w.stage().hide());
    }

    private static void pause() {
        try {
            Thread.sleep(450);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A-36: a saved new customer hidden by the active filter must not leave the form in "new" mode. */
    @Test
    void saveWithFilterHidingTheRowLeavesNewMode() {
        CustomersWindow w = Fx.call(() -> new CustomersWindow(ctx.customers));
        Fx.run(() -> {
            w.newCustomer.fire();
            w.name.setText("UI Filtr");
            w.email.setText(EMAIL);
            w.search.setText("zadny-takovy-zakaznik");
            w.save.fire();
        });
        assertFalse(Fx.call(() -> w.delete.isDisable()), "form is in edit mode (delete enabled)");
        ui.confirmAnswer = true;
        Fx.run(w.delete::fire);
        assertTrue(ctx.customers.search(EMAIL).isEmpty());
    }

    @Test
    void catalogCreateWithAllergensAndPhotoThenDelete(@TempDir Path dir) throws Exception {
        Path png = dir.resolve("foto.png");
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        CatalogWindow w = Fx.call(() -> new CatalogWindow(ctx.catalog));
        Fx.run(() -> w.stage().show());
        screenshot(w.stage(), w.table, "desktop-catalog.png");
        Fx.run(() -> {
            w.newProduct.fire();
            w.name.setText(PRODUCT);
            w.price.setText("abc");
            w.save.fire();
        });
        assertTrue(w.price.getStyleClass().contains("field-error"));
        assertTrue(ctx.catalog.findProducts().stream().noneMatch(p -> p.getName().equals(PRODUCT)));

        ui.file = png;
        Fx.run(() -> {
            w.price.setText("1 234,50");
            w.unit.setValue("kg");
            w.vat.setValue(new BigDecimal("21"));
            w.allergens.get(1).setSelected(true);
            w.allergens.get(7).setSelected(true);
            w.choosePhoto.fire();
            w.save.fire();
        });
        ui.file = null;
        assertEquals("Produkt byl uložen.", ui.lastMessage());
        Product saved = ctx.catalog.findProducts().stream().filter(p -> p.getName().equals(PRODUCT)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1234.50"), saved.getCurrentPrice().amount());
        assertEquals("kg", saved.getUnit());
        assertEquals(List.of(1, 7), saved.getAllergenList().stream().map(a -> a.id()).toList());
        assertArrayEquals(Files.readAllBytes(png), ctx.catalog.loadPhoto(saved.getId()));
        assertNotNull(Fx.call(() -> w.photoView.getImage()));

        Fx.run(w.delete::fire);
        assertEquals("Produkt byl smazán.", ui.lastMessage());
        assertTrue(ctx.catalog.findProducts().stream().noneMatch(p -> p.getName().equals(PRODUCT)));

        Fx.run(() -> {                                     // product 1 is used in a seed menu
            w.table.getItems().stream().filter(p -> p.getId() == 1L).findFirst().ifPresent(w.table.getSelectionModel()::select);
            w.delete.fire();
        });
        assertTrue(ui.lastMessage().contains("použit v menu"), ui.lastMessage());
        Fx.run(() -> w.stage().hide());
    }

    private static void screenshot(javafx.stage.Stage stage, javafx.scene.control.TableView<?> table, String file) {
        String dir = System.getProperty("catering.screenshot");
        if (dir == null) return;
        Fx.run(() -> table.getSelectionModel().select(0));
        Fx.run(() -> Ui.savePng(stage.getScene().getRoot(), Path.of(dir, file)));
    }
}
