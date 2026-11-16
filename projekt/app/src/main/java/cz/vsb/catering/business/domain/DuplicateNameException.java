package cz.vsb.catering.business.domain;

/** A folder already contains a node with this name (UC02 6.1, 8.1); carries a free name to offer. */
public class DuplicateNameException extends ValidationException {
    private final String suggestedName;

    public DuplicateNameException(String name, String suggestedName) {
        super("Položka s názvem „" + name + "“ ve složce již existuje.");
        this.suggestedName = suggestedName;
    }

    public String getSuggestedName() { return suggestedName; }
}
