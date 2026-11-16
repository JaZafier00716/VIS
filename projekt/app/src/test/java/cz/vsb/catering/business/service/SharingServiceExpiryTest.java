package cz.vsb.catering.business.service;

import static cz.vsb.catering.business.fake.Fixtures.NOW;
import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeMenuMapper;
import cz.vsb.catering.business.fake.FakeProjectMapper;
import cz.vsb.catering.business.fake.FakeShareLinkMapper;
import cz.vsb.catering.business.fake.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UC18 Zneplatnit prošlé odkazy with a fixed clock. */
class SharingServiceExpiryTest {
    private final FakeShareLinkMapper links = new FakeShareLinkMapper();
    private final SharingService service = new SharingService(new FakeMenuMapper(), new FakeProjectMapper(), links,
            new DirectTransactionRunner(), Fixtures.CLOCK, "http://localhost:8080");

    private ShareLink link(String token, long validMinutesFromNow, boolean active) {
        return links.add(new ShareLink(5, 1, token, NOW.minusDays(14), NOW.plusMinutes(validMinutesFromNow), active));
    }

    @Test void deactivatesOnlyActiveExpiredLinks() {
        ShareLink expired = link("a", -60, true);
        ShareLink endsNow = link("b", 0, true);          // valid_until == now is no longer valid
        ShareLink valid = link("c", 60, true);
        ShareLink inactive = link("d", -60, false);

        assertEquals(2, service.deactivateExpiredLinks());

        assertFalse(expired.isActive());
        assertFalse(endsNow.isActive());
        assertTrue(valid.isActive());
        assertFalse(inactive.isActive());
        assertEquals(List.of(expired, endsNow), links.updated);
        assertEquals(0, service.deactivateExpiredLinks());   // second run has nothing to do
    }
}
