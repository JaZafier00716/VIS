package cz.vsb.catering.business.port;

import java.io.IOException;
import java.nio.file.Path;

/** Gateway to the second storage (document folder). Keys look like "p12/smlouva.pdf". */
public interface DocumentStorage {
    void store(String key, Path source) throws IOException;

    void retrieve(String key, Path target) throws IOException;

    void delete(String key) throws IOException;

    boolean exists(String key);
}
