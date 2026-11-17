package dataaccess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Version of the stored data layout. Old data (e.g. customers with a single {@code name}, before CV07) cannot be
 * read by this version, so both storages refuse to open it with one clear message instead of failing later.
 */
public final class SchemaVersion {
    /**
     * 2 = CV07 phase 1 (one customers table with customer_type), 3 = CV07 inheritance mapping
     * (customers + person_customers + company_customers for CTI, customers_sti for STI, one store per strategy).
     */
    public static final int CURRENT = 3;
    public static final String OUTDATED_MESSAGE = "Data jsou ze starší verze, smažte složku data/ (kromě .gitkeep)";

    private SchemaVersion() {}

    /**
     * Before version 3 there was one store per data directory: {@code vis-h2.mv.db} and the JSON files directly
     * in it. Since version 3 each inheritance strategy has its own store ({@code vis-h2-cti}, {@code json-cti/},
     * …), so files of the old layout would simply be ignored; refuse them instead.
     */
    public static void refuseOldLayout(Path dataDirectory) {
        if (Files.exists(dataDirectory.resolve("vis-h2.mv.db"))) {
            throw outdated(dataDirectory.resolve("vis-h2.mv.db").toString());
        }
        if (Files.isDirectory(dataDirectory)) {
            try (DirectoryStream<Path> json = Files.newDirectoryStream(dataDirectory, "*.json")) {
                if (json.iterator().hasNext()) {
                    throw outdated("JSON soubory přímo v " + dataDirectory);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Složku " + dataDirectory + " nelze přečíst.", e);
            }
        }
    }

    public static IllegalStateException outdated(String where) {
        return new IllegalStateException(OUTDATED_MESSAGE + " [" + where + "]");
    }
}
