package business.domainmodel;

public interface DiscountPolicy {
    Money apply(Money price);

    /** Text for the price summary, e.g. "sleva 10 %". */
    String describe();
}
