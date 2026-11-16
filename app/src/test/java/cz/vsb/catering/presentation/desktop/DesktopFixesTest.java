package cz.vsb.catering.presentation.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.User;
import java.math.BigDecimal;
import java.util.List;
import javafx.scene.control.TreeItem;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Desktop fixes of the 2026-11-16 audit (A-11, A-12, A-21, A-37, A-39) on the seed menu 1; nothing is saved. */
class DesktopFixesTest {
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

    private static MenuEditorWindow openEditor() {
        Menu menu = new Menu("Svatební menu");
        menu.setMenuId(1L);
        return Fx.call(() -> {
            MenuEditorWindow e = new MenuEditorWindow(ctx, user, menu, null);
            e.stage().show();
            return e;
        });
    }

    /** Edits the first item in memory (the newest seed revision is WORKING), selecting it first. */
    private static void editFirstItem(MenuEditorWindow e) {
        ui.changedItem = i -> new MenuItemDialog.Values(i.getProduct(), i.getQuantity().add(BigDecimal.ONE), i.getUnitPrice(), null);
        Fx.run(() -> {
            e.table.getSelectionModel().select(e.table.getRoot().getChildren().get(0).getChildren().get(0));
            e.editItem.fire();
        });
    }

    @Test
    void selectionSurvivesEditAndCloseAsksWhenDirty() {
        MenuEditorWindow e = openEditor();
        editFirstItem(e);
        Fx.run(() -> {
            TreeItem<Object> sel = e.table.getSelectionModel().getSelectedItem();
            assertNotNull(sel, "A-11: selection kept after the edit");
            assertTrue(sel.getValue() instanceof MenuItem);
            assertFalse(e.editItem.isDisable());
            ui.confirmAnswer = false;                                              // A-12
            e.stage().fireEvent(new WindowEvent(e.stage(), WindowEvent.WINDOW_CLOSE_REQUEST));
            assertTrue(e.stage().isShowing(), "dirty editor stays open when the user declines");
            ui.confirmAnswer = true;
            e.stage().fireEvent(new WindowEvent(e.stage(), WindowEvent.WINDOW_CLOSE_REQUEST));
            assertFalse(e.stage().isShowing());
        });
    }

    @Test
    void compareOfOldestRevisionUsesTheNextOne() {
        MenuEditorWindow e = openEditor();
        Fx.run(() -> e.revisions.setValue(e.revisions.getItems().stream().filter(r -> r.getRevisionNo() == 1).findFirst().orElseThrow()));
        CompareRevisionsWindow w = Fx.call(e::compare);
        assertEquals(1, w.comparison().getOlder().getRevisionNo());
        assertEquals(2, w.comparison().getNewer().getRevisionNo());
    }

    @Test
    void technicalErrorsGetFixedTextBusinessOnesStay() {
        assertEquals("Menu mezitím změnil jiný pracovník.", Ui.userMessage(new ConflictException("Menu mezitím změnil jiný pracovník.")));
        assertEquals(Ui.TECHNICAL_ERROR, Ui.userMessage(new RuntimeException("ORA-12514 listener")));
        Fx.run(() -> Ui.error(new IllegalStateException("ORA-00001 unique")));
        assertEquals(Ui.TECHNICAL_ERROR, ui.lastMessage());
    }

    @Test
    void qrFailureStillGivesAnImageView() {
        assertNotNull(Fx.call(() -> ShareDialog.qrOrEmpty("x".repeat(5000))));
    }
}
