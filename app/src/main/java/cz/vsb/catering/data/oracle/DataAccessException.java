package cz.vsb.catering.data.oracle;

/** Unchecked wrapper of SQLException so that business code does not depend on JDBC. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) { super(message, cause); }
}
