package cz.vsb.catering.business.pricing;

import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.Money;

/** Strategy (GoF): how the price of one menu line is computed; the menu editor and the web page
 *  switch between prices without and with VAT without changing MenuRevision. */
public interface PriceStrategy {
    Money lineTotal(MenuItem item);

    /** Label for the UI, e.g. "bez DPH". */
    String label();

    PriceStrategy WITHOUT_VAT = new PriceWithoutVat();
    PriceStrategy WITH_VAT = new PriceWithVat();
}
