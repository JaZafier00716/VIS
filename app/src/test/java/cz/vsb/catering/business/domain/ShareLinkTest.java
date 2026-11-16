package cz.vsb.catering.business.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ShareLinkTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);

    @Test void createActiveLinkValid14Days() {
        ShareLink link = ShareLink.create(5, 2, NOW);
        assertTrue(link.getToken().matches("[0-9a-f]{32}"));
        assertNotEquals(link.getToken(), ShareLink.create(5, 2, NOW).getToken());
        assertEquals(NOW.plusDays(14), link.getValidUntil());
        assertTrue(link.isActive());
        assertTrue(link.exists());
        assertEquals(5, link.getMenuId());
        assertEquals(2, link.getRevisionNo());
    }

    @Test void validityAndExpiry() {
        ShareLink link = ShareLink.create(5, 2, NOW);
        assertTrue(link.isValid(NOW.plusDays(14).minusSeconds(1)));
        assertFalse(link.isValid(NOW.plusDays(14)));
        link.deactivate();
        assertFalse(link.isValid(NOW));
    }

    @Test void unknownLinkIsSpecialCase() {
        ShareLink unknown = new UnknownShareLink("abc");
        assertFalse(unknown.exists());
        assertFalse(unknown.isValid(NOW));
        assertEquals("abc", unknown.getToken());
    }
}
