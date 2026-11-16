package cz.vsb.catering.business.domain;

public enum Role {
    ADMIN("Administrátor"), WORKER("Pracovník"), MANAGER("Manažer");

    private final String label;

    Role(String label) { this.label = label; }

    public String label() { return label; }
}
