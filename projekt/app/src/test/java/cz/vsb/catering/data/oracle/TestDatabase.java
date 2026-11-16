package cz.vsb.catering.data.oracle;

import cz.vsb.catering.business.domain.Customer;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Project;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Properties;

/** Opens the local test database from config/db.properties and creates a throw-away project. */
final class TestDatabase {
    private TestDatabase() {}

    static Database init() {
        Properties config = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("config/db.properties"))) {
            config.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Database.init(config.getProperty("db.url"), config.getProperty("db.user"), config.getProperty("db.password"));
    }

    /** New project (customer 1, user 1) with its stored root folder; delete it by deleteProject. */
    static Project createProject(String name) {
        Customer customer = new OracleCustomerMapper().findById(1).orElseThrow();
        Project project = Project.create(name, customer, LocalDate.now().plusDays(30), "Ostrava", 40, 1, LocalDate.now());
        new OracleProjectMapper().insert(project);
        Folder root = project.getRootFolder();
        root.setProjectId(project.getId());
        new OracleProjectNodeMapper().insert(root);
        return project;
    }

    static void deleteProject(long projectId) {
        Database.getInstance().inTransaction(() -> Jdbc.update("DELETE FROM projects WHERE project_id = ?", projectId));
    }
}
