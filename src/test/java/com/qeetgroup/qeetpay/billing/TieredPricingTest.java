package com.qeetgroup.qeetpay.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure pricing-model tests for {@link PricingCalculator}. No Spring context / DB needed — the
 * calculator only needs an {@link ObjectMapper}, and {@link Plan}s are built in memory. Keeping this
 * off Testcontainers makes it a fast, deterministic unit test.
 */
class TieredPricingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PricingCalculator calculator = new PricingCalculator(objectMapper);

    @Test
    void flat_ignoresUsage() {
        Plan plan = flatPlan(5000L);
        assertThat(calculator.compute(plan, 999L)).isEqualTo(5000L);
    }

    @Test
    void perUnit_multiplyQuantity() {
        Plan plan = perUnitPlan(100L);
        assertThat(calculator.compute(plan, 50L)).isEqualTo(5000L);
    }

    @Test
    void perUnit_zeroQuantityIsZero() {
        assertThat(calculator.compute(perUnitPlan(100L), 0L)).isZero();
    }

    @Test
    void tiered_crossingMultipleTiers() throws Exception {
        // 0-10 units at 200 paise, 11-100 at 150 paise, 101+ at 100 paise
        String tiers = objectMapper.writeValueAsString(List.of(
                Map.of("upTo", 10, "unitPrice", 200),
                Map.of("upTo", 100, "unitPrice", 150),
                Map.of("unitPrice", 100)
        ));
        Plan plan = tieredPlan(tiers);

        // 25 units: first 10 × 200 = 2000, next 15 × 150 = 2250 → 4250
        assertThat(calculator.compute(plan, 25L)).isEqualTo(4250L);
        // 150 units: 10×200 + 90×150 + 50×100 = 2000 + 13500 + 5000 = 20500
        assertThat(calculator.compute(plan, 150L)).isEqualTo(20500L);
    }

    @Test
    void tiered_exactTierBoundaryStaysInFirstTier() throws Exception {
        String tiers = objectMapper.writeValueAsString(List.of(
                Map.of("upTo", 10, "unitPrice", 200),
                Map.of("unitPrice", 100)
        ));
        Plan plan = tieredPlan(tiers);
        // Exactly 10 units → all in tier 1 → 10 × 200 = 2000 (no spill into tier 2).
        assertThat(calculator.compute(plan, 10L)).isEqualTo(2000L);
        // 11 units → 10×200 + 1×100 = 2100.
        assertThat(calculator.compute(plan, 11L)).isEqualTo(2100L);
    }

    @Test
    void tiered_zeroQuantityIsZero() throws Exception {
        String tiers = objectMapper.writeValueAsString(List.of(
                Map.of("upTo", 10, "unitPrice", 200),
                Map.of("unitPrice", 100)
        ));
        assertThat(calculator.compute(tieredPlan(tiers), 0L)).isZero();
    }

    @Test
    void volume_allUnitsAtOneTier() throws Exception {
        // 0-100 at 300, 101-1000 at 200, >1000 at 100
        String tiers = objectMapper.writeValueAsString(List.of(
                Map.of("upTo", 100, "unitPrice", 300),
                Map.of("upTo", 1000, "unitPrice", 200),
                Map.of("unitPrice", 100)
        ));
        Plan plan = volumePlan(tiers);

        // 50 units → all in first tier → 50 × 300 = 15000
        assertThat(calculator.compute(plan, 50L)).isEqualTo(15000L);
        // 500 units → all in second tier → 500 × 200 = 100000
        assertThat(calculator.compute(plan, 500L)).isEqualTo(100000L);
        // 2000 units → all in third tier → 2000 × 100 = 200000
        assertThat(calculator.compute(plan, 2000L)).isEqualTo(200000L);
    }

    @Test
    void volume_boundaryUsesTierWhoseCeilingItReaches() throws Exception {
        String tiers = objectMapper.writeValueAsString(List.of(
                Map.of("upTo", 100, "unitPrice", 300),
                Map.of("unitPrice", 100)
        ));
        Plan plan = volumePlan(tiers);
        // Exactly 100 (== upTo) stays in the first tier: 100 × 300 = 30000.
        assertThat(calculator.compute(plan, 100L)).isEqualTo(30000L);
        // 101 spills to the unbounded tier: 101 × 100 = 10100.
        assertThat(calculator.compute(plan, 101L)).isEqualTo(10100L);
    }

    @Test
    void volume_quantityBeyondAllFiniteTiersThrows() throws Exception {
        // No unbounded (upTo: null) tier — a quantity above the last ceiling has no rate.
        String tiers = objectMapper.writeValueAsString(List.of(
                Map.of("upTo", 100, "unitPrice", 300),
                Map.of("upTo", 1000, "unitPrice", 200)
        ));
        Plan plan = volumePlan(tiers);
        assertThatThrownBy(() -> calculator.compute(plan, 5000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds all volume tiers");
    }

    @Test
    void hybrid_baseFeePlusOverage() throws Exception {
        // Base fee 5000 paise, overage at 50 paise/unit (via single-tier tiers)
        String tiers = objectMapper.writeValueAsString(List.of(Map.of("unitPrice", 50)));
        Plan plan = hybridPlan(5000L, tiers);
        // 100 units: 5000 + 100×50 = 10000
        assertThat(calculator.compute(plan, 100L)).isEqualTo(10000L);
    }

    @Test
    void hybrid_emptyTiersIsJustBaseFee() throws Exception {
        String tiers = objectMapper.writeValueAsString(List.of());
        Plan plan = hybridPlan(5000L, tiers);
        // No overage tiers → perUnit contributes 0 → just the base fee.
        assertThat(calculator.compute(plan, 100L)).isEqualTo(5000L);
    }

    @Test
    void invalidTiersJsonThrows() {
        Plan plan = tieredPlan("not-json");
        assertThatThrownBy(() -> calculator.compute(plan, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid tiers JSON");
    }

    // ── plan stubs ────────────────────────────────────────────────────────────

    private Plan flatPlan(long amount) {
        return new Plan(null, "flat", "Flat", amount, "INR", BillingInterval.MONTH);
    }

    private Plan perUnitPlan(long unitPrice) {
        return new Plan(null, "pu", "PerUnit", unitPrice, "INR", BillingInterval.MONTH)
                .withPricingModel(PricingModel.PER_UNIT, null, "api_calls");
    }

    private Plan tieredPlan(String tiersJson) {
        return new Plan(null, "tiered", "Tiered", 0L, "INR", BillingInterval.MONTH)
                .withPricingModel(PricingModel.TIERED, tiersJson, "api_calls");
    }

    private Plan volumePlan(String tiersJson) {
        return new Plan(null, "volume", "Volume", 0L, "INR", BillingInterval.MONTH)
                .withPricingModel(PricingModel.VOLUME, tiersJson, "api_calls");
    }

    private Plan hybridPlan(long baseFee, String tiersJson) {
        return new Plan(null, "hybrid", "Hybrid", baseFee, "INR", BillingInterval.MONTH)
                .withPricingModel(PricingModel.HYBRID, tiersJson, "api_calls");
    }
}
