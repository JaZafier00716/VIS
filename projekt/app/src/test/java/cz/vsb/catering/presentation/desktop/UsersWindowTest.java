package cz.vsb.catering.presentation.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.PasswordHasher;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeUserMapper;
import cz.vsb.catering.business.service.UserService;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** UC16 in UsersWindow: create a user in Oracle (removed afterwards) and the last-admin rule
 *  (on an in-memory user list, so no seed administrator is ever changed). */
class UsersWindowTest {
    private static final String USERNAME = "uitest" + (System.currentTimeMillis() % 100000);
    private static ScriptedPrompter ui;

    @BeforeAll
    static void setUp() {
        Fx.start();
        ui = new ScriptedPrompter();
        Ui.prompter = ui;
    }

    @AfterAll
    static void cleanUp() throws Exception {
        Ui.prompter = new Prompter.Dialogs();
        Properties config = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("config/db.properties"))) {
            config.load(in);
        }
        try (Connection c = DriverManager.getConnection(config.getProperty("db.url"),
                config.getProperty("db.user"), config.getProperty("db.password"));
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE username = ?")) {
            ps.setString(1, USERNAME);                                   // user_roles cascade
            ps.executeUpdate();
        }
    }

    @Test
    void createUserWithFieldErrorsThenValid() {
        AppContext ctx = AppContext.load();
        User admin = ctx.auth.login("mkral", "heslo123");
        UsersWindow w = Fx.call(() -> new UsersWindow(ctx.users, admin));
        Fx.run(() -> {
            w.username.setText(USERNAME);
            w.fullName.setText("UI Tester");
            w.email.setText("neplatny-email");
            w.password.setText("kratke");
            w.save.fire();
        });
        assertTrue(w.email.getStyleClass().contains("field-error"));
        assertTrue(w.password.getStyleClass().contains("field-error"));
        assertTrue(ctx.users.findAllUsers(admin).stream().noneMatch(u -> u.getUsername().equals(USERNAME)));

        Fx.run(() -> {
            w.email.setText(USERNAME + "@catering.cz");
            w.password.setText("heslo1234");
            w.roles.get(Role.MANAGER).setSelected(true);
            w.save.fire();
        });
        assertFalse(w.email.getStyleClass().contains("field-error"));
        assertEquals("Uživatel byl uložen.", ui.lastMessage());
        User saved = ctx.users.findAllUsers(admin).stream().filter(u -> u.getUsername().equals(USERNAME)).findFirst().orElseThrow();
        assertEquals(Set.of(Role.WORKER, Role.MANAGER), saved.getRoles());
        assertEquals(USERNAME, Fx.call(() -> w.table.getSelectionModel().getSelectedItem().getUsername()));
        assertTrue(ctx.auth.login(USERNAME, "heslo1234").isActive());
    }

    @Test
    void lastAdminCannotBeRemovedAndFormReverts() {
        FakeUserMapper mapper = new FakeUserMapper();
        User admin = new User("jedinyadmin", "Jediný Admin", "a@b.cz", PasswordHasher.hash("heslo1234"), true,
                Set.of(Role.ADMIN, Role.WORKER));
        mapper.add(admin);
        UsersWindow w = Fx.call(() -> new UsersWindow(new UserService(mapper, new DirectTransactionRunner()), admin));
        Fx.run(() -> {
            w.table.getSelectionModel().select(0);
            w.roles.get(Role.ADMIN).setSelected(false);
            w.save.fire();
        });
        assertEquals("Systém musí mít alespoň jednoho aktivního administrátora.", ui.lastMessage());
        assertTrue(Fx.call(() -> w.roles.get(Role.ADMIN).isSelected()), "form shows the original values (7.3)");
        assertTrue(mapper.updated.isEmpty());
        assertTrue(mapper.stored(admin.getId()).isActiveAdmin());

        Fx.run(() -> {
            w.active.setSelected(false);
            w.save.fire();
        });
        assertEquals("Systém musí mít alespoň jednoho aktivního administrátora.", ui.lastMessage());
        assertTrue(Fx.call(w.active::isSelected));
        Fx.run(() -> w.stage().hide());
    }
}
