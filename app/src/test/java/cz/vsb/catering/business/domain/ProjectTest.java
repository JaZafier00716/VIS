package cz.vsb.catering.business.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private final Customer customer = new Customer("Jana Nováková", "ABC s.r.o.", null, null, null);

    @Test void createsInProgressProjectWithRootFolder() {
        Project p = Project.create(" Svatba ", customer, TODAY, " Ostrava ", 80, 3L, TODAY);
        assertEquals("Svatba", p.getName());
        assertEquals("Ostrava", p.getEventPlace());
        assertEquals(ProjectStatus.IN_PROGRESS, p.getStatus());
        assertEquals(3L, p.getResponsibleUserId());
        assertEquals("Svatba", p.getRootFolder().getName());
        assertTrue(p.getRootFolder().getChildren().isEmpty());
        assertTrue(p.isNew());
    }

    @Test void reportsAllFieldErrors() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> Project.create(" ", null, null, "", 0, 1L, TODAY));
        assertEquals(Set.of("name", "customer", "eventDate", "eventPlace", "guestCount"), e.getFieldErrors().keySet());
        e = assertThrows(ValidationException.class, () -> Project.create("x".repeat(121), customer, TODAY, "A", null, 1L, TODAY));
        assertEquals(Set.of("name", "guestCount"), e.getFieldErrors().keySet());
    }

    @Test void rejectsDateInPast() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> Project.create("A", customer, TODAY.minusDays(1), "B", 10, 1L, TODAY));
        assertEquals(Set.of("eventDate"), e.getFieldErrors().keySet());
    }

    @Test void customerDisplayName() {
        assertEquals("Jana Nováková (ABC s.r.o.)", customer.displayName());
        assertEquals("Petr", new Customer("Petr", " ", null, null, null).displayName());
    }

    @Test void reactivateTurnsArchivedBackToInProgressAndRefusesOthers() {
        Project p = Project.create("Svatba", customer, TODAY, "Ostrava", 80, 3L, TODAY);
        assertEquals("Projekt není archivovaný, není co obnovovat.", assertThrows(ValidationException.class, p::reactivate).getMessage());
        p.archive();
        p.reactivate();
        assertEquals(ProjectStatus.IN_PROGRESS, p.getStatus());
        assertDoesNotThrow(p::requireNotArchived);
    }
}
