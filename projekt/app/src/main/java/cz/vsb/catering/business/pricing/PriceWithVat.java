package cz.vsb.catering.business.pricing;

import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.Money;
import java.math.BigDecimal;

/** quantity x unit price x (1 + VAT rate of the product). */
public class PriceWithVat implements PriceStrategy {
    @Override public Money lineTotal(MenuItem item) {
        BigDecimal factor = BigDecimal.ONE.add(item.getProduct().getVatRate().movePointLeft(2));
        return item.getUnitPrice().times(item.getQuantity().multiply(factor));
    }

    @Override public String label() { return "s DPH"; }
}
