package com.qeetgroup.qeetpay.billing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Computes invoice amounts from a plan's pricing model + period usage.
 *
 * <p>Tier JSON shape: [{upTo: N, unitPrice: M}, ..., {upTo: null, unitPrice: K}]
 * where {@code upTo: null} means "no ceiling" (last tier).
 */
@Component
public class PricingCalculator {

    private static final TypeReference<List<Map<String, Object>>> TIER_TYPE = new TypeReference<>() {};
    private final ObjectMapper objectMapper;

    public PricingCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the invoice amount in minor units for the given plan and total usage quantity.
     * For FLAT plans the usage value is ignored.
     */
    public long compute(Plan plan, long usageQuantity) {
        return switch (plan.getPricingModel()) {
            case FLAT -> plan.getAmountMinor();
            case PER_UNIT -> plan.getAmountMinor() * usageQuantity;
            case TIERED -> tiered(plan, usageQuantity);
            case VOLUME -> volume(plan, usageQuantity);
            case HYBRID -> plan.getAmountMinor() + perUnit(plan, usageQuantity);
        };
    }

    /**
     * Tiered: {@code upTo} is a cumulative ceiling (Stripe-style).
     * Tier 1 upTo=10 → units 1–10. Tier 2 upTo=100 → units 11–100 (90 units). Tier 3 upTo=null → 101+.
     */
    private long tiered(Plan plan, long quantity) {
        List<Map<String, Object>> tiers = parseTiers(plan);
        long remaining = quantity;
        long total = 0L;
        long previousCeiling = 0L;
        for (Map<String, Object> tier : tiers) {
            if (remaining <= 0) break;
            Long upTo = toLong(tier.get("upTo"));
            long unitPrice = toLong(tier.get("unitPrice"));
            long tierCapacity = upTo != null ? upTo - previousCeiling : remaining;
            long inTier = Math.min(remaining, tierCapacity);
            total += inTier * unitPrice;
            remaining -= inTier;
            if (upTo != null) previousCeiling = upTo;
        }
        return total;
    }

    private long volume(Plan plan, long quantity) {
        List<Map<String, Object>> tiers = parseTiers(plan);
        for (Map<String, Object> tier : tiers) {
            Long upTo = toLong(tier.get("upTo"));
            long unitPrice = toLong(tier.get("unitPrice"));
            if (upTo == null || quantity <= upTo) {
                return quantity * unitPrice;
            }
        }
        throw new IllegalArgumentException("quantity " + quantity + " exceeds all volume tiers");
    }

    private long perUnit(Plan plan, long quantity) {
        List<Map<String, Object>> tiers = parseTiers(plan);
        if (tiers.isEmpty()) return 0L;
        return quantity * toLong(tiers.get(0).get("unitPrice"));
    }

    private List<Map<String, Object>> parseTiers(Plan plan) {
        try {
            return objectMapper.readValue(plan.getTiers(), TIER_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("invalid tiers JSON on plan " + plan.getId(), e);
        }
    }

    private static Long toLong(Object val) {
        if (val == null) return null;
        return ((Number) val).longValue();
    }
}
