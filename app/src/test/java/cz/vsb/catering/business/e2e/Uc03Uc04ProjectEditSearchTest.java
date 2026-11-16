package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectStatus;
import cz.vsb.catering.business.domain.ValidationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** UC03 edit + archive (own project, checked with JDBC) and UC04 search on the seed (read only). */
class Uc03Uc04ProjectEditSearchTest extends E2eBaseTest {

    @Test
    void uc03UpdateWritesEventDataAndKeepsCustomerAndResponsible() {
        Project p = newProject("E2E UC03 Úprava");
        LocalDate date = LocalDate.now().plusDays(45);
        projects.updateProject(p.getId(), "E2E UC03 Upraveno", date, "Opava", 55, "Bezlepková varianta");

        Map<String, Object> row = row("SELECT p.*, TO_CHAR(event_date, 'YYYY-MM-DD') AS day FROM projects p WHERE project_id = ?",
                p.getId());
        assertEquals("E2E UC03 Upraveno", row.get("NAME"));
        assertEquals(date.toString(), row.get("DAY"));
        assertEquals("Opava", row.get("EVENT_PLACE"));
        assertEquals(55L, row.get("GUEST_COUNT"));
        assertEquals("IN_PROGRESS", row.get("STATUS"));
        assertEquals("Bezlepková varianta", projects.searchProjects("E2E UC03 Upraveno", null, null, false).get(0).getNote());
        assertEquals(1L, row.get("CUSTOMER_ID"));
        assertEquals(worker.getId(), row.get("RESPONSIBLE_USER_ID"));
    }

    @Test
    void uc03InvalidDataChangesNothing() {
        Project p = newProject("E2E UC03 Chyba");
        assertThrows(ValidationException.class, () ->
                projects.updateProject(p.getId(), "", LocalDate.now().minusDays(1), "Opava", 0, null));
        Map<String, Object> row = row("SELECT name, guest_count FROM projects WHERE project_id = ?", p.getId());
        assertEquals("E2E UC03 Chyba", row.get("NAME"));
        assertEquals(40L, row.get("GUEST_COUNT"));
    }

    @Test
    void uc03ArchivedProjectIsReadOnly() {
        Project p = newProject("E2E UC03 Archiv");
        assertEquals(ProjectStatus.ARCHIVED, projects.archiveProject(p.getId()).getStatus());
        assertEquals("ARCHIVED", row("SELECT status FROM projects WHERE project_id = ?", p.getId()).get("STATUS"));

        assertThrows(ValidationException.class, () -> projects.archiveProject(p.getId()));
        assertThrows(ValidationException.class, () ->
                projects.updateProject(p.getId(), "Jiný název", LocalDate.now().plusDays(30), "Ostrava", 40, null));
        assertEquals("E2E UC03 Archiv", row("SELECT name FROM projects WHERE project_id = ?", p.getId()).get("NAME"));
        // UC04: archived projects are found only on request
        assertTrue(projects.searchProjects("E2E UC03 Archiv", null, null, false).isEmpty());
        assertEquals(1, projects.searchProjects("e2e uc03 archiv", null, null, true).size());
    }

    /** Seed project ids among the hits (other tests may add their own projects meanwhile). */
    private static List<Long> seedHits(String text, LocalDate from, LocalDate to, boolean archived) {
        return projects.searchProjects(text, from, to, archived).stream().map(Project::getId).filter(id -> id <= 10).toList();
    }

    @Test
    void uc04SearchByNameCustomerPlaceAndDateOnSeed() {
        assertEquals(List.of(1L, 10L), seedHits("SVATBA", null, null, false));              // name, case-insensitive
        assertEquals(List.of(10L), seedHits("sedláčková", null, null, false));              // diacritics
        assertEquals(List.of(2L), seedHits("abc logistics", null, null, false));            // customer company
        assertEquals(List.of(4L), seedHits("Procházková", null, null, false));              // customer name
        assertEquals(List.of(4L, 9L), seedHits("poruba", null, null, false));               // place
        assertEquals(List.of(4L), seedHits(null, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20), false));
        assertEquals(List.of(6L, 2L), seedHits(null, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), false));
        assertEquals(List.of(1L), seedHits("svatba", null, LocalDate.of(2027, 7, 1), false));
    }

    @Test
    void uc04MissesArchivedAndWildcards() {
        assertEquals(List.of(), seedHits("Krajský úřad", null, null, false));             // project 8 is ARCHIVED
        assertEquals(List.of(8L), seedHits("Krajský úřad", null, null, true));
        assertEquals(List.of(), seedHits("%", null, null, true));                          // LIKE wildcards are escaped
        assertEquals(List.of(), seedHits("_", null, null, true));
        assertEquals(List.of(), seedHits("neexistující akce", null, null, true));
        assertThrows(ValidationException.class,
                () -> projects.searchProjects(null, LocalDate.of(2027, 1, 2), LocalDate.of(2027, 1, 1), false));
    }
}
