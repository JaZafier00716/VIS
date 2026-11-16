package cz.vsb.catering.business.pricing;

import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.Money;

/** quantity x unit price (unit prices are stored without VAT). */
public class PriceWithoutVat implements PriceStrategy {
    @Override public Money lineTotal(MenuItem item) {
        return item.getUnitPrice().times(item.getQuantity());
    }

    @Override public String label() { return "bez DPH"; }
}
