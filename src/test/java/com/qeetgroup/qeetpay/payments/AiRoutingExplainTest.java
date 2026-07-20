package com.qeetgroup.qeetpay.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AI orchestration ranking + compliance-aware routing (PRD Module 07.3 "Orchestration ML" + 07.6
 * "Compliance-aware routing"). Verifies the AI-predicted auth-rate × (1 − cost) ranking through the AI
 * gateway, the deterministic scorecard fallback when the model client errors, and that the GST
 * compliance assessment (GSTIN correctness + place of supply) + a plain-English explanation are present.
 */
@AutoConfigureMockMvc
class AiRoutingExplainTest extends AbstractIntegrationTest {

    // Checksum-valid GSTINs (state 27 = Maharashtra, 29 = Karnataka).
    private static final String VALID_GSTIN_27 = "27AAPFU0939F1ZT";

    @Autowired MerchantService merchants;
    @Autowired ProviderRoutingService routing;
    @Autowired AiProviderScorer scorer;
    @Autowired ComplianceRouter compliance;
    @Autowired MockMvc mvc;

    @Test
    void aiRankingPrefersLowerCostAndAssessesInterStateSupply() {
        UUID merchantId = newMerchant();
        routing.setCost(merchantId, "SANDBOX", 15); // 0.15%
        routing.setCost(merchantId, "RAZORPAY", 30); // 0.30%

        ComplianceAssessment a = compliance.assess(VALID_GSTIN_27, "29"); // supplier 27 → buyer 29
        ProviderRanking ranking =
                scorer.rank(
                        merchantId,
                        List.of("RAZORPAY", "SANDBOX"),
                        compliance.contextString(VALID_GSTIN_27, a),
                        Set.of());

        assertThat(ranking.aiAssisted()).isTrue();
        assertThat(ranking.method()).isEqualTo("ai-predicted");
        assertThat(ranking.providers()).hasSize(2);
        assertThat(ranking.recommendedProvider()).isEqualTo("SANDBOX"); // cheaper → higher predicted
        assertThat(ranking.decisionId()).isNotNull();

        assertThat(a.gstinValid()).isTrue();
        assertThat(a.supplyType()).isEqualTo("INTER_STATE");
        assertThat(a.igstApplicable()).isTrue();
        assertThat(a.compliant()).isTrue();
    }

    @Test
    void fallsBackToDeterministicScorecardWhenModelErrors() {
        UUID merchantId = newMerchant();
        routing.setCost(merchantId, "SANDBOX", 15);

        // A GSTIN carrying the sandbox "fail_" sentinel forces the model client to throw.
        String sentinel = "FAIL_MODEL_GSTIN";
        ComplianceAssessment a = compliance.assess(sentinel, "27");
        ProviderRanking ranking =
                scorer.rank(
                        merchantId,
                        List.of("RAZORPAY", "SANDBOX"),
                        compliance.contextString(sentinel, a),
                        Set.of());

        assertThat(ranking.aiAssisted()).isFalse();
        assertThat(ranking.method()).isEqualTo("deterministic-scorecard");
        assertThat(ranking.recommendedProvider()).isNotNull();
        assertThat(a.gstinValid()).isFalse();
        assertThat(a.compliant()).isFalse(); // invalid GSTIN flagged as a compliance risk
    }

    @Test
    void routingExplainEndpointReturnsRankingComplianceAndExplanation() throws Exception {
        UUID merchantId = newMerchant();
        routing.setCost(merchantId, "SANDBOX", 15);

        mvc.perform(
                        get("/v1/payments/providers/routing-explain")
                                .header("X-Merchant-Id", merchantId.toString())
                                .param("gstin", VALID_GSTIN_27)
                                .param("placeOfSupply", "27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranking.length()").value(2))
                .andExpect(jsonPath("$.recommendedProvider").isNotEmpty())
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.compliance.gstinValid").value(true))
                .andExpect(jsonPath("$.compliance.supplyType").value("INTRA_STATE"));
    }

    private UUID newMerchant() {
        return merchants.create("route-" + UUID.randomUUID().toString().substring(0, 8), "Routing Co")
                .merchant().getId();
    }
}
