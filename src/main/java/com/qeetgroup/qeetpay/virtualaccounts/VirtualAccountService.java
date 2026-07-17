package com.qeetgroup.qeetpay.virtualaccounts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Virtual accounts (PRD Module 01 "Virtual Accounts", TAD §5). Mints a unique account number + IFSC
 * per customer; an inbound credit is auto-reconciled to the ledger with the canonical money-in entry
 * (debit {@code settlement} / credit {@code revenue}), the same posting a payment capture makes.
 * Credits are idempotent on the bank UTR so a replayed bank webhook never double-credits. All writes
 * are outbox-published and merchant-scoped via RLS.
 */
@Service
public class VirtualAccountService {

    /** Partner-bank IFSC for minted virtual accounts (sandbox constant). */
    private static final String PARTNER_IFSC = "QEET0000001";

    private final VirtualAccountRepository accounts;
    private final VirtualAccountCreditRepository credits;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public VirtualAccountService(
            VirtualAccountRepository accounts,
            VirtualAccountCreditRepository credits,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.credits = credits;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Mints (or returns the existing ACTIVE) virtual account for a customer. */
    @Transactional
    public VirtualAccount mintAccount(UUID merchantId, String customerRef) {
        merchantScope.apply(merchantId);
        if (customerRef == null || customerRef.isBlank()) {
            throw new IllegalArgumentException("customerRef is required");
        }
        return accounts
                .findByMerchantIdAndCustomerRefAndStatus(merchantId, customerRef, VirtualAccountStatus.ACTIVE)
                .orElseGet(() -> {
                    VirtualAccount va =
                            accounts.save(new VirtualAccount(merchantId, customerRef, generateVaNumber(), PARTNER_IFSC));
                    outbox.enqueue(merchantId, "va.minted", accountJson(va));
                    return va;
                });
    }

    @Transactional
    public VirtualAccount closeAccount(UUID merchantId, UUID vaId) {
        merchantScope.apply(merchantId);
        VirtualAccount va = load(merchantId, vaId);
        va.close();
        return accounts.save(va);
    }

    /**
     * Ingests an inbound bank/UPI credit to a virtual account and auto-reconciles it to the ledger.
     * Idempotent on {@code utr}: a repeated UTR returns the existing credit without re-posting.
     */
    @Transactional
    public VirtualAccountCredit ingestCredit(
            UUID merchantId, UUID vaId, long amountMinor, String currency, String utr,
            String payerName, String payerRef) {
        merchantScope.apply(merchantId);
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("credit amount must be positive");
        }
        if (utr == null || utr.isBlank()) {
            throw new IllegalArgumentException("utr is required");
        }
        var existing = credits.findByMerchantIdAndUtr(merchantId, utr);
        if (existing.isPresent()) {
            return existing.get(); // idempotent replay
        }
        VirtualAccount va = load(merchantId, vaId);
        if (!va.isActive()) {
            throw new IllegalStateException("virtual account is closed");
        }

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId,
                        "va credit " + utr,
                        currency,
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, amountMinor),
                                new LedgerLineInput(revenue, Direction.CREDIT, amountMinor)));

        VirtualAccountCredit credit =
                credits.save(
                        new VirtualAccountCredit(
                                va.getId(), merchantId, amountMinor, currency, utr, payerName, payerRef, entryId));
        outbox.enqueue(merchantId, "va.credit.reconciled", creditJson(va, credit));
        return credit;
    }

    @Transactional(readOnly = true)
    public List<VirtualAccount> listAccounts(UUID merchantId) {
        merchantScope.apply(merchantId);
        return accounts.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public AccountWithCredits getAccount(UUID merchantId, UUID vaId) {
        merchantScope.apply(merchantId);
        VirtualAccount va = load(merchantId, vaId);
        return new AccountWithCredits(va, credits.findByVaIdOrderByCreditedAt(vaId));
    }

    private VirtualAccount load(UUID merchantId, UUID vaId) {
        return accounts
                .findById(vaId)
                .filter(a -> a.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new VirtualAccountNotFoundException("no virtual account " + vaId));
    }

    /** A 16-hex-char account number (effectively unique); a real VA maps to the nodal account. */
    private String generateVaNumber() {
        return "QV" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    /** A virtual account plus its credit history. */
    public record AccountWithCredits(VirtualAccount account, List<VirtualAccountCredit> credits) {}

    private String accountJson(VirtualAccount va) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("vaId", va.getId().toString());
        b.put("vaNumber", va.getVaNumber());
        b.put("ifsc", va.getIfsc());
        b.put("customerRef", va.getCustomerRef());
        return write(b);
    }

    private String creditJson(VirtualAccount va, VirtualAccountCredit c) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("vaId", va.getId().toString());
        b.put("creditId", c.getId().toString());
        b.put("amountMinor", c.getAmountMinor());
        b.put("utr", c.getUtr());
        b.put("ledgerEntryId", c.getLedgerEntryId().toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise virtual-account event", e);
        }
    }
}
