package cz.vsb.catering.business.port;

import cz.vsb.catering.business.domain.Project;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Data Mapper for projects (with their customer; the tree is loaded by ProjectNodeMapper). */
public interface ProjectMapper {
    /** All projects ordered by event date, without the content tree. */
    List<Project> findAll();

    Optional<Project> findById(long projectId);

    /** Inserts the project row and sets its id (the root folder is inserted by ProjectNodeMapper). */
    void insert(Project project);

    /** UC03: writes name, date, place, guests, note and status (customer and responsible worker never change). */
    void update(Project project);

    /** A-17 (UC15): projects whose responsible user reports to the given supervisor (users.supervisor_id), ordered like findAll. */
    List<Project> findBySupervisor(long supervisorId);

    /** UC04: projects whose name, customer name/company or place contains text (case-insensitive),
     *  with the event date in [from, to]; null criteria are ignored. Ordered like findAll. */
    List<Project> search(String text, LocalDate from, LocalDate to, boolean includeArchived);
}
