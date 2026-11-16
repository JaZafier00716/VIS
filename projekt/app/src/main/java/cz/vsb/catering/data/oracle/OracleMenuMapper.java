package cz.vsb.catering.data.oracle;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.port.MenuMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Data Mapper for menus, menu_revisions and comments. Categories and items are a Dependent Mapping
 *  of their revision (written and replaced together with it). Comments are given to the revision
 *  as a Lazy Load: they are read only on the first getComments(). */
public class OracleMenuMapper implements MenuMapper {
    private static final String HEADER =
            "SELECT menu_id, revision_no, state, note, created_by, created_at FROM menu_revisions WHERE menu_id = ? ";

    private static final String CONTENT = """
            SELECT c.category_id, c.name AS category_name, i.quantity, i.unit_price, i.note AS item_note,
            """ + OracleProductMapper.COLUMNS + """
            FROM menu_categories c
            LEFT JOIN menu_items i ON i.category_id = c.category_id
            LEFT JOIN products p ON p.product_id = i.product_id
            WHERE c.menu_id = ? AND c.revision_no = ?
            ORDER BY c.position, p.name
            """;

    // item comments have no menu/revision of their own, they come from the category (ARC)
    private static final String COMMENTS = """
            SELECT k.comment_id, NVL(k.menu_id, c.menu_id) AS menu_id, NVL(k.revision_no, c.revision_no) AS revision_no,
                   k.category_id, k.product_id, p.name AS product_name, k.author_name, k.body, k.created_at,
                   k.resolved, k.resolution_note, k.resolved_by, u.full_name AS resolved_by_name, k.resolved_at
            FROM comments k
            LEFT JOIN menu_categories c ON c.category_id = k.category_id
            LEFT JOIN products p ON p.product_id = k.product_id
            LEFT JOIN users u ON u.user_id = k.resolved_by
            """;

    @Override
    public void insertMenu(Menu menu, long projectId) {
        menu.setMenuId(Jdbc.insert("INSERT INTO menus (project_id, node_id, name) VALUES (?, ?, ?)", "MENU_ID",
                projectId, menu.getId(), menu.getName()));
    }

    @Override
    public List<MenuRevision> findRevisionHeaders(long menuId) {
        return Jdbc.query(HEADER + "ORDER BY revision_no DESC", OracleMenuMapper::readHeader, menuId);
    }

    @Override
    public Optional<MenuRevision> findLatestHeader(long menuId) {
        return Jdbc.queryOne(HEADER + "ORDER BY revision_no DESC FETCH FIRST 1 ROW ONLY",
                OracleMenuMapper::readHeader, menuId);
    }

    @Override
    public Optional<MenuRevision> findRevision(long menuId, int revisionNo) {
        Optional<MenuRevision> found = Jdbc.queryOne(HEADER + "AND revision_no = ?",
                OracleMenuMapper::readHeader, menuId, revisionNo);
        found.ifPresent(revision -> {
            Map<Long, MenuCategory> categories = new LinkedHashMap<>();
            Jdbc.query(CONTENT, rs -> {
                long categoryId = rs.getLong("category_id");
                MenuCategory category = categories.get(categoryId);
                if (category == null) {
                    category = new MenuCategory(rs.getString("category_name"));
                    category.setId(categoryId);
                    categories.put(categoryId, category);
                    revision.loadCategory(category);
                }
                if (rs.getBigDecimal("quantity") != null)                // empty category: no item row
                    category.loadItem(new MenuItem(OracleProductMapper.read(rs), rs.getBigDecimal("quantity"),
                            Money.of(rs.getBigDecimal("unit_price")), rs.getString("item_note")));
                return category;
            }, menuId, revisionNo);
            revision.setCommentLoader(() -> Database.getInstance().inTransaction(() -> findComments(menuId, revisionNo)));
        });
        return found;
    }

