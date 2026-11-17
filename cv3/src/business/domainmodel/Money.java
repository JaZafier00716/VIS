package business.domainmodel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * CV08 Value Object (cv08 p.7–8): an amount of money in one currency. Immutable, never negative, two instances
 * with the same amount and currency are equal. The amount always has two decimal places, so 500 and 500.00 are
 * the same value. Amounts in different currencies are never added together.
 */
public record Money(BigDecimal amount, Currency currency) {
    public static final Currency CZK = Currency.getInstance("CZK");

    public Money {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("Částka i měna jsou povinné.");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Cena nesmí být záporná.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Cena smí mít nejvýše 2 desetinná místa.");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static Money czk(BigDecimal amount) {
        return new Money(amount, CZK);
    }

    public static Money czk(long amount) {
        return czk(BigDecimal.valueOf(amount));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Nelze sčítat částky v různých měnách (" + currency + ", "
                    + other.currency + ").");
        }
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(int quantity) {
        return times(BigDecimal.valueOf(quantity));
    }

    /** Multiplies by a factor (e.g. 0.9 for a 10 % discount), rounded half up to two decimal places. */
    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor).setScale(2, RoundingMode.HALF_UP), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
