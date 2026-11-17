package business.domainmodel;

/** CV08 Special Case: the expected situation "no discount"; the price stays unchanged. */
public final class NoDiscount implements DiscountPolicy {
    public static final NoDiscount INSTANCE = new NoDiscount();

    private NoDiscount() {}

    @Override
    public Money apply(Money price) {
        return price;
    }

    @Override
    public String describe() {
        return "bez slevy";
    }
}
