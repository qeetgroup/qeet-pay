package com.qeetgroup.qeetpay.offline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UPI Lite (PRD Module 15.1) — an on-device low-value wallet. A top-up loads value (money-in: debit
 * {@code settlement} / credit {@code liability}); a spend draws it down at the merchant (debit
 * {@code liability} / credit {@code revenue}). NPCI limits are enforced before any posting:
 * per-transaction ≤ ₹500 (50,000 paise) and per-day ≤ ₹2,000 (200,000 paise). Merchant-scoped via
 * RLS; every movement is posted to the ledger and outbox-published.
 */
@Service
public class UpiLiteService {

    /** Per-transaction spend limit: ₹500. */
    static final long PER_TXN_LIMIT_MINOR = 50_000L;

    /** Per-day cumulative spend limit: ₹2,000. */
    static final long PER_DAY_LIMIT_MINOR = 200_000L;

    /** UPI Lite limits reset on the Indian calendar day. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final UpiLiteWalletRepository wallets;
    private final UpiLiteTxnRepository txns;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public UpiLiteService(
            UpiLiteWalletRepository wallets,
            UpiLiteTxnRepository txns,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.wallets = wallets;
        this.txns = txns;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Creates (or returns the existing ACTIVE) UPI Lite wallet for a customer. */
    @Transactional
    public UpiLiteWallet createWallet(UUID merchantId, String customerRef, String currency) {
        merchantScope.apply(merchantId);
        if (customerRef == null || customerRef.isBlank()) {
            throw new IllegalArgumentException("customerRef is required");
        }
        String cur = (currency == null || currency.isBlank()) ? "INR" : currency;
        return wallets
                .findByMerchantIdAndCustomerRefAndStatus(
                        merchantId, customerRef, UpiLiteWalletStatus.ACTIVE)
                .orElseGet(
                        () -> {
                            UpiLiteWallet w =
                                    wallets.save(new UpiLiteWallet(merchantId, customerRef, cur));
                            outbox.enqueue(merchantId, "upi_lite.wallet.created", walletJson(w));
                            return w;
                        });
    }

    /** Loads value into a wallet: money-in (debit settlement / credit liability). */
    @Transactional
    public UpiLiteTxn topUp(UUID merchantId, UUID walletId, long amountMinor) {
        merchantScope.apply(merchantId);
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("top-up amount must be positive");
        }
        UpiLiteWallet wallet = requireActive(merchantId, walletId);

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID liability = ledger.accountByCode(merchantId, "liability").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId,
                        "upi-lite topup " + walletId,
                        wallet.getCurrency(),
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, amountMinor),
                                new LedgerLineInput(liability, Direction.CREDIT, amountMinor)));

        wallet.topUp(amountMinor);
        wallets.save(wallet);
        UpiLiteTxn txn =
                txns.save(
                        new UpiLiteTxn(
                                walletId, merchantId, UpiLiteTxnType.TOPUP, amountMinor,
                                wallet.getCurrency(), entryId));
        outbox.enqueue(merchantId, "upi_lite.topped_up", txnJson(wallet, txn));
        return txn;
    }

    /**
     * Spends from a wallet: debit {@code liability} / credit {@code revenue}. Enforces per-transaction
     * (₹500) and per-day (₹2,000) limits and sufficient balance before posting.
     */
    @Transactional
    public UpiLiteTxn spend(UUID merchantId, UUID walletId, long amountMinor) {
        merchantScope.apply(merchantId);
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("spend amount must be positive");
        }
        if (amountMinor > PER_TXN_LIMIT_MINOR) {
            throw new IllegalArgumentException(
                    "UPI Lite per-transaction limit of ₹500 (50000 paise) exceeded");
        }
        UpiLiteWallet wallet = requireActive(merchantId, walletId);

        long spentToday = spentTodayMinor(walletId);
        if (spentToday + amountMinor > PER_DAY_LIMIT_MINOR) {
            throw new IllegalArgumentException(
                    "UPI Lite per-day limit of ₹2,000 (200000 paise) exceeded");
        }
        if (amountMinor > wallet.getBalanceMinor()) {
            throw new IllegalArgumentException("insufficient UPI Lite balance");
        }

        UUID liability = ledger.accountByCode(merchantId, "liability").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId,
                        "upi-lite spend " + walletId,
                        wallet.getCurrency(),
                        List.of(
                                new LedgerLineInput(liability, Direction.DEBIT, amountMinor),
                                new LedgerLineInput(revenue, Direction.CREDIT, amountMinor)));

        wallet.spend(amountMinor);
        wallets.save(wallet);
        UpiLiteTxn txn =
                txns.save(
                        new UpiLiteTxn(
                                walletId, merchantId, UpiLiteTxnType.SPEND, amountMinor,
                                wallet.getCurrency(), entryId));
        outbox.enqueue(merchantId, "upi_lite.spent", txnJson(wallet, txn));
        return txn;
    }

    @Transactional(readOnly = true)
    public List<UpiLiteWallet> listWallets(UUID merchantId) {
        merchantScope.apply(merchantId);
        return wallets.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public WalletWithTxns getWallet(UUID merchantId, UUID walletId) {
        merchantScope.apply(merchantId);
        UpiLiteWallet wallet = load(merchantId, walletId);
        return new WalletWithTxns(wallet, txns.findByWalletIdOrderByCreatedAtDesc(walletId));
    }

    /** Sum of today's (IST) spends for the wallet, used to enforce the per-day cap. */
    private long spentTodayMinor(UUID walletId) {
        Instant startOfDay = LocalDate.now(IST).atStartOfDay(IST).toInstant();
        return txns
                .findByWalletIdAndTypeAndCreatedAtAfter(walletId, UpiLiteTxnType.SPEND, startOfDay)
                .stream()
                .mapToLong(UpiLiteTxn::getAmountMinor)
                .sum();
    }

    private UpiLiteWallet requireActive(UUID merchantId, UUID walletId) {
        UpiLiteWallet wallet = load(merchantId, walletId);
        if (!wallet.isActive()) {
            throw new IllegalStateException("UPI Lite wallet is closed");
        }
        return wallet;
    }

    private UpiLiteWallet load(UUID merchantId, UUID walletId) {
        return wallets
                .findById(walletId)
                .filter(w -> w.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new OfflineNotFoundException("no UPI Lite wallet " + walletId));
    }

    /** A wallet plus its movement history. */
    public record WalletWithTxns(UpiLiteWallet wallet, List<UpiLiteTxn> txns) {}

    private String walletJson(UpiLiteWallet w) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("walletId", w.getId().toString());
        b.put("customerRef", w.getCustomerRef());
        b.put("balanceMinor", w.getBalanceMinor());
        return write(b);
    }

    private String txnJson(UpiLiteWallet w, UpiLiteTxn t) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("walletId", w.getId().toString());
        b.put("txnId", t.getId().toString());
        b.put("type", t.getType().name());
        b.put("amountMinor", t.getAmountMinor());
        b.put("balanceMinor", w.getBalanceMinor());
        b.put("ledgerEntryId", t.getLedgerEntryId().toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise upi-lite event", e);
        }
    }
}
