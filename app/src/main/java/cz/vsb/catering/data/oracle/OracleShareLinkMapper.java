package cz.vsb.catering.data.oracle;

import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.port.ShareLinkMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Data Mapper for share_links. */
public class OracleShareLinkMapper implements ShareLinkMapper {
    private static final String SELECT =
            "SELECT link_id, menu_id, revision_no, token, created_at, valid_until, active FROM share_links ";

    @Override
    public Optional<ShareLink> findByToken(String token) {
        return Jdbc.queryOne(SELECT + "WHERE token = ?", OracleShareLinkMapper::read, token);
    }

    @Override
    public List<ShareLink> findByMenu(long menuId) {
        return Jdbc.query(SELECT + "WHERE menu_id = ? ORDER BY created_at DESC, link_id DESC",
                OracleShareLinkMapper::read, menuId);
    }

    @Override
    public List<ShareLink> findActive() {
        return Jdbc.query(SELECT + "WHERE active = 'Y'", OracleShareLinkMapper::read);
    }

    @Override
    public void insert(ShareLink link) {
        link.setId(Jdbc.insert("""
                INSERT INTO share_links (menu_id, revision_no, token, created_at, valid_until, active)
                VALUES (?, ?, ?, ?, ?, ?)""", "LINK_ID",
                link.getMenuId(), link.getRevisionNo(), link.getToken(), link.getCreatedAt(),
                link.getValidUntil(), Jdbc.yesNo(link.isActive())));
    }

    @Override
    public void update(ShareLink link) {
        Jdbc.update("UPDATE share_links SET active = ? WHERE link_id = ?", Jdbc.yesNo(link.isActive()), link.getId());
    }

    private static ShareLink read(ResultSet rs) throws SQLException {
        ShareLink link = new ShareLink(rs.getLong("menu_id"), rs.getInt("revision_no"), rs.getString("token").trim(),
                Jdbc.dateTime(rs, "created_at"), Jdbc.dateTime(rs, "valid_until"), "Y".equals(rs.getString("active")));
        link.setId(rs.getLong("link_id"));
        return link;
    }
}
