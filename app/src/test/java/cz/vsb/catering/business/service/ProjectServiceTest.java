package cz.vsb.catering.business.service;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Folder;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectServiceTest {
    private final FakeProjectMapper projects = new FakeProjectMapper();
    private final FakeCustomerMapper customers = new FakeCustomerMapper();
    private final FakeProjectNodeMapper nodes = new FakeProjectNodeMapper();
    private final ProjectService service = new ProjectService(projects, customers, nodes, new DirectTransactionRunner(), Fixtures.CLOCK);
    private final User worker = Fixtures.user("ehorakova", Role.WORKER);
    private final LocalDate today = LocalDate.of(2026, 11, 13);

    { worker.setId(3L); customers.add(11, "Jana Nováková"); }

    @Test void createProjectInsertsProjectAndRootFolder() {
        Project p = service.createProject("Svatba", 11L, today, "Ostrava", 80, worker);
        assertNotNull(p.getId());
        assertEquals(ProjectStatus.IN_PROGRESS, p.getStatus());
        assertEquals(3L, p.getResponsibleUserId());
        assertEquals("Jana Nováková", p.getCustomer().getName());
        assertEquals(1, projects.inserted.size());
        Folder root = p.getRootFolder();
        assertEquals(1, nodes.inserted.size());
        assertSame(root, nodes.inserted.get(0));
        assertEquals(p.getId(), root.getProjectId());
        assertNotNull(root.getId());
        assertSame(root, service.loadTree(p.getId()));
    }

    @Test void invalidDataInsertsNothing() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.createProject("Svatba", null, today.minusDays(1), "Ostrava", 80, worker));
        assertEquals(Set.of("customer", "eventDate"), e.getFieldErrors().keySet());
        e = assertThrows(ValidationException.class, () -> service.createProject("Svatba", 999L, today, "Ostrava", 80, worker));
        assertEquals(Set.of("customer"), e.getFieldErrors().keySet());
        assertTrue(projects.inserted.isEmpty());
        assertTrue(nodes.inserted.isEmpty());
    }

    @Test void listsCustomersAndProjects() {
        assertEquals(1, service.findAllCustomers().size());
        service.createProject("Svatba", 11L, today, "Ostrava", 80, worker);
        assertEquals(1, service.findAllProjects().size());
    }

    @Test void reactivateRestoresArchivedProjectForWorkerOnly() {
        Project p = service.createProject("Svatba", 11L, today, "Ostrava", 80, worker);
        service.archiveProject(p.getId());
        User manager = Fixtures.user("m", Role.MANAGER);
        assertThrows(ValidationException.class, () -> service.reactivateProject(p.getId(), manager));
        assertTrue(p.isArchived());
        assertEquals(ProjectStatus.IN_PROGRESS, service.reactivateProject(p.getId(), worker).getStatus());
        assertThrows(ValidationException.class, () -> service.reactivateProject(p.getId(), worker));
        assertThrows(ValidationException.class, () -> service.reactivateProject(999L, worker));
    }
}
