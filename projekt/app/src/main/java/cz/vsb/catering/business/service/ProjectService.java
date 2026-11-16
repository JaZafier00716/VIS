package cz.vsb.catering.business.service;

import cz.vsb.catering.business.domain.AccessPolicy;
import cz.vsb.catering.business.domain.Customer;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.port.CustomerMapper;
import cz.vsb.catering.business.port.ProjectMapper;
import cz.vsb.catering.business.port.ProjectNodeMapper;
import cz.vsb.catering.business.port.TransactionRunner;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Facade for projects (UC02 steps 1-4, UC03 edit and archive, UC04 search) and their content tree. */
public class ProjectService {
    private final ProjectMapper projects;
    private final CustomerMapper customers;
    private final ProjectNodeMapper nodes;
    private final TransactionRunner tx;
    private final Clock clock;

    public ProjectService(ProjectMapper projects, CustomerMapper customers, ProjectNodeMapper nodes,
                          TransactionRunner tx, Clock clock) {
        this.projects = projects;
        this.customers = customers;
        this.nodes = nodes;
        this.tx = tx;
        this.clock = clock;
    }

    public List<Customer> findAllCustomers() { return tx.inTransaction(customers::findAll); }

    public List<Project> findAllProjects() { return tx.inTransaction(projects::findAll); }

    /** UC02 step 4: project IN_PROGRESS with a root folder; the logged-in worker is responsible. */
    public Project createProject(String name, Long customerId, LocalDate eventDate, String eventPlace,
                                 Integer guestCount, User worker) {
        AccessPolicy.requireWorker(worker);
        return tx.inTransaction(() -> {
            Customer customer = customerId == null ? null : customers.findById(customerId).orElse(null);
            Project project = Project.create(name, customer, eventDate, eventPlace, guestCount,
                    worker.getId(), LocalDate.now(clock));
            projects.insert(project);
            Folder root = project.getRootFolder();
            root.setProjectId(project.getId());
            nodes.insert(root);
            return project;
        });
    }

    /** UC03: changes the event data of a stored project; returns the updated project. */
    public Project updateProject(long projectId, String name, LocalDate eventDate, String eventPlace,
                                 Integer guestCount, String note) {
        return tx.inTransaction(() -> {
            Project project = load(projectId);
            project.update(name, eventDate, eventPlace, guestCount, note, LocalDate.now(clock));
            projects.update(project);
            return project;
        });
    }

    /** UC03: the project becomes ARCHIVED and read-only. */
    public Project archiveProject(long projectId) {
        return tx.inTransaction(() -> {
            Project project = load(projectId);
            project.archive();
            projects.update(project);
            return project;
        });
    }

    /** UC03: the archived project becomes IN_PROGRESS again (menus, documents and comments are writable); worker only. */
    public Project reactivateProject(long projectId, User worker) {
        AccessPolicy.requireWorker(worker);
        return tx.inTransaction(() -> {
            Project project = load(projectId);
            project.reactivate();
            projects.update(project);
            return project;
        });
    }

    /** UC04: the business layer decides the criteria (blank text = any, the range must be ordered,
     *  archived projects only on request); the mapper only runs the query. */
    public List<Project> searchProjects(String text, LocalDate from, LocalDate to, boolean includeArchived) {
        if (from != null && to != null && from.isAfter(to)) {
            String msg = "Datum „od“ musí být před datem „do“.";
            throw new ValidationException(msg, Map.of("to", msg));
        }
        String criterion = text == null || text.isBlank() ? null : text.trim();
        return tx.inTransaction(() -> projects.search(criterion, from, to, includeArchived));
    }

    /** A-17 (UC15): projects of the workers who report to the manager, optionally filtered like UC04 (archived too). */
    public List<Project> searchManagedProjects(User manager, String text) {
        AccessPolicy.requireManager(manager);
        Set<Long> managed = managedIds(manager);
        return searchProjects(text, null, null, true).stream().filter(p -> managed.contains(p.getId())).toList();
    }

    /** A-17 (UC15): the project only if its responsible worker reports to the manager, else empty (the web answers 404). */
    public Optional<Project> findManagedProject(User manager, long projectId) {
        AccessPolicy.requireManager(manager);
        return tx.inTransaction(() -> projects.findBySupervisor(manager.getId())).stream()
                .filter(p -> p.getId() == projectId).findFirst();
    }

    private Set<Long> managedIds(User manager) {
        return tx.inTransaction(() -> projects.findBySupervisor(manager.getId())).stream()
                .map(Project::getId).collect(Collectors.toSet());
    }

    private Project load(long projectId) {
        return projects.findById(projectId).orElseThrow(() -> new ValidationException("Projekt již neexistuje."));
    }

    /** Whole content tree of a project (Composite), loaded in one query. */
    public Folder loadTree(long projectId) { return tx.inTransaction(() -> nodes.loadTree(projectId)); }
}
