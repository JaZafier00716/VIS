package cz.vsb.catering.business.domain;

public enum RevisionState {
    WORKING("PRACOVNÍ"), SHARED("SDÍLENÁ");

    private final String label;

    RevisionState(String label) { this.label = label; }

    public String label() { return label; }
}
