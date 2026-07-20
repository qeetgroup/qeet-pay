package com.qeetgroup.qeetpay.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Agent-mandate authorization (PRD Module 17.5, Novel N1). Deterministic decisions: an in-cap action
 * is allowed and spends; over-cap / expired / non-allowlisted-payee / revoked actions are denied; and
 * a retried authorize (same agent-supplied key) replays the decision without double-spending.
 */
class AgentMandateFlowTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired AgentMandateService mandates;
    @Autowired McpManifestService mcp;

    @Test
    void authorizeWithinCapIsAllowedAndIncrementsSpent() {
        UUID merchantId = newMerchant();
        UUID mandateId = issueDefault(merchantId, 100_000L, 500_000L);

        AuthorizationDecision d =
                mandates.authorize(merchantId, mandateId, "payment.create", "vpa:alice@bank", 50_000L, true, null);

        assertThat(d.allowed()).isTrue();
        assertThat(d.reason()).isEqualTo("authorized");
        assertThat(d.spentMinor()).isEqualTo(50_000L);
        assertThat(d.remainingMinor()).isEqualTo(450_000L);
        assertThat(mandates.get(merchantId, mandateId).getSpentMinor()).isEqualTo(50_000L);
    }

    @Test
    void overPerTransactionCapIsDenied() {
        UUID merchantId = newMerchant();
        UUID mandateId = issueDefault(merchantId, 100_000L, 500_000L);

        AuthorizationDecision d =
                mandates.authorize(merchantId, mandateId, "payment.create", "vpa:alice@bank", 200_000L, true, null);

        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("amount exceeds per-transaction cap");
        assertThat(mandates.get(merchantId, mandateId).getSpentMinor()).isZero();
    }

    @Test
    void cumulativeCapIsEnforcedAcrossAuthorizations() {
        UUID merchantId = newMerchant();
        UUID mandateId = issueDefault(merchantId, 100_000L, 120_000L);

        AuthorizationDecision first =
                mandates.authorize(merchantId, mandateId, "payment.create", "vpa:alice@bank", 80_000L, true, "a");
        assertThat(first.allowed()).isTrue();

        AuthorizationDecision second =
                mandates.authorize(merchantId, mandateId, "payment.create", "vpa:alice@bank", 80_000L, true, "b");
        assertThat(second.allowed()).isFalse();
        assertThat(second.reason()).isEqualTo("amount exceeds cumulative cap");
        assertThat(mandates.get(merchantId, mandateId).getSpentMinor()).isEqualTo(80_000L);
    }

    @Test
    void expiredMandateIsDenied() {
        UUID merchantId = newMerchant();
        UUID mandateId =
                mandates.issue(
                                merchantId, "agent-x", "expired", 100_000L, 500_000L,
                                List.of("payment.create"), List.of("vpa:alice@bank"),
                                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60))
                        .getId();

        AuthorizationDecision d =
                mandates.authorize(merchantId, mandateId, "payment.create", "vpa:alice@bank", 10_000L, true, null);

        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("mandate expired");
        assertThat(mandates.get(merchantId, mandateId).getStatus()).isEqualTo(AgentMandateStatus.EXPIRED);
    }

    @Test
    void payeeNotAllowlistedIsDenied() {
        UUID merchantId = newMerchant();
        UUID mandateId = issueDefault(merchantId, 100_000L, 500_000L);

        AuthorizationDecision d =
                mandates.authorize(merchantId, mandateId, "payment.create", "vpa:eve@bank", 10_000L, true, null);

        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("payee not in allowlist");
        assertThat(mandates.get(merchantId, mandateId).getSpentMinor()).isZero();
    }

    @Test
    void operationNotAllowlistedIsDenied() {
        UUID merchantId = newMerchant();
        UUID mandateId =
                mandates.issue(
                                merchantId, "agent-x", "payments-only", 100_000L, 500_000L,
                                List.of("payment.create"), List.of("vpa:alice@bank"), null, null)
                        .getId();

        AuthorizationDecision d =
                mandates.authorize(merchantId, mandateId, "payout.create", "vpa:alice@bank", 10_000L, true, null);

        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("operation not permitted");
    }

    @Test
    void revokedMandateIsDenied() {
        UUID merchantId = newMerchant();
        UUID mandateId = issueDefault(merchantId, 100_000L, 500_000L);
        mandates.revoke(merchantId, mandateId, "agent compromised");

        AuthorizationDecision d =
                mandates.authorize(merchantId, mandateId, "payment.create", "vpa:alice@bank", 10_000L, true, null);

        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("mandate revoked");
        assertThat(mandates.get(merchantId, mandateId).getStatus()).isEqualTo(AgentMandateStatus.REVOKED);
    }

    @Test
    void idempotentReauthorizeReplaysAndDoesNotDoubleSpend() {
        UUID merchantId = newMerchant();
        UUID mandateId = issueDefault(merchantId, 100_000L, 500_000L);

        AuthorizationDecision first =
                mandates.authorize(merchantId, mandateId, "payment.create", "vpa:alice@bank", 50_000L, true, "order-42");
        AuthorizationDecision replay =
                mandates.authorize(merchantId, mandateId, "payment.create", "vpa:alice@bank", 50_000L, true, "order-42");

        assertThat(first.allowed()).isTrue();
        assertThat(replay.allowed()).isTrue();
        assertThat(replay.useId()).isEqualTo(first.useId()); // same original decision replayed
        assertThat(replay.spentMinor()).isEqualTo(50_000L);
        assertThat(mandates.get(merchantId, mandateId).getSpentMinor()).isEqualTo(50_000L); // not 100,000
    }

    @Test
    void issueRejectsUnknownOperation() {
        UUID merchantId = newMerchant();
        assertThatThrownBy(
                        () ->
                                mandates.issue(
                                        merchantId, "agent-x", "bad", 100_000L, 500_000L,
                                        List.of("bogus.tool"), List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mcpManifestListsCuratedTools() {
        McpManifest manifest = mcp.manifest();
        assertThat(manifest.server()).isEqualTo("qeet-pay");
        assertThat(manifest.tools()).extracting(McpTool::name)
                .contains("payment.create", "paymentlink.create", "payout.create", "invoice.create", "balance.read");
        assertThat(mcp.toolNames()).contains("payment.create");
    }

    private UUID newMerchant() {
        return merchants.create("agt-" + UUID.randomUUID().toString().substring(0, 8), "Agentic Co")
                .merchant().getId();
    }

    private UUID issueDefault(UUID merchantId, long maxTxnMinor, long cumulativeCapMinor) {
        return mandates.issue(
                        merchantId, "agent-x", "default", maxTxnMinor, cumulativeCapMinor,
                        List.of("payment.create", "payout.create"), List.of("vpa:alice@bank"), null, null)
                .getId();
    }
}
