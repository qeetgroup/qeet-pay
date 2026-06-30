package com.qeetgroup.qeetpay.ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The double-entry ledger (TAD §6.2, §7.1). Posting an entry is atomic and balanced (Σdebits =
 * Σcredits); the DB enforces the same invariant via a deferred constraint trigger (V2), and a
 * {@code ledger.entry.posted} event is captured in the outbox in the same transaction.
 */
@Service
public class LedgerService {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final JournalLineRepository lines;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public LedgerService(
            AccountRepository accounts,
            JournalEntryRepository entries,
            JournalLineRepository lines,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.entries = entries;
        this.lines = lines;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    private static final List<AccountSeed> DEFAULT_CHART =
            List.of(
                    new AccountSeed("settlement", "Settlement account", AccountType.SETTLEMENT),
                    new AccountSeed("bank", "Bank / nodal", AccountType.BANK),
                    new AccountSeed("revenue", "Revenue", AccountType.REVENUE),
                    new AccountSeed("liability", "Merchant payable", AccountType.LIABILITY),
                    new AccountSeed("tax_payable", "Tax payable (GST)", AccountType.TAX_PAYABLE),
                    new AccountSeed(
                            "deferred_revenue", "Deferred revenue", AccountType.DEFERRED_REVENUE));

    /** Seeds the standard chart of accounts for a newly onboarded merchant. */
    @Transactional
    public void openDefaultChart(UUID merchantId, String currency) {
        merchantScope.apply(merchantId);
        for (AccountSeed seed : DEFAULT_CHART) {
            accounts.save(new Account(merchantId, seed.code(), seed.name(), seed.type(), currency));
        }
    }

    @Transactional
    public Account openAccount(UUID merchantId, String code, String name, AccountType type, String currency) {
        merchantScope.apply(merchantId);
        return accounts.save(new Account(merchantId, code, name, type, currency));
    }

    /**
     * Posts a balanced journal entry. Throws {@link LedgerImbalanceException} (422) if debits do
     * not equal credits, or {@link IllegalArgumentException} (400) for malformed input.
     */
    @Transactional
    public UUID postEntry(
            UUID merchantId, String description, String currency, List<LedgerLineInput> lineInputs) {
        merchantScope.apply(merchantId);
        if (lineInputs == null || lineInputs.size() < 2) {
            throw new IllegalArgumentException("a journal entry needs at least two lines");
        }

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerLineInput li : lineInputs) {
            if (li.amountMinor() <= 0) {
                throw new IllegalArgumentException("line amount must be positive");
            }
            Account account =
                    accounts.findById(li.accountId())
                            .orElseThrow(
                                    () -> new IllegalArgumentException("unknown account " + li.accountId()));
            if (!account.getMerchantId().equals(merchantId)) {
                throw new IllegalArgumentException("account does not belong to merchant");
            }
            BigDecimal amount = BigDecimal.valueOf(li.amountMinor());
            if (li.direction() == Direction.DEBIT) {
                debits = debits.add(amount);
            } else {
                credits = credits.add(amount);
            }
        }
        if (debits.signum() == 0 || debits.compareTo(credits) != 0) {
            throw new LedgerImbalanceException(
                    "entry not balanced: debits=" + debits + " credits=" + credits);
        }

        JournalEntry entry = entries.save(new JournalEntry(merchantId, description, currency));
        for (LedgerLineInput li : lineInputs) {
            lines.save(
                    new JournalLine(
                            entry.getId(), merchantId, li.accountId(), li.direction(), li.amountMinor()));
        }
        outbox.enqueue(merchantId, "ledger.entry.posted", payloadJson(entry, debits));
        return entry.getId();
    }

    /** The account's balance in minor units, expressed in its normal-side (natural) sign. */
    @Transactional(readOnly = true)
    public long balanceMinor(UUID merchantId, UUID accountId) {
        merchantScope.apply(merchantId);
        Account account =
                accounts.findById(accountId)
                        .orElseThrow(() -> new IllegalArgumentException("unknown account"));
        if (!account.getMerchantId().equals(merchantId)) {
            throw new IllegalArgumentException("account does not belong to merchant");
        }
        long signed = 0;
        for (JournalLine l : lines.findByAccountId(accountId)) {
            signed += (l.getDirection() == Direction.DEBIT ? l.getAmountMinor() : -l.getAmountMinor());
        }
        return account.getType().normalSide() == Direction.DEBIT ? signed : -signed;
    }

    @Transactional(readOnly = true)
    public List<Account> accountsOf(UUID merchantId) {
        merchantScope.apply(merchantId);
        return accounts.findByMerchantId(merchantId);
    }

    /** Resolves one of a merchant's accounts by code (e.g. "settlement", "revenue"). */
    @Transactional(readOnly = true)
    public Account accountByCode(UUID merchantId, String code) {
        merchantScope.apply(merchantId);
        return accounts
                .findByMerchantIdAndCode(merchantId, code)
                .orElseThrow(() -> new IllegalArgumentException("no account '" + code + "' for merchant"));
    }

    private String payloadJson(JournalEntry entry, BigDecimal totalMinor) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("entryId", entry.getId().toString());
            payload.put("amountMinor", totalMinor.longValueExact());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise ledger event", e);
        }
    }

    private record AccountSeed(String code, String name, AccountType type) {}
}
