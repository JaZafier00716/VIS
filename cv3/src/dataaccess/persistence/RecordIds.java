package dataaccess.persistence;

/** Shared ID checks and error messages for all gateways (JSON and H2). */
public final class RecordIds {
    private RecordIds() {}

    public static String require(String id, String entity) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(entity + " musí mít ID.");
        }
        return id;
    }

    public static IllegalArgumentException missing(String entity, String id) {
        return new IllegalArgumentException(entity + " " + id + " neexistuje.");
    }

    public static IllegalArgumentException duplicate(String entity, String id) {
        return new IllegalArgumentException(entity + " " + id + " už existuje.");
    }
}
