package cz.vsb.catering.presentation.desktop;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.User;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** UC03 EditProjectDialog (own project, deleted afterwards) and UC04 ProjectSearchDialog (seed, read only). */
class EditProjectDialogTest {
    private static Properties config;
    private static AppContext ctx;
    private static User user;
    private static ScriptedPrompter ui;
    private static Project project;

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
    }

    @AfterAll
    static void cleanUp() throws Exception {
        Ui.prompter = new Prompter.Dialogs();
        if (project != null) exec("DELETE FROM projects WHERE project_id = ?", project.getId());
    }

    @Test
    void editWithFieldErrorsThenSaveThenArchive() throws Exception {
        project = ctx.projects.createProject("Firemní večírek (UI test)", 2L, LocalDate.now().plusDays(30),
                "Clarion Congress Hotel Ostrava", 80, user);
        EditProjectDialog d = Fx.call(() -> new EditProjectDialog(ctx.projects, project, user));
        String shots = System.getProperty("catering.screenshot");
        if (shots != null) Fx.run(() -> {
            d.dialog().show();
            Ui.savePng(d.dialog().getDialogPane(), Path.of(shots).resolve("desktop-edit-project.png"));
            d.dialog().close();
        });

        Fx.run(() -> {                                                            // wrong data: nothing is written
            d.guests.setText("abc");
            d.date.setValue(LocalDate.now().minusDays(1));
            assertNull(d.submit());
            assertTrue(d.guests.getStyleClass().contains("field-error"));
            assertTrue(d.date.getStyleClass().contains("field-error"));
        });
        assertEquals("80", query("SELECT guest_count FROM projects WHERE project_id = ?"));

        Fx.run(() -> {
            d.guests.setText("95");
            d.date.setValue(LocalDate.now().plusDays(31));
            d.place.setText("Dolní oblast Vítkovice");
            d.note.setText("Raut ve stoje");
            assertEquals(95, d.submit().getGuestCount());
            assertFalse(d.guests.getStyleClass().contains("field-error"));
        });
        assertEquals("95|Dolní oblast Vítkovice|Raut ve stoje|IN_PROGRESS|2",
                query("SELECT guest_count || '|' || event_place || '|' || TO_CHAR(note) || '|' || status || '|' || customer_id FROM projects WHERE project_id = ?"));

        ui.confirmAnswer = false;                                                 // Zrušit in the confirmation
        assertFalse(Fx.call(d::archive));
        assertEquals("IN_PROGRESS", query("SELECT status FROM projects WHERE project_id = ?"));
        ui.confirmAnswer = true;
        assertTrue(Fx.call(d::archive));
        assertEquals("ARCHIVED", query("SELECT status FROM projects WHERE project_id = ?"));

        Project archived = ctx.projects.searchProjects("Firemní večírek (UI test)", null, null, true).get(0);
        EditProjectDialog readOnly = Fx.call(() -> new EditProjectDialog(ctx.projects, archived, user));
        Fx.run(() -> {
            assertTrue(readOnly.name.isDisabled());
            assertTrue(readOnly.archive.isDisabled());
            assertTrue(readOnly.reactivate.isVisible(), "archived project offers Obnovit projekt");
        });
        ui.confirmAnswer = false;
        assertFalse(Fx.call(readOnly::reactivate));
        assertEquals("ARCHIVED", query("SELECT status FROM projects WHERE project_id = ?"));
        ui.confirmAnswer = true;
        assertTrue(Fx.call(readOnly::reactivate));
        assertEquals("IN_PROGRESS", query("SELECT status FROM projects WHERE project_id = ?"));
        Project restored = ctx.projects.searchProjects("Firemní večírek (UI test)", null, null, true).get(0);
        Fx.run(() -> {
            EditProjectDialog open = new EditProjectDialog(ctx.projects, restored, user);
            assertFalse(open.reactivate.isVisible());
            assertFalse(open.name.isDisabled());
        });
    }

    @Test
    void searchDialogFindsSeedProjects() {
        ProjectSearchDialog d = Fx.call(() -> new ProjectSearchDialog(ctx.projects, "svatba"));
        Fx.run(() -> {
            List<Long> ids = d.results.getItems().stream().map(Project::getId).filter(id -> id <= 10).toList();
            assertEquals(List.of(1L, 10L), ids);
            d.text.setText("Krajský úřad");
            assertEquals(0, d.search());                                          // archived project 8 is hidden
            d.archived.setSelected(true);
            assertEquals(1, d.search());
            assertEquals(8L, d.results.getItems().get(0).getId());
            d.from.setValue(LocalDate.of(2027, 1, 2));
            d.to.setValue(LocalDate.of(2027, 1, 1));
            assertEquals(-1, d.search());
            assertTrue(d.to.getStyleClass().contains("field-error"));
        });
    }

    private static String query(String sql) throws Exception {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, project.getId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static void exec(String sql, long id) throws Exception {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(config.getProperty("db.url"), config.getProperty("db.user"),
                config.getProperty("db.password"));
    }
}
