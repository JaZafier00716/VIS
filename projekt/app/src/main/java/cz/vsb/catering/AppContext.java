package cz.vsb.catering;

import cz.vsb.catering.business.service.AuthService;
import cz.vsb.catering.business.service.CatalogService;
import cz.vsb.catering.business.service.CustomerService;
import cz.vsb.catering.business.service.DocumentService;
import cz.vsb.catering.business.service.MenuExportService;
import cz.vsb.catering.business.service.MenuService;
import cz.vsb.catering.business.service.ProjectService;
import cz.vsb.catering.business.service.SharingService;
import cz.vsb.catering.business.service.UserService;
import cz.vsb.catering.data.DataAccessFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Properties;

/** Composition root: reads config/db.properties, builds the data layer through the factory and
 *  wires the service facades. Both UIs get their services only from here. */
public final class AppContext {
    public final AuthService auth;
    public final ProjectService projects;
    public final DocumentService documents;
    public final MenuService menus;
    public final MenuExportService menuExport;
    public final SharingService sharing;
    public final UserService users;
    public final CustomerService customers;
    public final CatalogService catalog;
    public final int webPort;
    public final String baseUrl;
    /** A-55: true = the desktop app starts the web server and the link scheduler itself (dev/demo). */
    public final boolean webEmbedded;

    private AppContext(Properties config) {
        DataAccessFactory data = new DataAccessFactory(config);
        Clock clock = Clock.systemDefaultZone();
        var tx = data.transactions();
        auth = new AuthService(data.userMapper(), tx);
        projects = new ProjectService(data.projectMapper(), data.customerMapper(), data.projectNodeMapper(), tx, clock);
        documents = new DocumentService(data.projectNodeMapper(), data.projectMapper(), data.documentStorage(), tx);
        menus = new MenuService(data.menuMapper(), data.projectMapper(), data.projectNodeMapper(), data.productMapper(), tx, clock);
        menuExport = new MenuExportService(menus, documents, data.menuDocumentRenderer());
        baseUrl = config.getProperty("web.baseUrl", "http://localhost:8080");
        sharing = new SharingService(data.menuMapper(), data.projectMapper(), data.shareLinkMapper(), tx, clock, baseUrl);
        users = new UserService(data.userMapper(), tx);
        customers = new CustomerService(data.customerMapper(), tx);
        catalog = new CatalogService(data.productMapper(), tx);
        webEmbedded = webEmbedded(config);
        webPort = Integer.parseInt(config.getProperty("web.port", "8080"));
    }

    static boolean webEmbedded(Properties config) {
        return Boolean.parseBoolean(config.getProperty("web.embedded", "false").trim());
    }

    /** Loads config/db.properties (or the file in -Dcatering.config). */
    public static AppContext load() {
        Path file = Path.of(System.getProperty("catering.config", "config/db.properties"));
        Properties config = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            config.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Nelze načíst konfiguraci " + file.toAbsolutePath()
                    + " (zkopírujte config/db.properties.example).", e);
        }
        return new AppContext(config);
    }
}
