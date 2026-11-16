package cz.vsb.catering.business.domain;

import java.time.LocalDateTime;

/** One row of the price journal written by the DB trigger trg_products_price_history (read only). */
public record PriceChange(LocalDateTime changedAt, Money oldPrice, Money newPrice) {}
