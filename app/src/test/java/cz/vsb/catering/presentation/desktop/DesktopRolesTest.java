package cz.vsb.catering.presentation.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A-17 and A-54 in the desktop against the seed users: tsvoboda = MANAGER only, mkral = ADMIN+MANAGER,
 *  jzamostny = ADMIN+WORKER, pnovak = WORKER. */
class DesktopRolesTest {
    private static AppContext ctx;

    @BeforeAll static void setUp() {
        Fx.start();
        ctx = AppContext.load();
    }

    @Test void managerOnlyAccountIsRefusedByTheDesktopLogin() {
        ValidationException e = assertThrows(ValidationException.class, () -> ctx.auth.loginDesktop("tsvoboda", "heslo123"));
        assertTrue(e.getMessage().contains("webovému přehledu"));
        assertEquals("tsvoboda", ctx.auth.login("tsvoboda", "heslo123").getUsername(), "the web login is a different path");
    }

    @Test void adminWithoutWorkerGetsNoWorkerWindow() {
        User admin = ctx.auth.loginDesktop("mkral", "heslo123");                 // allowed: admin mode only
        assertThrows(ValidationException.class, () -> Fx.call(() -> new MainWindow(ctx, admin, new Stage())));
        Fx.run(() -> assertTrue(new UsersWindow(ctx.users, admin).table != null));       // the admin mode itself opens
    }

    @Test void workerGetsMainWindowAndAdminSwitchOnlyWithAdminRole() {
        User worker = ctx.auth.loginDesktop("pnovak", "heslo123");
        assertTrue(Fx.call(() -> new MainWindow(ctx, worker, new Stage())).stage() != null);
        User both = ctx.auth.loginDesktop("jzamostny", "heslo123");
        assertTrue(both.isActiveAdmin());
    }

    @Test void itemNoteFieldSaysTheCustomerSeesIt() {
        assertEquals("Poznámka (vidí zákazník)", MenuItemDialog.NOTE_LABEL);
    }
}
