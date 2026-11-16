package cz.vsb.catering.business.service;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectStatus;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeCustomerMapper;
import cz.vsb.catering.business.fake.FakeProjectMapper;
import cz.vsb.catering.business.fake.FakeProjectNodeMapper;
import cz.vsb.catering.business.fake.Fixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** UC03 update/archive and UC04 search criteria of ProjectService on in-memory mappers. */
class ProjectEditSearchServiceTest {
    private final FakeProjectMapper projects = new FakeProjectMapper();
    private final FakeCustomerMapper customers = new FakeCustomerMapper();
    private final ProjectService service = new ProjectService(projects, customers, new FakeProjectNodeMapper(),
            new DirectTransactionRunner(), Fixtures.CLOCK);
    private final User worker = Fixtures.user("ehorakova", Role.WORKER);
    private final LocalDate today = LocalDate.of(2026, 11, 13);

    { worker.setId(3L); customers.add(11, "Jana Nováková"); customers.add(12, "Pavel Dostál"); }

    @Test void updateProjectWritesTheChangedProject() {
        Project p = service.createProject("Svatba", 11L, today.plusDays(5), "Ostrava", 80, worker);
        Project changed = service.updateProject(p.getId(), "Svatba Novákovi", today.plusDays(6), "Opava", 90, "pozn.");
        assertEquals(List.of(changed), projects.updated);
        assertEquals("Svatba Novákovi", changed.getName());
        assertEquals(90, changed.getGuestCount());
        assertEquals(11L, changed.getCustomer().getId());
        assertEquals(3L, changed.getResponsibleUserId());
    }

    @Test void invalidUpdateAndUnknownProjectWriteNothing() {
        Project p = service.createProject("Svatba", 11L, today.plusDays(5), "Ostrava", 80, worker);
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.updateProject(p.getId(), "Svatba", today.minusDays(1), "Ostrava", -1, null));
        assertEquals(Set.of("eventDate", "guestCount"), e.getFieldErrors().keySet());
        assertThrows(ValidationException.class, () -> service.archiveProject(999));
        assertTrue(projects.updated.isEmpty());
    }

    @Test void archiveProjectThenNoMoreEdits() {
        Project p = service.createProject("Svatba", 11L, today.plusDays(5), "Ostrava", 80, worker);
        assertEquals(ProjectStatus.ARCHIVED, service.archiveProject(p.getId()).getStatus());
        assertEquals(1, projects.updated.size());
        assertThrows(ValidationException.class, () -> service.archiveProject(p.getId()));
        assertThrows(ValidationException.class,
                () -> service.updateProject(p.getId(), "X", today.plusDays(5), "Ostrava", 80, null));
        assertEquals(1, projects.updated.size());
    }

    @Test void searchNormalisesCriteriaAndHidesArchivedByDefault() {
        Project wedding = service.createProject("Svatba", 11L, today.plusDays(5), "Ostrava", 80, worker);
        Project party = service.createProject("Večírek", 12L, today.plusDays(20), "Opava", 30, worker);
        Project old = service.createProject("Ples", 12L, today.plusDays(40), "Ostrava", 300, worker);
        service.archiveProject(old.getId());

        assertEquals(List.of(wedding, party), service.searchProjects("  ", null, null, false));
        assertEquals(List.of(wedding, party, old), service.searchProjects(null, null, null, true));
        assertEquals(List.of(wedding), service.searchProjects(" ostr ", null, null, false));     // place, trimmed
        assertEquals(List.of(party), service.searchProjects("DOSTÁL", null, null, false));       // customer
        assertEquals(List.of(party, old), service.searchProjects("dostál", null, null, true));
        assertEquals(List.of(party), service.searchProjects(null, today.plusDays(6), today.plusDays(20), false));
        assertEquals(List.of(), service.searchProjects("nic takového", null, null, true));
    }

    @Test void searchRejectsReversedDateRange() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.searchProjects(null, today.plusDays(2), today, false));
        assertEquals(Set.of("to"), e.getFieldErrors().keySet());
    }
}
