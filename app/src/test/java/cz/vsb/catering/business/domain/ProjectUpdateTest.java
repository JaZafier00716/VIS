package cz.vsb.catering.business.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** UC03: editing and archiving a project. */
class ProjectUpdateTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private final Customer customer = new Customer("Jana Nováková", null, null, null, null);

    private Project project(LocalDate date) {
        return new Project(customer, 3L, "Svatba", date, "Ostrava", 80, ProjectStatus.CONFIRMED, null);
    }

    @Test void updatesTrimmedDataAndKeepsCustomerResponsibleAndStatus() {
        Project p = project(TODAY.plusDays(10));
        p.update(" Svatba II ", TODAY.plusDays(20), " Opava ", 120, "  bez lepku ", TODAY);
        assertEquals("Svatba II", p.getName());
        assertEquals(TODAY.plusDays(20), p.getEventDate());
        assertEquals("Opava", p.getEventPlace());
        assertEquals(120, p.getGuestCount());
        assertEquals("bez lepku", p.getNote());
        assertSame(customer, p.getCustomer());
        assertEquals(3L, p.getResponsibleUserId());
        assertEquals(ProjectStatus.CONFIRMED, p.getStatus());
        p.update("Svatba II", TODAY.plusDays(20), "Opava", 120, " ", TODAY);
        assertNull(p.getNote());
    }

    @Test void reportsFieldErrorsAndChangesNothing() {
        Project p = project(TODAY.plusDays(10));
        ValidationException e = assertThrows(ValidationException.class,
                () -> p.update(" ", null, "", 0, "x".repeat(Project.MAX_NOTE + 1), TODAY));
        assertEquals(Set.of("name", "eventDate", "eventPlace", "guestCount", "note"), e.getFieldErrors().keySet());
        assertEquals("Svatba", p.getName());
        assertEquals(80, p.getGuestCount());
    }

    @Test void newDateMustNotBeInPastButUnchangedPastDateIsAccepted() {
        Project future = project(TODAY.plusDays(10));
        ValidationException e = assertThrows(ValidationException.class,
                () -> future.update("A", TODAY.minusDays(1), "B", 10, null, TODAY));
        assertEquals(Set.of("eventDate"), e.getFieldErrors().keySet());

        Project past = project(TODAY.minusDays(5));                 // event is over, only the guest count is fixed
        past.update("Svatba", TODAY.minusDays(5), "Ostrava", 78, null, TODAY);
        assertEquals(78, past.getGuestCount());
        assertThrows(ValidationException.class, () -> past.update("Svatba", TODAY.minusDays(6), "Ostrava", 78, null, TODAY));
    }

    @Test void archivedProjectIsReadOnly() {
        Project p = project(TODAY.minusDays(5));
        assertFalse(p.isArchived());
        p.archive();
        assertTrue(p.isArchived());
        assertEquals(ProjectStatus.ARCHIVED, p.getStatus());
        assertThrows(ValidationException.class, p::archive);
        assertThrows(ValidationException.class, () -> p.update("Nový", TODAY.minusDays(5), "Ostrava", 80, null, TODAY));
        assertEquals("Svatba", p.getName());
    }
}
