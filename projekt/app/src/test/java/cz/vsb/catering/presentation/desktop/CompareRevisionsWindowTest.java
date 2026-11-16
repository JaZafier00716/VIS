package cz.vsb.catering.presentation.desktop;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.RevisionComparison.Status;
import cz.vsb.catering.business.domain.User;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.control.ComboBox;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** UC10 CompareRevisionsWindow and UC09 switching to an older revision in the editor, on seed menu 1
 *  (read only: nothing is saved). */
class CompareRevisionsWindowTest {
    private static AppContext ctx;
    private static User user;
    private static ScriptedPrompter ui;

    @BeforeAll
    static void setUp() {
        Fx.start();
        ctx = AppContext.load();
        user = ctx.auth.loginDesktop("jzamostny", "heslo123");
        ui = new ScriptedPrompter();
        Ui.prompter = ui;
    }

    @AfterAll
    static void cleanUp() {
        Fx.run(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
        Ui.prompter = new Prompter.Dialogs();
    }

    @Test
    void comparesFirstAndThirdRevisionOfSeedMenu() {
        CompareRevisionsWindow w = Fx.call(() -> CompareRevisionsWindow.open(ctx, 1, 1, 3));
        Fx.run(() -> {
            assertEquals(11, w.table.getItems().size());
            assertEquals(List.of(Status.CHANGED, Status.ADDED, Status.REMOVED),
                    w.comparison().changes().stream().map(r -> r.status()).toList());
            assertTrue(w.difference.getText().startsWith("Rozdíl celkové ceny: -"), w.difference.getText());
            assertTrue(w.totals.getText().contains("změn: 3"));
        });
        String shots = System.getProperty("catering.screenshot");
        if (shots != null) Fx.run(() -> Ui.savePng(w.stage().getScene().getRoot(), Path.of(shots).resolve("desktop-compare.png")));
        Fx.run(() -> {
            w.onlyChanges.fire();                                                 // "jen změny"
            assertEquals(3, w.table.getItems().size());
            w.newer.getSelectionModel().select(w.newer.getItems().stream().filter(r -> r.getRevisionNo() == 2).findFirst().orElseThrow());
            w.compare();
            assertEquals(2, w.comparison().getNewer().getRevisionNo());
            assertEquals(3, w.table.getItems().size());                           // rev. 1 -> 2 has the same three changes
            w.stage().hide();
        });
    }

    @Test
    void editorShowsOlderRevisionsReadOnly() {
        Menu menu = new Menu("Svatební menu");
        menu.setMenuId(1L);
        MenuEditorWindow editor = Fx.call(() -> {
            MenuEditorWindow e = new MenuEditorWindow(ctx, user, menu, null);
            e.stage().show();
            return e;
        });
        Fx.run(() -> {
            assertEquals(3, editor.revision().getRevisionNo());
            assertFalse(editor.save.isDisabled());                                // newest WORKING revision
            @SuppressWarnings("unchecked")
            ComboBox<MenuRevision> revisions = (ComboBox<MenuRevision>) editor.stage().getScene().getRoot().lookup(".combo-box");
            revisions.setValue(revisions.getItems().stream().filter(r -> r.getRevisionNo() == 1).findFirst().orElseThrow());
            assertEquals(1, editor.revision().getRevisionNo());
            assertTrue(editor.revision().isHistorical());
            assertTrue(editor.save.isDisabled());
            assertTrue(editor.addCategory.isDisabled());
            assertTrue(editor.banner.isVisible());
            assertEquals(4, editor.revision().getCategories().size());

            revisions.setValue(revisions.getItems().stream().filter(r -> r.getRevisionNo() == 2).findFirst().orElseThrow());
            assertEquals(2, editor.revision().getRevisionNo());
            assertTrue(editor.save.isDisabled());                                 // SHARED

            ui.messages.clear();
            editor.copy.fire();                                                   // newest is WORKING: refused as a dialog (A-05)
            assertEquals(2, editor.revision().getRevisionNo());
            assertEquals(1, ui.messages.size(), "the conflict is shown, not thrown on the FX thread");
            editor.stage().hide();                                                // not saved
        });
    }
}
