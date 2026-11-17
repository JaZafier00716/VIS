package business.domainmodel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CV08 Special Case (cv08 p.10 table, p.19). */
class DiscountPolicyTest {
    @Test
    void orderWithoutDiscountKeepsThePrice() {
        assertEquals(Money.czk(2000), NoDiscount.INSTANCE.apply(Money.czk(2000)));
    }

    @Test
    void tenPercentDiscountLowersThePriceByTenPercent() {
        assertEquals(Money.czk(1800), new PercentageDiscount(10).apply(Money.czk(2000)));
    }

    @Test
    void bothVariantsGoThroughTheSameMechanism() {
        List<DiscountPolicy> policies = List.of(NoDiscount.INSTANCE, new PercentageDiscount(10));
        List<Money> prices = policies.stream().map(policy -> policy.apply(Money.czk(2000))).toList();
        assertEquals(List.of(Money.czk(2000), Money.czk(1800)), prices);
    }

    @Test
    void invalidPercentageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PercentageDiscount(0));
        assertThrows(IllegalArgumentException.class, () -> new PercentageDiscount(100));
    }
}
