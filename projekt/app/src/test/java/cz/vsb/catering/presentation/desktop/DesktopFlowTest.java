package cz.vsb.catering.presentation.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectNode;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.pricing.PriceStrategy;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.control.TreeItem;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/** Drives the real desktop windows against the local Oracle: UC02, UC08, UC11, UC13, UC19.
 *  Works only in its own project, which is deleted (with its stored files) at the end. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DesktopFlowTest {
    private static final String NAME = "UI test " + System.currentTimeMillis();
    private static Properties config;
    private static AppContext ctx;
    private static User user;
    private static ScriptedPrompter ui;
    private static MainWindow main;
    private static Path tmp;
    private static Project project;
    private static Menu menu;
    private static MenuEditorWindow editor;

    @BeforeAll
    static void setUp() throws Exception {
        Fx.start();
        config = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("config/db.properties"))) {
            config.load(in);
        }
        ctx = AppContext.load();
        user = ctx.auth.loginDesktop("jzamostny", "heslo123");
        ui = new ScriptedPrompter();
        Ui.prompter = ui;
        tmp = Files.createTempDirectory("catering-ui");
        main = Fx.call(() -> {
            MainWindow w = new MainWindow(ctx, user, new Stage());
            w.stage().show();
            return w;
        });
    }

    @AfterAll
    static void cleanUp() throws Exception {
        Fx.run(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
        Ui.prompter = new Prompter.Dialogs();
        if (project == null) return;
        try (Connection c = DriverManager.getConnection(config.getProperty("db.url"),
                config.getProperty("db.user"), config.getProperty("db.password"))) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT storage_key FROM project_nodes WHERE project_id = ? AND storage_key IS NOT NULL")) {
                ps.setLong(1, project.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) Files.deleteIfExists(storagePath(rs.getString(1)));
                }
            }
            Path dir = storagePath("p" + project.getId());              // DocumentService keys start with p<projectId>/
            if (Files.isDirectory(dir)) try (var left = Files.list(dir)) { if (left.findAny().isEmpty()) Files.delete(dir); }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM projects WHERE project_id = ?")) {
                ps.setLong(1, project.getId());
                assertEquals(1, ps.executeUpdate());
            }
        }
    }

    private static Path storagePath(String key) {
        return Path.of(config.getProperty("documents.dir", "./documents")).resolve(key);
    }

    private static Folder tree() { return ctx.projects.loadTree(project.getId()); }

    private static Set<String> names(Folder f) {
        return f.getChildren().stream().map(ProjectNode::getName).collect(Collectors.toSet());
    }

    private static ProjectNode child(Folder f, String name) {
        return f.getChildren().stream().filter(n -> n.getName().equals(name)).findFirst().orElseThrow();
    }

    /** Selects the rows with these names in the content table of the active folder. */
    private static void selectRows(String... rowNames) {
        Fx.run(() -> {
            main.table.getSelectionModel().clearSelection();
            for (String n : rowNames)
                main.table.getSelectionModel().select(main.table.getItems().stream()
                        .filter(r -> r.getName().equals(n)).findFirst().orElseThrow());
        });
    }

    private static String total() { return Fx.call(() -> editor.total.getText()); }

    @Test @Order(1)
    void newProjectInvalidThenValid() {
        NewProjectDialog d = Fx.call(() -> new NewProjectDialog(ctx.projects, user));
        Fx.run(() -> d.guests.setText("0"));
        assertNull(Fx.call(d::submit));
        for (var field : List.of(d.name, d.customer, d.date, d.place, d.guests))
            assertTrue(field.getStyleClass().contains("field-error"), field.toString());

        Fx.run(() -> {
            d.name.setText(NAME);
            d.customer.getSelectionModel().selectFirst();
            d.date.setValue(LocalDate.now().plusDays(30));
            d.place.setText("Testovací sál");
            d.guests.setText("40");
        });
        project = Fx.call(d::submit);
        assertNotNull(project);
        assertFalse(d.guests.getStyleClass().contains("field-error"));
        Fx.run(() -> main.projectCreated(project));

        assertEquals(project.getId(), Fx.call(() -> main.projectBox.getValue().getId()));
        Folder active = Fx.call(main::activeFolder);
        assertNull(active.getParent());
        assertEquals(NAME, active.getName());
        assertTrue(ctx.projects.findAllProjects().stream().anyMatch(p -> p.getId().equals(project.getId())));
    }

    @Test @Order(2)
    void createFolderAndRenameDuplicate() {
        ui.texts.add("Podklady");
        Fx.run(main.newFolder::fire);
        ui.texts.add("Podklady");
        Fx.run(main.newFolder::fire);

        assertEquals(List.of("Podklady (2)"), ui.renames);
        assertEquals(Set.of("Podklady", "Podklady (2)"), names(tree()));
        assertEquals(2, (int) Fx.call(() -> main.table.getItems().size()));
    }

    @Test @Order(3)
    void uploadAndDownloadDocument() throws Exception {
        Fx.run(() -> main.selectInTree(child(tree(), "Podklady").getId()));
        Path file = tmp.resolve("nabidka.txt");
        Files.writeString(file, "Testovací nabídka");
        ui.file = file;
        Fx.run(main.upload::fire);

        Document doc = (Document) child((Folder) child(tree(), "Podklady"), "nabidka.txt");
        assertEquals(Files.size(file), doc.getSizeBytes());
        assertTrue(Files.exists(storagePath(doc.getStorageKey())));

        selectRows("nabidka.txt");
        assertFalse(Fx.call(main.download::isDisabled));
        ui.directory = Files.createDirectory(tmp.resolve("out"));
        Fx.run(main.download::fire);
        assertEquals("Testovací nabídka", Files.readString(ui.directory.resolve("nabidka.txt")));
        assertTrue(ui.lastMessage().startsWith("Staženo 1 dokument "), ui.lastMessage());
    }

    @Test @Order(4)
    void createMenuAndOpenEditor() {
        Fx.run(() -> main.selectInTree(tree().getId()));
        ui.texts.add("Testovací menu");
        Fx.run(main.newMenu::fire);

        menu = (Menu) child(tree(), "Testovací menu");
        List<MenuRevision> revisions = ctx.menus.listRevisions(menu.getMenuId());
        assertEquals(1, revisions.size());
        assertEquals(RevisionState.WORKING, revisions.get(0).getState());
        editor = Fx.call(() -> main.openMenu(menu));
        assertNotNull(editor);
        assertEquals(1, editor.revision().getRevisionNo());
    }

    @Test @Order(5)
    void emptyMenuIsNotSaved() {
        ui.texts.add("prázdné");
        Fx.run(editor.save::fire);
        assertTrue(ui.lastMessage().contains("alespoň jednu kategorii"), ui.lastMessage());
        assertNull(ctx.menus.openLatestRevision(menu.getMenuId()).getNote());
    }

    @Test @Order(6)
    void editWithCommandsUndoAndObserverTotals() {
        ui.texts.add("Předkrmy");
        Fx.run(editor.addCategory::fire);
        assertEquals(1, (int) Fx.call(() -> editor.table.getRoot().getChildren().size()));
        assertEquals(Money.ZERO.format(), total());

        Fx.run(() -> editor.table.getSelectionModel().select(editor.table.getRoot().getChildren().get(0)));
        ui.newItem = products -> new MenuItemDialog.Values(products.get(0), new BigDecimal("10"), Money.of(100), null);
        Fx.run(editor.addItem::fire);
        MenuRevision r = editor.revision();
        assertEquals(r.totalPrice(PriceStrategy.WITH_VAT).format(), total());

        Fx.run(editor.vat::fire);                                     // Strategy: bez DPH
        assertEquals(Money.of(1000).format(), total());

        Fx.run(() -> editor.table.getSelectionModel().select(editor.table.getRoot().getChildren().get(0).getChildren().get(0)));
        ui.changedItem = item -> new MenuItemDialog.Values(item.getProduct(), new BigDecimal("20"), Money.of(60), "bez cibule");
        Fx.run(editor.editItem::fire);
        MenuItem item = r.getCategories().get(0).getItems().get(0);
        assertEquals(0, new BigDecimal("20").compareTo(item.getQuantity()));
        assertEquals(Money.of(1200).format(), total());

        Fx.run(editor.undo::fire);
        assertEquals(0, BigDecimal.TEN.compareTo(item.getQuantity()));
        assertEquals(Money.of(1000).format(), total());
        assertFalse(Fx.call(editor.undo::isDisabled));

        // Observer: a change made directly on the revision (no UI call) updates the label too
        Fx.run(() -> r.changeItem(item, new BigDecimal("5"), Money.of(100), null));
        assertEquals(Money.of(500).format(), total());
        Fx.run(() -> r.changeItem(item, BigDecimal.TEN, Money.of(100), null));
        assertEquals(Money.of(1000).format(), total());
    }

    @Test @Order(7)
    void saveRevisionWithNote() {
        ui.texts.add("první verze");
        Fx.run(editor.save::fire);
        assertEquals("Uloženo jako revize č. 1.", ui.lastMessage());

        MenuRevision stored = ctx.menus.openLatestRevision(menu.getMenuId());
        assertEquals(1, stored.getRevisionNo());
        assertEquals("první verze", stored.getNote());
        assertEquals(1, stored.getCategories().size());
        assertEquals(0, BigDecimal.TEN.compareTo(stored.getCategories().get(0).getItems().get(0).getQuantity()));
    }

    @Test @Order(8)
    void shareShowsLinkAndQr() {
        assertFalse(Fx.call(editor.share::isDisabled));
        Fx.run(editor.share::fire);

        ShareLink link = ctx.sharing.findValidLink(menu.getMenuId()).orElseThrow();
        assertEquals(1, link.getRevisionNo());
        assertNotNull(ui.shown);
        assertEquals(ctx.sharing.shareUrl(link), Fx.call(() -> ui.shown.urlField.getText()));
        assertTrue(ui.shown.url.endsWith("/menu/" + link.getToken()));
        assertTrue(ui.shown.qr.getImage().getWidth() > 0);
        assertEquals(RevisionState.SHARED, ctx.menus.listRevisions(menu.getMenuId()).get(0).getState());
        assertTrue(Fx.call(editor.banner::isVisible));
        assertTrue(Fx.call(editor.addCategory::isDisabled));
    }

    @Test @Order(9)
    void workingCopyOfSharedRevisionIsSavedAsNextNumber() {
        Fx.run(editor.copy::fire);
        assertFalse(Fx.call(editor.banner::isVisible));
        assertEquals(2, editor.revision().getRevisionNo());
        assertFalse(editor.revision().isStored());

        ui.texts.add("Dezerty");
        Fx.run(editor.addCategory::fire);
        ui.texts.add("druhá verze");
        Fx.run(editor.save::fire);
        assertEquals("Uloženo jako revize č. 2.", ui.lastMessage());

        List<MenuRevision> revisions = ctx.menus.listRevisions(menu.getMenuId());
        assertEquals(List.of(2, 1), revisions.stream().map(MenuRevision::getRevisionNo).toList());
        assertEquals(RevisionState.WORKING, revisions.get(0).getState());
        assertEquals(RevisionState.SHARED, revisions.get(1).getState());
        assertEquals(1, ctx.menus.openRevision(menu.getMenuId(), 1).getCategories().size());
        assertEquals(2, ctx.menus.openRevision(menu.getMenuId(), 2).getCategories().size());
    }

    @Test @Order(10)
    void commentsAreResolvedInCommentsWindow() {
        long menuId = menu.getMenuId();
        ShareLink link = ctx.sharing.findValidLink(menuId).orElseThrow();
        MenuCategory category = ctx.menus.openRevision(menuId, 1).getCategories().get(0);
        long productId = category.getItems().get(0).getProduct().getId();
        ctx.sharing.addComment(link.getToken(), "Test Zákazník", "Prosíme méně.", category.getId(), productId);
        ctx.sharing.addComment(link.getToken(), "Test Zákazník", "Celé menu je fajn.", null, null);
        Fx.run(editor::refreshCommentCount);
        assertTrue(Fx.call(() -> editor.comments.getText()).contains("(2 nevyřízených)"));

        CommentsWindow window = Fx.call(editor::openComments);
        assertEquals(2, (int) Fx.call(() -> window.list.getItems().size()));
        Fx.run(() -> window.list.getSelectionModel().select(
                window.list.getItems().stream().filter(Comment::isOnItem).findFirst().orElseThrow()));

        // UC13 3.1-3.5: edit the menu, after the save the note is pre-filled
        Fx.run(window.editMenu::fire);
        assertEquals(2, editor.revision().getRevisionNo());
        TreeItem<Object> selected = Fx.call(() -> editor.table.getSelectionModel().getSelectedItem());
        assertEquals(Long.valueOf(productId), ((MenuItem) selected.getValue()).getProduct().getId());
        ui.texts.add("dle komentáře");
        Fx.run(editor.save::fire);
        assertEquals("Upraveno v revizi č. 2", Fx.call(() -> window.note.getText()));
        Fx.run(window.resolve::fire);

        // UC13 4.1-4.5: the other comment was resolved meanwhile by someone else
        Comment other = Fx.call(() -> window.list.getSelectionModel().getSelectedItem());
        assertFalse(other.isOnItem());
        ctx.sharing.resolveComment(other.getId(), "vyřízeno jinde", user);
        Fx.run(window.resolve::fire);
        assertTrue(ui.messages.stream().anyMatch(m -> m.startsWith("Komentář již vyřídil")), ui.messages.toString());
        assertEquals("Všechny komentáře jsou vyřízeny.", ui.lastMessage());

        List<Comment> stored = ctx.sharing.listComments(menuId, 1);
        assertTrue(stored.stream().allMatch(Comment::isResolved));
        Comment onItem = stored.stream().filter(Comment::isOnItem).findFirst().orElseThrow();
        assertEquals("Upraveno v revizi č. 2", onItem.getResolutionNote());
        assertEquals(user.getId(), onItem.getResolvedBy());
        assertTrue(Fx.call(() -> editor.comments.getText()).contains("(0 nevyřízených)"));
        Fx.run(() -> window.stage().hide());
    }

    @Test @Order(11)
    void deleteRefusedThenAllowed() {
        Fx.run(() -> editor.stage().hide());
        Fx.run(() -> main.selectInTree(tree().getId()));

        selectRows("Podklady", "Podklady (2)");                      // Podklady contains the document
        Fx.run(main.delete::fire);
        assertTrue(ui.lastMessage().startsWith("Nic nebylo smazáno."), ui.lastMessage());
        assertTrue(ui.lastMessage().contains("není prázdná"));
        assertTrue(names(tree()).containsAll(Set.of("Podklady", "Podklady (2)")));

        selectRows("Testovací menu");                                // active share link
        Fx.run(main.delete::fire);
        assertTrue(ui.lastMessage().contains("aktivní sdílený odkaz"), ui.lastMessage());
        assertTrue(names(tree()).contains("Testovací menu"));

        ui.texts.add("Menu ke smazání");
        Fx.run(main.newMenu::fire);
        selectRows("Menu ke smazání");
        Fx.run(main.delete::fire);
        assertFalse(names(tree()).contains("Menu ke smazání"));

        Folder podklady = (Folder) child(tree(), "Podklady");
        Document doc = (Document) child(podklady, "nabidka.txt");
        Fx.run(() -> main.selectInTree(podklady.getId()));
        selectRows("nabidka.txt");
        Fx.run(main.delete::fire);
        assertTrue(((Folder) child(tree(), "Podklady")).getChildren().isEmpty());
        assertFalse(Files.exists(storagePath(doc.getStorageKey())));

        Fx.run(() -> main.selectInTree(tree().getId()));
        selectRows("Podklady", "Podklady (2)");
        Fx.run(main.delete::fire);
        assertEquals(Set.of("Testovací menu"), names(tree()));
    }

    private static Stage window(String title) {
        return Fx.call(() -> Window.getWindows().stream().filter(w -> w instanceof Stage st && title.equals(st.getTitle()))
                .map(w -> (Stage) w).findFirst().orElse(null));
    }

    @Test @Order(12)
    void editorCompareExportAndHistoricalRevision() {
        Fx.run(() -> main.selectInTree(tree().getId()));
        editor = Fx.call(() -> main.openMenu(menu));                  // latest = rev. 2, stored WORKING
        assertFalse(Fx.call(editor.compare::isDisabled));

        Fx.run(editor.compare::fire);                                  // UC10
        Stage compareStage = window("Catering IS · Porovnání revizí");
        assertNotNull(compareStage);
        assertTrue(Fx.call(compareStage::isShowing));
        Fx.run(compareStage::hide);
        CompareRevisionsWindow compare = Fx.call(editor::compare);
        assertEquals(1, (int) Fx.call(() -> compare.older.getValue().getRevisionNo()));
        assertEquals(2, (int) Fx.call(() -> compare.newer.getValue().getRevisionNo()));
        Fx.run(() -> compare.stage().hide());

        assertFalse(Fx.call(editor.exportPdf::isDisabled));            // UC14
        Fx.run(editor.exportPdf::fire);
        Document pdf = tree().getChildren().stream().filter(n -> n instanceof Document d && d.getName().endsWith(".pdf"))
                .map(n -> (Document) n).findFirst().orElseThrow();
        assertTrue(Files.exists(storagePath(pdf.getStorageKey())));
        assertTrue(ui.lastMessage().contains("bylo uloženo"), ui.lastMessage());
        assertTrue(Fx.call(() -> main.table.getItems().stream().anyMatch(n -> n.getName().equals(pdf.getName()))),
                "main window tree reloaded after the export");

        Fx.run(() -> editor.revisions.setValue(editor.revisions.getItems().stream()   // UC09
                .filter(r -> r.getRevisionNo() == 1).findFirst().orElseThrow()));
        assertTrue(editor.revision().isHistorical());
        assertTrue(Fx.call(editor.banner::isVisible));
        assertTrue(Fx.call(() -> editor.bannerText.getText()).startsWith("Starší revize"));
        Fx.run(() -> editor.stage().hide());
    }

    @Test @Order(13)
    void mainWindowButtonsOpenTheirWindows() {
        ui.dialogResult = null;                                         // UC03: dialog opens, cancelled
        Fx.run(main.editProject::fire);
        assertEquals("Upravit projekt", ui.dialogs.get(ui.dialogs.size() - 1).getTitle());

        Fx.run(() -> main.selectProject(1));                           // UC04: search finds our project
        ui.dialogResult = project;
        Fx.run(() -> {
            main.searchField.setText(NAME);
            main.search.fire();
        });
        assertEquals("Vyhledat projekt", ui.dialogs.get(ui.dialogs.size() - 1).getTitle());
        assertEquals(project.getId(), Fx.call(() -> main.projectBox.getValue().getId()));

        Fx.run(main.editProject::fire);                                // UC03 result: list reloaded, project reselected
        assertEquals(project.getId(), Fx.call(() -> main.projectBox.getValue().getId()));
        ui.dialogResult = null;

        Fx.run(main.customers::fire);                                  // UC05
        Fx.run(main.catalog::fire);                                    // UC06
        for (String title : List.of("Catering IS · Zákazníci", "Catering IS · Katalog produktů")) {
            Stage st = window(title);
            assertNotNull(st, title);
            assertTrue(Fx.call(st::isShowing));
            Fx.run(st::hide);
        }
    }

    @Test @Order(14)
    void archivedProjectIsReadOnly() {                                 // UC03: content tree locked too
        ShareLink link = ctx.sharing.findValidLink(menu.getMenuId()).orElseThrow();
        ctx.sharing.addComment(link.getToken(), "Jana", "Ještě jedna otázka.", null, null);   // unresolved
        ctx.projects.archiveProject(project.getId());
        Fx.run(() -> main.projectCreated(project));                    // reloads the list, reselects
        assertTrue(Fx.call(main.create::isDisabled));
        assertTrue(Fx.call(main.upload::isDisabled));
        selectRows(menu.getName());
        assertTrue(Fx.call(main.delete::isDisabled));
        assertFalse(Fx.call(main.editProject::isDisabled));            // opens the read-only form

        editor = Fx.call(() -> main.openMenu(menu));
        assertTrue(editor.readOnly);
        assertTrue(Fx.call(editor.banner::isVisible));
        assertEquals("Projekt je archivovaný – jen pro čtení.", Fx.call(() -> editor.bannerText.getText()));
        for (var b : List.of(editor.save, editor.share, editor.exportPdf, editor.addCategory, editor.addItem, editor.undo))
            assertTrue(Fx.call(b::isDisabled), b.getText());
        assertFalse(Fx.call(editor.copy::isVisible));
        assertFalse(Fx.call(editor.compare::isDisabled));              // comparing stays allowed
        CommentsWindow comments = Fx.call(editor::openComments);
        assertTrue(Fx.call(() -> comments.list.getItems().stream().anyMatch(c -> !c.isResolved())));
        assertTrue(Fx.call(comments.resolve::isDisabled));
        assertTrue(Fx.call(comments.editMenu::isDisabled));
        Fx.run(() -> { comments.stage().hide(); editor.stage().hide(); });
    }
}
