package cz.vsb.catering.business.port;

import cz.vsb.catering.business.domain.ShareLink;
import java.util.List;
import java.util.Optional;

public interface ShareLinkMapper {
    Optional<ShareLink> findByToken(String token);

    /** All links of a menu, newest first. */
    List<ShareLink> findByMenu(long menuId);

    /** All links with active = 'Y' (UC18 decides in the domain which of them expired). */
    List<ShareLink> findActive();

    void insert(ShareLink link);

    /** Writes the active flag. */
    void update(ShareLink link);
}
