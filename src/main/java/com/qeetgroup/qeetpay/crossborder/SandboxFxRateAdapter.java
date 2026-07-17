package com.qeetgroup.qeetpay.crossborder;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Sandbox FX rates (TAD §11.1) — deterministic, offline stand-in for a live rate feed. Provides
 * indicative INR conversion rates for common export currencies. Active whenever no live adapter bean
 * is present.
 */
@Component
@ConditionalOnMissingBean(name = "liveFxRateAdapter")
public class SandboxFxRateAdapter implements FxRateAdapter {

    private static final Map<String, BigDecimal> INR_RATES =
            Map.of(
                    "USD", new BigDecimal("83.50"),
                    "EUR", new BigDecimal("90.25"),
                    "GBP", new BigDecimal("105.75"),
                    "AED", new BigDecimal("22.73"),
                    "SGD", new BigDecimal("62.10"));

    @Override
    public BigDecimal rate(String fromCurrency, String toCurrency) {
        if (!"INR".equalsIgnoreCase(toCurrency)) {
            throw new IllegalArgumentException("sandbox only converts to INR, got " + toCurrency);
        }
        BigDecimal rate = INR_RATES.get(fromCurrency == null ? null : fromCurrency.toUpperCase(Locale.ROOT));
        if (rate == null) {
            throw new IllegalArgumentException("no sandbox FX rate for " + fromCurrency + "->INR");
        }
        return rate;
    }
}
