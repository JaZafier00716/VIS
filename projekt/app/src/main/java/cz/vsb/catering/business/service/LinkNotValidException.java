package cz.vsb.catering.business.service;

/** The share link expired or was replaced while the customer was writing a comment (UC11 note after 7.3). */
public class LinkNotValidException extends RuntimeException {
    public LinkNotValidException() { super("Odkaz již není platný."); }
}
