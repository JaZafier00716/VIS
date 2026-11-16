package cz.vsb.catering.business.fake;

import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/** Shared test data. */
public final class Fixtures {
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
    public static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private Fixtures() {}

    public static Product product(long id, String name, String price, String vatRate) {
        Product p = new Product(name, null, "ks", Money.of(price), new BigDecimal(vatRate), List.of());
        p.setId(id);
        return p;
    }

    /** Valid revision with one category (id 10) holding product 1 (qty 10 x 100 Kč). */
    public static MenuRevision validRevision(long menuId, int revisionNo, RevisionState state, boolean stored) {
        MenuRevision r = new MenuRevision(menuId, revisionNo, state, null, 1L, NOW.minusDays(1), stored);
        MenuCategory c = new MenuCategory("Předkrmy");
        c.setId(10L);
        r.loadCategory(c);
        if (state == RevisionState.WORKING) {
            r.addItem(c, product(1, "Bruschetta", "100", "12"), BigDecimal.TEN, Money.of(100), null);
        } else {
            // a SHARED revision is read-only, so it is built as WORKING and shared
            MenuRevision w = validRevision(menuId, revisionNo, RevisionState.WORKING, stored);
            w.markShared();
            return w;
        }
        return r;
    }

    public static User user(String username, Role... roles) {
        return new User(username, username + " Test", username + "@catering.cz",
                cz.vsb.catering.business.domain.PasswordHasher.hash("heslo123"), true, Set.of(roles));
    }

    public static MenuItem firstItem(MenuRevision r) { return r.getCategories().get(0).getItems().get(0); }
}
