package cz.vsb.catering.business.fake;

import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.ProjectNode;
import cz.vsb.catering.business.port.ProjectNodeMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keeps the tree objects themselves; loadTree returns the root registered for the project. */
public class FakeProjectNodeMapper implements ProjectNodeMapper {
    public final List<ProjectNode> inserted = new ArrayList<>();
    public final List<ProjectNode> deleted = new ArrayList<>();
    public final Map<Long, Folder> trees = new HashMap<>();
    /** When set, insert throws it (simulates a database error). */
    public RuntimeException failOnInsert;
    private long nextId = 1000;

    @Override public Folder loadTree(long projectId) {
        Folder root = trees.get(projectId);
        if (root == null) throw new IllegalStateException("no tree for project " + projectId);
        return root;
    }

    @Override public void insert(ProjectNode node) {
        if (failOnInsert != null) throw failOnInsert;
        node.setId(nextId++);
        if (node.getParent() == null && node instanceof Folder root) trees.put(node.getProjectId(), root);
        inserted.add(node);
    }

    @Override public void delete(ProjectNode node) { deleted.add(node); }
}
