package com.qeetgroup.qeetpay.crossborder;

import java.math.BigDecimal;

/**
 * Pluggable FX-rate source (TAD §5) — sandbox or a live rate provider / authorised dealer feed.
 * Returns the conversion rate as units of {@code toCurrency} per one unit of {@code fromCurrency}.
 */
public interface FxRateAdapter {

    BigDecimal rate(String fromCurrency, String toCurrency);
}
