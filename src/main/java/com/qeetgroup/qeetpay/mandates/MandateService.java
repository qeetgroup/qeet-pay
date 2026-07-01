package com.qeetgroup.qeetpay.mandates;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.customers.Customer;
import com.qeetgroup.qeetpay.platform.customers.CustomerRepository;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mandate management (TAD Module 02). Handles UPI AutoPay and NACH recurring authorizations.
 * {@code debit()} charges against an ACTIVE mandate: validates the limit, posts a balanced
 * double-entry ledger entry (debit settlement / credit revenue), and records the debit.
 */
@Service
public class MandateService {

    private final MandateRepository mandates;
    private final MandateDebitRepository debits;
    private final CustomerRepository customers;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public MandateService(
            MandateRepository mandates,
            MandateDebitRepository debits,
            CustomerRepository customers,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.mandates = mandates;
        this.debits = debits;
        this.customers = customers;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Mandate create(
            UUID merchantId,
            String customerRef,
            MandateType type,
            long limitMinor,
            String currency,
            MandateFrequency frequency,
            LocalDate startDate,
            LocalDate endDate) {
        merchantScope.apply(merchantId);
        if (limitMinor <= 0) throw new IllegalArgumentException("limit must be positive");

        // Resolve or create customer from ref
        Customer customer = customers.findByMerchantIdAndRef(merchantId, customerRef)
                .orElseGet(() -> customers.save(new Customer(merchantId, customerRef)));

        Mandate mandate = mandates.save(
                new Mandate(merchantId, customer.getId(), type, limitMinor, currency, frequency, startDate, endDate));
        outbox.enqueue(merchantId, "mandate.created", json("mandateId", mandate.getId()));
        return mandate;
    }

    @Transactional
    public Mandate activate(UUID merchantId, UUID mandateId, String providerMandateId) {
        merchantScope.apply(merchantId);
        Mandate mandate = load(merchantId, mandateId);
        mandate.activate(providerMandateId == null ? "sandbox_mandate_" + mandateId.toString().substring(0, 8) : providerMandateId);
        outbox.enqueue(merchantId, "mandate.activated", json("mandateId", mandate.getId()));
        return mandate;
    }

    @Transactional
    public Mandate pause(UUID merchantId, UUID mandateId) {
        merchantScope.apply(merchantId);
        Mandate mandate = load(merchantId, mandateId);
        mandate.pause();
        outbox.enqueue(merchantId, "mandate.paused", json("mandateId", mandate.getId()));
        return mandate;
    }

    @Transactional
    public Mandate revoke(UUID merchantId, UUID mandateId) {
        merchantScope.apply(merchantId);
        Mandate mandate = load(merchantId, mandateId);
        mandate.revoke();
        outbox.enqueue(merchantId, "mandate.revoked", json("mandateId", mandate.getId()));
        return mandate;
    }

    /**
     * Charges {@code amountMinor} paise against an ACTIVE mandate. Amount must not exceed the
     * mandate's per-debit limit. Posts a balanced ledger entry (debit settlement / credit revenue)
     * and records the debit as an immutable {@link MandateDebit}.
     */
    @Transactional
    public MandateDebit debit(UUID merchantId, UUID mandateId, long amountMinor, String description) {
        merchantScope.apply(merchantId);
        if (amountMinor <= 0) throw new IllegalArgumentException("debit amount must be positive");

        Mandate mandate = load(merchantId, mandateId);
        if (mandate.getStatus() != MandateStatus.ACTIVE) {
            throw new IllegalStateException(
                    "can only debit an ACTIVE mandate; status=" + mandate.getStatus());
        }
        if (amountMinor > mandate.getLimitMinor()) {
            throw new IllegalArgumentException(
                    "debit amount " + amountMinor + " exceeds mandate limit " + mandate.getLimitMinor());
        }

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue    = ledger.accountByCode(merchantId, "revenue").getId();
        String desc = description != null ? description : "mandate debit " + mandateId;
        UUID entryId = ledger.postEntry(
                merchantId, desc, mandate.getCurrency(),
                List.of(
                        new LedgerLineInput(settlement, Direction.DEBIT,  amountMinor),
                        new LedgerLineInput(revenue,    Direction.CREDIT, amountMinor)));

        MandateDebit debitRecord = debits.save(new MandateDebit(
                mandateId, merchantId, amountMinor, mandate.getCurrency(),
                "SUCCEEDED", null, null, entryId));
        outbox.enqueue(merchantId, "mandate.debited", json("mandateDebitId", debitRecord.getId()));
        return debitRecord;
    }

    @Transactional(readOnly = true)
    public List<Mandate> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return mandates.findByMerchantId(merchantId);
    }

    @Transactional(readOnly = true)
    public Mandate get(UUID merchantId, UUID mandateId) {
        merchantScope.apply(merchantId);
        return load(merchantId, mandateId);
    }

    @Transactional(readOnly = true)
    public List<MandateDebit> debitsOf(UUID merchantId, UUID mandateId) {
        merchantScope.apply(merchantId);
        load(merchantId, mandateId); // ownership check
        return debits.findByMandateId(mandateId);
    }

    private Mandate load(UUID merchantId, UUID mandateId) {
        return mandates.findById(mandateId)
                .filter(m -> m.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new MandateNotFoundException("no mandate " + mandateId));
    }

    private String json(String key, UUID value) {
        try {
            return objectMapper.writeValueAsString(Map.of(key, value.toString()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise mandate event", e);
        }
    }
}
