package cz.vsb.catering.business.fake;

import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.port.ShareLinkMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class FakeShareLinkMapper implements ShareLinkMapper {
    public final List<ShareLink> links = new ArrayList<>();
    public final List<ShareLink> inserted = new ArrayList<>();
    public final List<ShareLink> updated = new ArrayList<>();
    private long nextId = 1;

    /** Existing link in the "database" (not counted as inserted). */
    public ShareLink add(ShareLink link) {
        link.setId(nextId++);
        links.add(link);
        return link;
    }

    @Override public Optional<ShareLink> findByToken(String token) {
        return links.stream().filter(l -> l.getToken().equals(token)).findFirst();
    }

    @Override public List<ShareLink> findByMenu(long menuId) {
        return links.stream().filter(l -> l.getMenuId() == menuId)
                .sorted(Comparator.comparing(ShareLink::getCreatedAt).reversed()).toList();
    }

    @Override public List<ShareLink> findActive() {
        return links.stream().filter(ShareLink::isActive).toList();
    }

    @Override public void insert(ShareLink link) {
        add(link);
        inserted.add(link);
    }

    @Override public void update(ShareLink link) { updated.add(link); }
}
