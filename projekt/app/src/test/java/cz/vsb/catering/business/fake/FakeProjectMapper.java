package cz.vsb.catering.business.fake;

import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.port.ProjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FakeProjectMapper implements ProjectMapper {
    public final List<Project> inserted = new ArrayList<>();
    public final List<Project> updated = new ArrayList<>();
    /** responsible user id -> supervisor id (users.supervisor_id), set by tests. */
    public final java.util.Map<Long, Long> supervisorOf = new java.util.HashMap<>();
    private long nextId = 1;

    @Override public List<Project> findAll() { return List.copyOf(inserted); }

    @Override public Optional<Project> findById(long projectId) {
        return inserted.stream().filter(p -> p.getId() == projectId).findFirst();
    }

    @Override public void insert(Project project) {
        project.setId(nextId++);
        inserted.add(project);
    }

    @Override public List<Project> findBySupervisor(long supervisorId) {
        return inserted.stream().filter(p -> Long.valueOf(supervisorId).equals(supervisorOf.get(p.getResponsibleUserId()))).toList();
    }

    @Override public void update(Project project) { updated.add(project); }

    @Override public List<Project> search(String text, LocalDate from, LocalDate to, boolean includeArchived) {
        return inserted.stream()
                .filter(p -> text == null || contains(p.getName(), text) || contains(p.getCustomer().getName(), text)
                        || contains(p.getCustomer().getCompanyName(), text) || contains(p.getEventPlace(), text))
                .filter(p -> from == null || !p.getEventDate().isBefore(from))
                .filter(p -> to == null || !p.getEventDate().isAfter(to))
                .filter(p -> includeArchived || !p.isArchived())
                .toList();
    }

    private static boolean contains(String value, String text) {
        return value != null && value.toLowerCase().contains(text.toLowerCase());
    }
}
