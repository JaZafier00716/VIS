package business.domainmodel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CV08 Value Object (cv08 p.8, p.19). */
class MoneyTest {
    @Test
    void twoEqualPricesAreEqualByValue() {
        Money a = new Money(new BigDecimal("500"), Money.CZK);
        Money b = Money.czk(new BigDecimal("500.00"));
        assertNotSame(a, b);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void negativePriceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Money.czk(-1));
        assertThrows(IllegalArgumentException.class, () -> Money.czk(new BigDecimal("1.005")));
        assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.ONE, null));
    }

    @Test
    void operationsReturnNewValuesAndKeepTheOriginal() {
        Money price = Money.czk(1000);
        Money doubled = price.times(2);
        assertEquals(Money.czk(2000), doubled);
        assertEquals(Money.czk(1000), price);
        assertEquals(Money.czk(2500), doubled.plus(Money.czk(500)));
        assertEquals(Money.czk(new BigDecimal("33.33")), Money.czk(100).times(new BigDecimal("0.33333")));
    }

    @Test
    void differentCurrenciesAreNotAdded() {
        Money eur = new Money(BigDecimal.TEN, Currency.getInstance("EUR"));
        assertThrows(IllegalArgumentException.class, () -> Money.czk(10).plus(eur));
    }

    @Test
    void changingTheItemQuantityCreatesANewLinePriceAndLeavesTheUnitPrice() {
        OrderSpecification specification = OrderSpecification.builder().width(100).height(80).depth(40).build();
        Order order = new Order("O-1", "C-1", "M-OAK", specification, null);
        Money unitPrice = Money.czk(1500);
        OrderItem item = order.addItem("M-OAK", 2, unitPrice, null);
        Money before = item.totalPrice();
        order.changeItemQuantity(1, 3);
        assertEquals(Money.czk(3000), before);
        assertEquals(Money.czk(4500), item.totalPrice());
        assertEquals(Money.czk(1500), item.getUnitPrice());
    }
}
