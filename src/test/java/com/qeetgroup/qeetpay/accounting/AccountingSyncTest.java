package com.qeetgroup.qeetpay.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Accounting export flow (PRD Module 11.3): a Tally export renders the period's ledger entries as
 * journal-voucher lines, a run is persisted and listable/downloadable, and — with no Zoho creds in
 * the {@code test} profile — the ZOHO target is served by the sandbox connector.
 */
class AccountingSyncTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired LedgerService ledger;
    @Autowired AccountingSyncService accounting;

    @Test
    void tallyExportContainsJournalLines() {
        UUID merchantId = newMerchant();
        postEntry(merchantId, 500_000); // debit settlement / credit revenue ₹5000.00

        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now().plusSeconds(3600);
        AccountingSync run = accounting.export(merchantId, AccountingTarget.TALLY, from, to);

        assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(run.getRecordCount()).isGreaterThanOrEqualTo(1);

        String xml = run.getDocument();
        assertThat(xml).contains("<ENVELOPE>");
        assertThat(xml).contains("VCHTYPE=\"Journal\"");
        assertThat(xml).contains("<ALLLEDGERENTRIES.LIST>");
        assertThat(xml).contains("<LEDGERNAME>settlement</LEDGERNAME>");
        assertThat(xml).contains("<LEDGERNAME>revenue</LEDGERNAME>");
        assertThat(xml).contains("5000.00");        // rupees, 2dp
        assertThat(xml).contains("-5000.00");       // debit side is negative in Tally
    }

    @Test
    void syncRunPersistsAndIsListableAndDownloadable() {
        UUID merchantId = newMerchant();
        postEntry(merchantId, 120_000);

        AccountingSync run =
                accounting.export(
                        merchantId, AccountingTarget.TALLY,
                        Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        List<AccountingSync> all = accounting.list(merchantId);
        assertThat(all).extracting(AccountingSync::getId).contains(run.getId());

        AccountingSync fetched = accounting.get(merchantId, run.getId());
        assertThat(fetched.getTarget()).isEqualTo(AccountingTarget.TALLY);

        AccountingSync downloadable = accounting.download(merchantId, run.getId());
        assertThat(downloadable.getDocument()).contains("<ENVELOPE>");
    }

    @Test
    void zohoTargetUsesSandboxConnectorWhenNoCreds() {
        UUID merchantId = newMerchant();
        postEntry(merchantId, 90_000);

        AccountingSync run =
                accounting.export(
                        merchantId, AccountingTarget.ZOHO,
                        Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(run.getExternalRef()).startsWith("sandbox-zoho-");
    }

    @Test
    void connectionUpsertRoundTrips() {
        UUID merchantId = newMerchant();
        accounting.upsertConnection(merchantId, AccountingTarget.WEBHOOK, true, "https://example.com/hook", null);
        AccountingConnection updated =
                accounting.upsertConnection(merchantId, AccountingTarget.WEBHOOK, false, "https://example.com/hook2", null);

        assertThat(updated.isEnabled()).isFalse();
        assertThat(updated.getWebhookUrl()).isEqualTo("https://example.com/hook2");
        assertThat(accounting.listConnections(merchantId)).hasSize(1); // upsert, not insert
    }

    private void postEntry(UUID merchantId, long amountMinor) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        ledger.postEntry(
                merchantId, "accounting-test entry", "INR",
                List.of(
                        new LedgerLineInput(settlement, Direction.DEBIT, amountMinor),
                        new LedgerLineInput(revenue, Direction.CREDIT, amountMinor)));
    }

    private UUID newMerchant() {
        return merchants.create("acct-" + UUID.randomUUID().toString().substring(0, 8), "Accounting Co")
                .merchant().getId();
    }
}