    @Override
    public void insertRevision(MenuRevision revision) {
        Jdbc.update("""
                INSERT INTO menu_revisions (menu_id, revision_no, state, note, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
                revision.getMenuId(), revision.getRevisionNo(), revision.getState().name(), revision.getNote(),
                revision.getCreatedBy(), createdAt(revision));
        insertContent(revision);
    }

    @Override
    public boolean overwriteRevision(MenuRevision revision) {
        String sql = "UPDATE menu_revisions SET note = ?, created_by = ?, created_at = ? WHERE menu_id = ? AND revision_no = ? AND state = 'WORKING'";
        int rows = revision.getLoadedSavedAt() == null
                ? Jdbc.update(sql, revision.getNote(), revision.getCreatedBy(), createdAt(revision),
                        revision.getMenuId(), revision.getRevisionNo())
                : Jdbc.update(sql + " AND created_at = ?", revision.getNote(), revision.getCreatedBy(), createdAt(revision),
                        revision.getMenuId(), revision.getRevisionNo(), revision.getLoadedSavedAt());
        if (rows == 0) return false;
        // items (and their comments) go with the categories by ON DELETE CASCADE
        Jdbc.update("DELETE FROM menu_categories WHERE menu_id = ? AND revision_no = ?",
                revision.getMenuId(), revision.getRevisionNo());
        insertContent(revision);
        return true;
    }

    @Override
    public boolean markShared(long menuId, int revisionNo, LocalDateTime loadedSavedAt) {
        return Jdbc.update("""
                UPDATE menu_revisions SET state = 'SHARED'
                WHERE menu_id = ? AND revision_no = ? AND state = 'WORKING' AND created_at = ?""",
                menuId, revisionNo, loadedSavedAt) == 1;
    }

    @Override
    public List<Comment> findComments(long menuId, int revisionNo) {
        return Jdbc.query(COMMENTS + """
                WHERE (k.menu_id = ? AND k.revision_no = ?) OR (c.menu_id = ? AND c.revision_no = ?)
                ORDER BY k.created_at, k.comment_id""", OracleMenuMapper::readComment,
                menuId, revisionNo, menuId, revisionNo);
    }

    @Override
    public Optional<Comment> findComment(long commentId) {
        return Jdbc.queryOne(COMMENTS + "WHERE k.comment_id = ?", OracleMenuMapper::readComment, commentId);
    }

    @Override
    public void insertComment(Comment comment) {
        boolean onItem = comment.isOnItem();
        comment.setId(Jdbc.insert("""
                INSERT INTO comments (menu_id, revision_no, category_id, product_id, author_name, body, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", "COMMENT_ID",
                onItem ? null : comment.getMenuId(), onItem ? null : comment.getRevisionNo(),
                onItem ? comment.getCategoryId() : null, onItem ? comment.getProductId() : null,
                comment.getAuthorName(), comment.getBody(), comment.getCreatedAt()));
    }

    @Override
    public boolean updateResolution(Comment comment) {
        return Jdbc.update("""
                UPDATE comments SET resolved = 'Y', resolution_note = ?, resolved_by = ?, resolved_at = ?
                WHERE comment_id = ? AND resolved = 'N'""",
                comment.getResolutionNote(), comment.getResolvedBy(), comment.getResolvedAt(), comment.getId()) == 1;
    }

    @Override
    public int findGuestCount(long menuId) {
        return Jdbc.queryOne("SELECT p.guest_count FROM menus m JOIN projects p ON p.project_id = m.project_id WHERE m.menu_id = ?",
                rs -> rs.getInt(1), menuId).orElseThrow(() -> new IllegalStateException("Menu " + menuId + " neexistuje."));
    }

    @Override
    public long findProjectId(long menuId) {
        return Jdbc.queryOne("SELECT project_id FROM menus WHERE menu_id = ?", rs -> rs.getLong(1), menuId)
                .orElseThrow(() -> new IllegalStateException("Menu " + menuId + " neexistuje."));
    }

    @Override
    public String findTitle(long menuId) {
        return Jdbc.queryOne("SELECT p.name || ' – ' || m.name FROM menus m JOIN projects p ON p.project_id = m.project_id WHERE m.menu_id = ?",
                rs -> rs.getString(1), menuId).orElseThrow(() -> new IllegalStateException("Menu " + menuId + " neexistuje."));
    }

    private static void insertContent(MenuRevision revision) {
        int position = 1;
        for (MenuCategory category : revision.getCategories()) {
            category.setId(Jdbc.insert("""
                    INSERT INTO menu_categories (menu_id, revision_no, name, position) VALUES (?, ?, ?, ?)""",
                    "CATEGORY_ID", revision.getMenuId(), revision.getRevisionNo(), category.getName(), position++));
            for (MenuItem item : category.getItems())
                Jdbc.update("""
                        INSERT INTO menu_items (category_id, product_id, quantity, unit_price, note) VALUES (?, ?, ?, ?, ?)""",
                        category.getId(), item.getProduct().getId(), item.getQuantity(),
                        item.getUnitPrice().amount(), item.getNote());
        }
    }

    private static LocalDateTime createdAt(MenuRevision revision) {
        if (revision.getCreatedAt() == null) throw new IllegalStateException("Revize nemá čas uložení.");
        return revision.getCreatedAt();
    }

    private static MenuRevision readHeader(ResultSet rs) throws SQLException {
        return new MenuRevision(rs.getLong("menu_id"), rs.getInt("revision_no"), RevisionState.valueOf(rs.getString("state")),
                rs.getString("note"), Jdbc.longOrNull(rs, "created_by"), Jdbc.dateTime(rs, "created_at"), true);
    }

    private static Comment readComment(ResultSet rs) throws SQLException {
        Comment c = new Comment(rs.getLong("menu_id"), rs.getInt("revision_no"), Jdbc.longOrNull(rs, "category_id"),
                Jdbc.longOrNull(rs, "product_id"), rs.getString("product_name"), rs.getString("author_name"),
                rs.getString("body"), Jdbc.dateTime(rs, "created_at"), "Y".equals(rs.getString("resolved")),
                rs.getString("resolution_note"), Jdbc.longOrNull(rs, "resolved_by"), rs.getString("resolved_by_name"),
                Jdbc.dateTime(rs, "resolved_at"));
        c.setId(rs.getLong("comment_id"));
        return c;
    }
}
