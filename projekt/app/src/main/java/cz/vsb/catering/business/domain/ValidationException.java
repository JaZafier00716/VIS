package cz.vsb.catering.business.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Business rule violation; fieldErrors maps a form field to a Czech message for the UI. */
public class ValidationException extends RuntimeException {
    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        this(message, Map.of());
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    /** Throws when errors is not empty; message joins all errors. */
    public static void throwIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) throw new ValidationException(String.join(" ", errors.values()), errors);
    }

    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
