package cz.vsb.catering.data;

import cz.vsb.catering.business.port.CustomerMapper;
import cz.vsb.catering.business.port.DocumentStorage;
import cz.vsb.catering.business.port.MenuDocumentRenderer;
import cz.vsb.catering.business.port.MenuMapper;
import cz.vsb.catering.business.port.ProductMapper;
import cz.vsb.catering.business.port.ProjectMapper;
import cz.vsb.catering.business.port.ProjectNodeMapper;
import cz.vsb.catering.business.port.ShareLinkMapper;
import cz.vsb.catering.business.port.TransactionRunner;
import cz.vsb.catering.business.port.UserMapper;
import cz.vsb.catering.data.file.FileDocumentStorage;
import cz.vsb.catering.data.file.PdfMenuRenderer;
import cz.vsb.catering.data.oracle.Database;
import cz.vsb.catering.data.oracle.OracleCustomerMapper;
import cz.vsb.catering.data.oracle.OracleMenuMapper;
import cz.vsb.catering.data.oracle.OracleProductMapper;
import cz.vsb.catering.data.oracle.OracleProjectMapper;
import cz.vsb.catering.data.oracle.OracleProjectNodeMapper;
import cz.vsb.catering.data.oracle.OracleShareLinkMapper;
import cz.vsb.catering.data.oracle.OracleUserMapper;
import java.nio.file.Path;
import java.util.Properties;

/** Factory: creates the data layer from the configuration; the rest of the application
 *  sees only the interfaces defined in business.port. */
public class DataAccessFactory {
    private final Properties config;
    private final Database database;

    public DataAccessFactory(Properties config) {
        this.config = config;
        this.database = Database.init(required("db.url"), required("db.user"), required("db.password"));
    }

    public TransactionRunner transactions() { return database; }
    public UserMapper userMapper() { return new OracleUserMapper(); }
    public CustomerMapper customerMapper() { return new OracleCustomerMapper(); }
    public ProjectMapper projectMapper() { return new OracleProjectMapper(); }
    public ProjectNodeMapper projectNodeMapper() { return new OracleProjectNodeMapper(); }
    public MenuMapper menuMapper() { return new OracleMenuMapper(); }
    public ShareLinkMapper shareLinkMapper() { return new OracleShareLinkMapper(); }
    public ProductMapper productMapper() { return new OracleProductMapper(); }

    public MenuDocumentRenderer menuDocumentRenderer() { return new PdfMenuRenderer(); }

    public DocumentStorage documentStorage() {
        return new FileDocumentStorage(Path.of(config.getProperty("documents.dir", "./documents")));
    }

    private String required(String key) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("V config/db.properties chybí hodnota " + key + ".");
        return value;
    }
}
