package business.pricing;

import business.domainmodel.Money;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CV09 Strategy: the same order gives a different price only because a different strategy is used. */
class PricingStrategyTest {
    private static final OrderSpecification SPEC =
            OrderSpecification.builder().width(100).height(80).depth(50).build();

    private static Order orderWithTwoItems() {
        Order order = new Order("O-1", "C-1", "M-OAK", SPEC, Instant.now());
        order.addItem("M-OAK", 1, Money.czk(1000), null);
        order.addItem("M-PINE", 1, Money.czk(1000), null);
        return order;
    }

    @Test
    void standardPricingIsTheSumOfTheItems() {
        assertEquals(Money.czk(2000), new StandardPricing().calculate(orderWithTwoItems()));
    }

    @Test
    void companyPricingIsTenPercentLess() {
        assertEquals(Money.czk(1800), new CompanyPricing().calculate(orderWithTwoItems()));
    }

    @Test
    void strategiesAreInterchangeableThroughTheInterface() {
        Order order = orderWithTwoItems();
        List<IPricingStrategy> strategies = List.of(new StandardPricing(), new CompanyPricing());
        List<Money> prices = strategies.stream().map(strategy -> strategy.calculate(order)).toList();
        assertEquals(List.of(Money.czk(2000), Money.czk(1800)), prices);
    }

    @Test
    void anOrderWithoutItemsCannotBePriced() {
        Order empty = new Order("O-2", "C-1", "M-OAK", SPEC, Instant.now());
        assertThrows(IllegalArgumentException.class, () -> new StandardPricing().calculate(empty));
        assertThrows(IllegalArgumentException.class, () -> new CompanyPricing().calculate(empty));
    }
}
