package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.AccessPolicy;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.presentation.web.ExpiredLinkScheduler;
import cz.vsb.catering.presentation.web.WebServer;
import java.nio.file.Path;
import java.util.Locale;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Desktop application for workers and administrators (JavaFX). With {@code web.embedded=true} (dev/demo)
 *  it starts the customer web server too, so share links work while the desktop app runs; in production
 *  the application server runs it (WebMain). A WORKER gets the main window, an ADMIN without WORKER only the admin mode.
 *
 *  <p>Dev-only system properties for demos and screenshots (never use them in production):
 *  {@code -Dcatering.autologin=user:password} skips the login dialog;
 *  {@code -Dcatering.screenshot=/dir} saves PNGs of the main window (project 1), the editor of
 *  "Svatební menu", its comments window, (for an admin) the users window and an archived project
 *  into the directory and then exits. */
public class DesktopApp extends Application {
    private WebServer web;
    private ExpiredLinkScheduler scheduler;

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage stage) {
        Platform.setImplicitExit(false);                  // closing the login dialog must not end the app
        Locale.setDefault(Locale.forLanguageTag("cs-CZ"));   // Czech DatePicker and formats
        AppContext ctx;
        try {
            ctx = AppContext.load();
        } catch (Exception e) {
            Ui.error(e);
            Platform.exit();
            return;
        }
        if (ctx.webEmbedded) startWeb(ctx);                  // A-55: otherwise the application server runs it (WebMain)
        User user = login(ctx);
        if (user == null) {
            Platform.exit();
            return;
        }
        if (!AccessPolicy.canUseDesktopWorker(user)) {                    // A-17: ADMIN only = straight into the admin mode (UC17)
            UsersWindow users = new UsersWindow(ctx.users, user);
            users.stage().setOnHidden(e -> Platform.exit());
            Platform.setImplicitExit(true);
            users.stage().show();
            return;
        }
        MainWindow main = new MainWindow(ctx, user, stage);
        stage.show();
        stage.setOnHidden(e -> Platform.exit());          // closing the main window ends the app (stop() cleans up)
        Platform.setImplicitExit(true);
        String shots = System.getProperty("catering.screenshot");
        if (shots != null) screenshots(main, Path.of(shots));
    }

    private void startWeb(AppContext ctx) {
        try {
            web = new WebServer(ctx, ctx.webPort);           // customer pages and /manager (UC15)
            web.start();
        } catch (Exception e) {
            web = null;
            Ui.warn("Webové rozhraní pro zákazníky se nepodařilo spustit (port " + ctx.webPort
                    + "). Sdílené odkazy nebudou dostupné.\n" + e.getMessage());
        }
        scheduler = new ExpiredLinkScheduler(ctx.sharing, ExpiredLinkScheduler.DAILY);   // UC18
        scheduler.start();
    }

    private static User login(AppContext ctx) {
        String auto = System.getProperty("catering.autologin");
        if (auto != null && auto.contains(":")) {
            try {
                return ctx.auth.loginDesktop(auto.substring(0, auto.indexOf(':')), auto.substring(auto.indexOf(':') + 1));
            } catch (Exception e) {
                Ui.error(e);
            }
        }
        return new LoginDialog(ctx.auth).show().orElse(null);
    }

    @Override public void stop() {
        if (web != null) web.stop();
        if (scheduler != null) scheduler.stop();
    }

    /** Dev-only: opens the windows one after another, snapshots them and exits. */
    private static void screenshots(MainWindow main, Path dir) {
        main.selectProject(1);
        later(1.5, () -> {
            Ui.savePng(main.stage().getScene().getRoot(), dir.resolve("desktop-main.png"));
            MenuEditorWindow editor = main.findMenu("Svatební menu").map(main::openMenu).orElse(null);
            if (editor == null) { Platform.exit(); return; }
            later(1.5, () -> {
                Ui.savePng(editor.stage().getScene().getRoot(), dir.resolve("desktop-menu-editor.png"));
                CommentsWindow comments = editor.openComments();
                later(1.5, () -> {
                    Ui.savePng(comments.stage().getScene().getRoot(), dir.resolve("desktop-comments.png"));
                    if (!main.user().isActiveAdmin()) { archivedShot(main, dir); return; }
                    UsersWindow users = new UsersWindow(main.ctx().users, main.user());
                    users.stage().show();
                    later(1.5, () -> {
                        Ui.savePng(users.stage().getScene().getRoot(), dir.resolve("desktop-users.png"));
                        users.stage().hide();
                        archivedShot(main, dir);
                    });
                });
            });
        });
    }

    /** Last screenshot: the first archived project, its write actions disabled (UC03). */
    private static void archivedShot(MainWindow main, Path dir) {
        main.projectBox.getItems().stream().filter(p -> p.isArchived()).findFirst()
                .ifPresent(main.projectBox.getSelectionModel()::select);
        main.stage().toFront();
        later(1.5, () -> {
            Ui.savePng(main.stage().getScene().getRoot(), dir.resolve("desktop-archived.png"));
            Platform.exit();
        });
    }

    private static void later(double seconds, Runnable action) {
        PauseTransition p = new PauseTransition(Duration.seconds(seconds));
        p.setOnFinished(e -> action.run());
        p.play();
    }
}
