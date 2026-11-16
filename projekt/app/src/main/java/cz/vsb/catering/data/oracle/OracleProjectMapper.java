package cz.vsb.catering.data.oracle;

import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectStatus;
import cz.vsb.catering.business.port.ProjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Data Mapper for projects; each project is read together with its customer. */
public class OracleProjectMapper implements ProjectMapper {
    private static final String SELECT = """
            SELECT p.project_id, p.responsible_user_id, p.name AS project_name, p.event_date, p.event_place,
                   p.guest_count, p.status, p.note,
                   c.customer_id, c.name, c.company_name, c.email, c.phone, c.city
            FROM projects p JOIN customers c ON c.customer_id = p.customer_id
            """;

    @Override
    public List<Project> findAll() {
        return Jdbc.query(SELECT + "ORDER BY p.event_date, p.project_id", OracleProjectMapper::read);
    }

    @Override
    public List<Project> findBySupervisor(long supervisorId) {
        return Jdbc.query(SELECT + """
                JOIN users w ON w.user_id = p.responsible_user_id
                WHERE w.supervisor_id = ?
                ORDER BY p.event_date, p.project_id""", OracleProjectMapper::read, supervisorId);
    }

    @Override
    public Optional<Project> findById(long projectId) {
        return Jdbc.queryOne(SELECT + "WHERE p.project_id = ?", OracleProjectMapper::read, projectId);
    }

    @Override
    public void insert(Project project) {
        long id = Jdbc.insert("""
                INSERT INTO projects (customer_id, responsible_user_id, name, event_date, event_place,
                                      guest_count, status, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", "PROJECT_ID",
                project.getCustomer().getId(), project.getResponsibleUserId(), project.getName(),
                project.getEventDate(), project.getEventPlace(), project.getGuestCount(),
                project.getStatus().name(), project.getNote());
        project.setId(id);
    }

    @Override
    public void update(Project project) {
        Jdbc.update("""
                UPDATE projects SET name = ?, event_date = ?, event_place = ?, guest_count = ?, note = ?, status = ?
                WHERE project_id = ?""",
                project.getName(), project.getEventDate(), project.getEventPlace(), project.getGuestCount(),
                project.getNote(), project.getStatus().name(), project.getId());
    }

    @Override
    public List<Project> search(String text, LocalDate from, LocalDate to, boolean includeArchived) {
        String like = text == null ? null
                : "%" + text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return Jdbc.query(SELECT + """
                WHERE (? IS NULL OR LOWER(p.name) LIKE ? ESCAPE '\\' OR LOWER(c.name) LIKE ? ESCAPE '\\'
                       OR LOWER(c.company_name) LIKE ? ESCAPE '\\' OR LOWER(p.event_place) LIKE ? ESCAPE '\\')
                  AND (? IS NULL OR p.event_date >= ?)
                  AND (? IS NULL OR p.event_date < CAST(? AS DATE) + 1)
                  AND (? = 'Y' OR p.status <> 'ARCHIVED')
                ORDER BY p.event_date, p.project_id""", OracleProjectMapper::read,
                like, like, like, like, like, from, from, to, to, Jdbc.yesNo(includeArchived));
    }

    private static Project read(ResultSet rs) throws SQLException {
        Project p = new Project(OracleCustomerMapper.read(rs), rs.getLong("responsible_user_id"),
                rs.getString("project_name"), rs.getDate("event_date").toLocalDate(), rs.getString("event_place"),
                rs.getInt("guest_count"), ProjectStatus.valueOf(rs.getString("status")), rs.getString("note"));
        p.setId(rs.getLong("project_id"));
        return p;
    }
}
