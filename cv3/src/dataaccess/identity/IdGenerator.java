package dataaccess.identity;

import java.util.UUID;

/**
 * Identity Field: application-generated identifiers (the CV06 "UUID" option).
 * The ID is assigned before the entity is saved and never changes afterwards.
 */
public final class IdGenerator {
    private IdGenerator() {}

    public static String assign(String requestedId, String prefix) {
        if (requestedId != null && !requestedId.isBlank()) {
            return requestedId;
        }
        return prefix + "-" + UUID.randomUUID();
    }
}
