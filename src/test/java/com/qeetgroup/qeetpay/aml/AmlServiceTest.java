package com.qeetgroup.qeetpay.aml;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AML flow against a real Postgres (RLS) with the sandbox adapters: sanctions screening hit/miss,
 * transaction-monitoring alerts, mule detection, and STR generation/filing.
 */
class AmlServiceTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired AmlService aml;

    @Test
    void screeningHitPersistsAndRaisesAlert() {
        UUID merchantId = newMerchant();
        SanctionScreening hit =
                aml.screenParty(merchantId, PartyType.INDIVIDUAL, "Osama Bin Example", "ABCDE1234F");
        assertThat(hit.getResult()).isEqualTo(ScreeningResult.HIT);
        assertThat(hit.getMatchCount()).isGreaterThanOrEqualTo(1);
        assertThat(hit.getRiskScore()).isGreaterThan(0);

        // The hit escalated into an alert.
        List<AmlAlert> alerts = aml.listAlerts(merchantId, null);
        assertThat(alerts).extracting(AmlAlert::getRuleCode).contains("AML-SANCT-01");
    }

    @Test
    void screeningMissIsClear() {
        UUID merchantId = newMerchant();
        SanctionScreening clear =
                aml.screenParty(merchantId, PartyType.BUSINESS, "Ordinary Trading Pvt Ltd", "ZZZZZ9999Z");
        assertThat(clear.getResult()).isEqualTo(ScreeningResult.CLEAR);
        assertThat(clear.getMatchCount()).isZero();
    }

    @Test
    void blockedIdentifierAlsoHits() {
        UUID merchantId = newMerchant();
        SanctionScreening hit =
                aml.screenParty(merchantId, PartyType.BENEFICIARY, "Clean Name", "SANCTION-0001");
        assertThat(hit.getResult()).isEqualTo(ScreeningResult.HIT);
    }

    @Test
    void monitoringStructuringRaisesAnAlert() {
        UUID merchantId = newMerchant();
        List<AmlAlert> raised =
                aml.monitorTransaction(
                        merchantId,
                        new TransactionSignal("txn_str", 95_000_000L, "INR", null, null, null, null, "ben_1"));
        assertThat(raised).extracting(AmlAlert::getRuleCode).contains("AML-STRUCT-01");
        assertThat(raised.get(0).getSeverity()).isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void muleScanFlagsAndRaisesAlert() {
        UUID merchantId = newMerchant();
        MuleAssessment a =
                aml.assessBeneficiary(
                        merchantId,
                        new BeneficiaryActivity("ben_mule", 50_000_000L, 49_000_000L, 30, 12, 300, 40));
        assertThat(a.flagged()).isTrue();
        assertThat(aml.listAlerts(merchantId, null))
                .extracting(AmlAlert::getRuleCode)
                .contains("AML-MULE-01");
    }

    @Test
    void caseGroupsAlertsAndCloses() {
        UUID merchantId = newMerchant();
        List<AmlAlert> raised =
                aml.monitorTransaction(
                        merchantId,
                        new TransactionSignal("txn_c", 95_000_000L, "INR", 7995, "KP", null, null, "ben_c"));
        assertThat(raised).isNotEmpty();

        AmlCase amlCase =
                aml.createCase(
                        merchantId, "High-risk merchant pattern", "auto-grouped",
                        raised.stream().map(AmlAlert::getId).toList());
        assertThat(amlCase.getStatus()).isEqualTo(CaseStatus.OPEN);
        assertThat(amlCase.getAlertCount()).isEqualTo(raised.size());
        assertThat(amlCase.getRiskScore()).isGreaterThan(0);

        AmlCase closed = aml.closeCase(merchantId, amlCase.getId(), CaseDisposition.STR_FILED);
        assertThat(closed.getStatus()).isEqualTo(CaseStatus.CLOSED);
        assertThat(closed.getDisposition()).isEqualTo(CaseDisposition.STR_FILED);
        assertThat(closed.getClosedAt()).isNotNull();
    }

    @Test
    void strReportIsGeneratedAndFiled() {
        UUID merchantId = newMerchant();
        StrReport str =
                aml.createStrReport(
                        merchantId, null, "Suspicious layering by beneficiary ben_1",
                        "Rapid pass-through consistent with mule behaviour", true);
        assertThat(str.getStatus()).isEqualTo(StrStatus.FILED);
        assertThat(str.getFiuReferenceId()).startsWith("FIUIND-STR-");
        assertThat(str.getPayload()).contains("\"reportType\":\"STR\"");
        assertThat(aml.listStrReports(merchantId)).hasSize(1);
    }

    @Test
    void strReportCanRemainDraft() {
        UUID merchantId = newMerchant();
        StrReport str =
                aml.createStrReport(merchantId, null, "Draft subject", "Draft grounds", false);
        assertThat(str.getStatus()).isEqualTo(StrStatus.DRAFT);
        assertThat(str.getFiuReferenceId()).isNull();
    }

    private UUID newMerchant() {
        return merchants.create("aml-" + UUID.randomUUID().toString().substring(0, 8), "AML Co")
                .merchant().getId();
    }
}
