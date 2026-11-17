package business.domainmodel;

import java.math.BigDecimal;

/** CV08 Special Case: the ordinary variant, a percentage discount (e.g. 10 %). */
public record PercentageDiscount(int percent) implements DiscountPolicy {
    public PercentageDiscount {
        if (percent <= 0 || percent >= 100) {
            throw new IllegalArgumentException("Sleva musí být mezi 1 a 99 %.");
        }
    }

    @Override
    public Money apply(Money price) {
        return price.times(BigDecimal.valueOf(100 - percent).movePointLeft(2));
    }

    @Override
    public String describe() {
        return "sleva " + percent + " %";
    }
}
