package cz.vsb.catering.business.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Value Object: an amount in CZK rounded to 2 decimals; immutable, compared by value. */
public final class Money implements Comparable<Money> {
    public static final Money ZERO = new Money(BigDecimal.ZERO);
    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Částka chybí.");
        return new Money(amount);
    }

    public static Money of(String amount) { return of(new BigDecimal(amount)); }

    public static Money of(long amount) { return of(BigDecimal.valueOf(amount)); }

    public BigDecimal amount() { return amount; }

    public Money plus(Money other) { return new Money(amount.add(other.amount)); }

    public Money minus(Money other) { return new Money(amount.subtract(other.amount)); }

    public Money times(BigDecimal factor) { return new Money(amount.multiply(factor)); }

    public Money dividedBy(int divisor) {
        if (divisor <= 0) throw new IllegalArgumentException("Dělitel musí být větší než 0.");
        return new Money(amount.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP));
    }

    public boolean isNegative() { return amount.signum() < 0; }

    /** Czech format, e.g. "1 234,50 Kč". */
    public String format() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("cs-CZ"));
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.00", symbols).format(amount) + " Kč";
    }

    @Override public int compareTo(Money o) { return amount.compareTo(o.amount); }

    @Override public boolean equals(Object o) {
        return o instanceof Money m && amount.compareTo(m.amount) == 0;
    }

    @Override public int hashCode() { return amount.stripTrailingZeros().hashCode(); }

    @Override public String toString() { return format(); }
}
