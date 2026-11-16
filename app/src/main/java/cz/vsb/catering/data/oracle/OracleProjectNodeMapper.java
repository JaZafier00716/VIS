package cz.vsb.catering.data.oracle;

import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.NodeType;
import cz.vsb.catering.business.domain.ProjectNode;
import cz.vsb.catering.business.port.ProjectNodeMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Data Mapper for project_nodes, stored by Single Table Inheritance: node_type picks the subclass
 *  (Folder, Document, Menu) and the document columns are used only by DOCUMENT rows. */
public class OracleProjectNodeMapper implements ProjectNodeMapper {
    // hierarchical order guarantees that a parent row comes before its children
    private static final String TREE = """
            SELECT n.node_id, n.parent_id, n.node_type, n.name, n.created_at,
                   n.content_type, n.size_bytes, n.storage_key, m.menu_id,
                   CASE WHEN EXISTS (SELECT 1 FROM share_links s
                                     WHERE s.menu_id = m.menu_id AND s.active = 'Y'
                                       AND s.valid_until > SYSTIMESTAMP) THEN 'Y' ELSE 'N' END AS active_link
            FROM project_nodes n LEFT JOIN menus m ON m.node_id = n.node_id
            START WITH n.parent_id IS NULL AND n.project_id = ?
            CONNECT BY PRIOR n.node_id = n.parent_id
            ORDER SIBLINGS BY CASE n.node_type WHEN 'FOLDER' THEN 0 ELSE 1 END, n.name
            """;

    @Override
    public Folder loadTree(long projectId) {
        Map<Long, Folder> folders = new HashMap<>();
        Folder[] root = new Folder[1];
        Jdbc.query(TREE, rs -> {
            ProjectNode node = read(rs);
            Long parentId = Jdbc.longOrNull(rs, "parent_id");
            if (parentId == null) {
                node.setProjectId(projectId);
                root[0] = (Folder) node;
            } else {
                folders.get(parentId).add(node);
            }
            if (node instanceof Folder f) folders.put(f.getId(), f);
            return node;
        }, projectId);
        if (root[0] == null) throw new IllegalStateException("Projekt " + projectId + " nemá kořenovou složku.");
        return root[0];
    }

    @Override
    public void insert(ProjectNode node) {
        Document doc = node instanceof Document d ? d : null;
        node.setId(Jdbc.insert("""
                INSERT INTO project_nodes (project_id, parent_id, node_type, name, content_type, size_bytes, storage_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", "NODE_ID",
                node.getProjectId(), node.getParent() == null ? null : node.getParent().getId(),
                node.type().name(), node.getName(),
                doc == null ? null : doc.getContentType(), doc == null ? null : doc.getSizeBytes(),
                doc == null ? null : doc.getStorageKey()));
    }

    @Override
    public void delete(ProjectNode node) {
        Jdbc.update("DELETE FROM project_nodes WHERE node_id = ?", node.getId());
    }

    private static ProjectNode read(ResultSet rs) throws SQLException {
        String name = rs.getString("name");
        ProjectNode node = switch (NodeType.valueOf(rs.getString("node_type"))) {
            case FOLDER -> new Folder(name);
            case DOCUMENT -> new Document(name, rs.getString("content_type"), rs.getLong("size_bytes"),
                    rs.getString("storage_key"));
            case MENU -> {
                Menu menu = new Menu(name);
                menu.setMenuId(Jdbc.longOrNull(rs, "menu_id"));
                menu.setActiveShareLink("Y".equals(rs.getString("active_link")));
                yield menu;
            }
        };
        node.setId(rs.getLong("node_id"));
        node.setCreatedAt(Jdbc.dateTime(rs, "created_at"));
        return node;
    }
}
