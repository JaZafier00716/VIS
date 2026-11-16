package cz.vsb.catering.business.domain;

public enum ProjectStatus {
    IN_PROGRESS("Rozpracovaný"), CONFIRMED("Potvrzený"), DONE("Dokončený"), ARCHIVED("Archivovaný");

    private final String label;

    ProjectStatus(String label) { this.label = label; }

    public String label() { return label; }
}
