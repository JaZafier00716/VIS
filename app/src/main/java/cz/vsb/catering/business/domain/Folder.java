package cz.vsb.catering.business.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Composite: a folder contains folders, documents and menus; names are unique inside one folder. */
public class Folder extends ProjectNode {
    private final List<ProjectNode> children = new ArrayList<>();

    public Folder(String name) { super(name); }

    @Override public NodeType type() { return NodeType.FOLDER; }

    @Override public boolean isDeletable() { return children.isEmpty(); }

    @Override public int countDocuments() {
        return children.stream().mapToInt(ProjectNode::countDocuments).sum();
    }

    /** Adds a child; rejects a duplicate name and offers a free one (UC02 6.1, 8.1). */
    public void add(ProjectNode child) {
        if (containsName(child.getName()))
            throw new DuplicateNameException(child.getName(), suggestFreeName(child.getName()));
        child.setParent(this);
        child.setProjectId(getProjectId());
        children.add(child);
    }

    public void remove(ProjectNode child) {
        if (children.remove(child)) child.setParent(null);
    }

    public boolean containsName(String name) {
        return children.stream().anyMatch(c -> c.getName().equalsIgnoreCase(name.trim()));
    }

    /** "Svatební menu" -> "Svatební menu (2)", "(3)" ... first name not used in this folder. */
    public String suggestFreeName(String name) {
        String base = name.trim();
        String extension = "";
        int dot = base.lastIndexOf('.');
        if (dot > 0) { extension = base.substring(dot); base = base.substring(0, dot); }
        for (int i = 2; ; i++) {
            String candidate = base + " (" + i + ")" + extension;
            if (!containsName(candidate)) return candidate;
        }
    }

    public List<ProjectNode> getChildren() { return Collections.unmodifiableList(children); }

    public List<Folder> getSubfolders() {
        return children.stream().filter(c -> c instanceof Folder).map(c -> (Folder) c).toList();
    }

    /** Depth-first search for a node with this id in the subtree. */
    public Optional<ProjectNode> find(long nodeId) {
        if (getId() != null && getId() == nodeId) return Optional.of(this);
        for (ProjectNode child : children) {
            if (child.getId() != null && child.getId() == nodeId) return Optional.of(child);
            if (child instanceof Folder f) {
                Optional<ProjectNode> found = f.find(nodeId);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }
}
