package cz.vsb.catering.business.domain;

public enum NodeType {
    FOLDER("Složka"), DOCUMENT("Dokument"), MENU("Menu");

    private final String label;

    NodeType(String label) { this.label = label; }

    public String label() { return label; }
}
