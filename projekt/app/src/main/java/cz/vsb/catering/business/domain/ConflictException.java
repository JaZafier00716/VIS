package cz.vsb.catering.business.domain;

/** Someone else changed the data in the meantime (UC08 7.1, UC13 4.1). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
