package com.qeetgroup.qeetpay.offline;

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
 * POS / Tap-to-Pay (PRD Module 15.4) — in-person acceptance on a registered Android-POS or soft-POS
 * (Tap-to-Pay on phone) device. A capture posts the canonical money-in entry (debit {@code settlement}
 * / credit {@code revenue}), identical to a payment capture. Sandbox: the acquirer RRN is synthesised.
 * Merchant-scoped via RLS; outbox-published.
 */
@Service
public class PosService {

    private final PosDeviceRepository devices;
    private final PosTransactionRepository transactions;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public PosService(
            PosDeviceRepository devices,
            PosTransactionRepository transactions,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.devices = devices;
        this.transactions = transactions;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PosDevice registerDevice(UUID merchantId, String label, String serialNo) {
        merchantScope.apply(merchantId);
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label is required");
        }
        if (serialNo == null || serialNo.isBlank()) {
            throw new IllegalArgumentException("serialNo is required");
        }
        PosDevice device = devices.save(new PosDevice(merchantId, label, serialNo));
        outbox.enqueue(merchantId, "pos.device.registered", deviceJson(device));
        return device;
    }

    @Transactional(readOnly = true)
    public List<PosDevice> listDevices(UUID merchantId) {
        merchantScope.apply(merchantId);
        return devices.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /** Captures an in-person payment on a device, posting money-in to the ledger. */
    @Transactional
    public PosTransaction capture(
            UUID merchantId, UUID deviceId, long amountMinor, String currency, PosCaptureMethod method) {
        merchantScope.apply(merchantId);
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        PosDevice device =
                devices
                        .findById(deviceId)
                        .filter(d -> d.getMerchantId().equals(merchantId))
                        .orElseThrow(() -> new OfflineNotFoundException("no POS device " + deviceId));
        if (!device.isActive()) {
            throw new IllegalStateException("POS device is inactive");
        }
        String cur = (currency == null || currency.isBlank()) ? "INR" : currency;
        PosCaptureMethod cm = method == null ? PosCaptureMethod.TAP : method;

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId,
                        "pos capture " + deviceId,
                        cur,
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, amountMinor),
                                new LedgerLineInput(revenue, Direction.CREDIT, amountMinor)));

        PosTransaction txn =
                transactions.save(
                        new PosTransaction(
                                deviceId, merchantId, amountMinor, cur, cm, generateRrn(), entryId));
        outbox.enqueue(merchantId, "pos.captured", txnJson(txn));
        return txn;
    }

    @Transactional(readOnly = true)
    public List<PosTransaction> listTransactions(UUID merchantId) {
        merchantScope.apply(merchantId);
        return transactions.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    private String generateRrn() {
        return "RRN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String deviceJson(PosDevice d) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("deviceId", d.getId().toString());
        b.put("label", d.getLabel());
        b.put("serialNo", d.getSerialNo());
        return write(b);
    }

    private String txnJson(PosTransaction t) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("posTxnId", t.getId().toString());
        b.put("deviceId", t.getDeviceId().toString());
        b.put("amountMinor", t.getAmountMinor());
        b.put("method", t.getMethod().name());
        b.put("rrn", t.getRrn());
        b.put("ledgerEntryId", t.getLedgerEntryId().toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise pos event", e);
        }
    }
}
