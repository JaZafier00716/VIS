package cz.vsb.catering.business.domain;

import java.time.LocalDateTime;

/** Composite: common type of Folder (composite), Document and Menu (leaves).
 *  Stored by Single Table Inheritance in project_nodes (node_type). */
public abstract class ProjectNode extends DomainObject {
    private Long projectId;
    private String name;
    private Folder parent;
    private LocalDateTime createdAt;

    protected ProjectNode(String name) { this.name = name; }

    /** Discriminator value stored in project_nodes.node_type. */
    public abstract NodeType type();

    /** UC19: may this node be deleted? Folders only when empty; leaves answer themselves. */
    public abstract boolean isDeletable();

    /** Number of documents in this subtree (used by the tree labels and tests). */
    public abstract int countDocuments();

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public Folder getParent() { return parent; }
    void setParent(Folder parent) { this.parent = parent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Path from the root, e.g. "Svatba / Podklady / Fotky". */
    public String path() {
        return parent == null ? name : parent.path() + " / " + name;
    }

    @Override public String toString() { return name; }
}
