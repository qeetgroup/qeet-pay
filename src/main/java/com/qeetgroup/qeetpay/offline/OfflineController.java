package com.qeetgroup.qeetpay.offline;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Offline &amp; Rural payments API (PRD Module 15): Bharat QR, UPI Lite, UPI 123Pay, and POS /
 * Tap-to-Pay. All rails are simulated (sandbox). Money movement posts to the ledger; QR generation
 * does not.
 */
@RestController
@RequestMapping("/v1/offline")
public class OfflineController {

    private final BharatQrService bharatQr;
    private final UpiLiteService upiLite;
    private final Pay123Service pay123;
    private final PosService pos;

    public OfflineController(
            BharatQrService bharatQr, UpiLiteService upiLite, Pay123Service pay123, PosService pos) {
        this.bharatQr = bharatQr;
        this.upiLite = upiLite;
        this.pay123 = pay123;
        this.pos = pos;
    }

    // ── Bharat QR ─────────────────────────────────────────────────────────────

    @PostMapping("/bharat-qr")
    public ResponseEntity<BharatQrView> generateQr(@Valid @RequestBody BharatQrRequest req) {
        BharatQr qr =
                bharatQr.generate(
                        MerchantContext.require(),
                        req.amountMinor(),
                        req.currency(),
                        req.merchantName(),
                        req.reference());
        return ResponseEntity.status(HttpStatus.CREATED).body(BharatQrView.of(qr));
    }

    @GetMapping("/bharat-qr")
    public List<BharatQrView> listQr() {
        return bharatQr.list(MerchantContext.require()).stream().map(BharatQrView::of).toList();
    }

    // ── UPI Lite ──────────────────────────────────────────────────────────────

    @PostMapping("/upi-lite/wallets")
    public ResponseEntity<WalletView> createWallet(@Valid @RequestBody CreateWalletRequest req) {
        UpiLiteWallet w =
                upiLite.createWallet(MerchantContext.require(), req.customerRef(), req.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(WalletView.of(w));
    }

    @GetMapping("/upi-lite/wallets")
    public List<WalletView> listWallets() {
        return upiLite.listWallets(MerchantContext.require()).stream().map(WalletView::of).toList();
    }

    @PostMapping("/upi-lite/wallets/{walletId}/topup")
    public ResponseEntity<TxnView> topUp(
            @PathVariable UUID walletId, @Valid @RequestBody AmountRequest req) {
        UpiLiteTxn t = upiLite.topUp(MerchantContext.require(), walletId, req.amountMinor());
        return ResponseEntity.status(HttpStatus.CREATED).body(TxnView.of(t));
    }

    @PostMapping("/upi-lite/wallets/{walletId}/spend")
    public ResponseEntity<TxnView> spend(
            @PathVariable UUID walletId, @Valid @RequestBody AmountRequest req) {
        UpiLiteTxn t = upiLite.spend(MerchantContext.require(), walletId, req.amountMinor());
        return ResponseEntity.status(HttpStatus.CREATED).body(TxnView.of(t));
    }

    @GetMapping("/upi-lite/wallets/{walletId}")
    public WalletWithTxnsView getWallet(@PathVariable UUID walletId) {
        return WalletWithTxnsView.of(upiLite.getWallet(MerchantContext.require(), walletId));
    }

    // ── UPI 123Pay ────────────────────────────────────────────────────────────

    @PostMapping("/123pay/intents")
    public ResponseEntity<IntentView> createIntent(@Valid @RequestBody Intent123Request req) {
        Pay123Intent i =
                pay123.createIntent(
                        MerchantContext.require(), req.payerMobile(), req.amountMinor(), req.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(IntentView.of(i));
    }

    @GetMapping("/123pay/intents")
    public List<IntentView> listIntents() {
        return pay123.list(MerchantContext.require()).stream().map(IntentView::of).toList();
    }

    @PostMapping("/123pay/intents/{intentId}/confirm")
    public IntentView confirmIntent(@PathVariable UUID intentId) {
        return IntentView.of(pay123.confirmIntent(MerchantContext.require(), intentId));
    }

    // ── POS / Tap-to-Pay ──────────────────────────────────────────────────────

    @PostMapping("/pos/devices")
    public ResponseEntity<DeviceView> registerDevice(@Valid @RequestBody RegisterDeviceRequest req) {
        PosDevice d = pos.registerDevice(MerchantContext.require(), req.label(), req.serialNo());
        return ResponseEntity.status(HttpStatus.CREATED).body(DeviceView.of(d));
    }

    @GetMapping("/pos/devices")
    public List<DeviceView> listDevices() {
        return pos.listDevices(MerchantContext.require()).stream().map(DeviceView::of).toList();
    }

    @PostMapping("/pos/transactions")
    public ResponseEntity<PosTxnView> capture(@Valid @RequestBody PosCaptureRequest req) {
        PosCaptureMethod method =
                req.method() == null ? PosCaptureMethod.TAP : PosCaptureMethod.valueOf(req.method());
        PosTransaction t =
                pos.capture(
                        MerchantContext.require(),
                        req.deviceId(),
                        req.amountMinor(),
                        req.currency(),
                        method);
        return ResponseEntity.status(HttpStatus.CREATED).body(PosTxnView.of(t));
    }

    @GetMapping("/pos/transactions")
    public List<PosTxnView> listTransactions() {
        return pos.listTransactions(MerchantContext.require()).stream().map(PosTxnView::of).toList();
    }

    // ── Requests ──────────────────────────────────────────────────────────────

    public record BharatQrRequest(
            @Positive Long amountMinor, String currency, String merchantName, String reference) {}

    public record CreateWalletRequest(@NotBlank String customerRef, String currency) {}

    public record AmountRequest(@NotNull @Positive Long amountMinor) {}

    public record Intent123Request(
            @NotBlank String payerMobile, @NotNull @Positive Long amountMinor, String currency) {}

    public record RegisterDeviceRequest(@NotBlank String label, @NotBlank String serialNo) {}

    public record PosCaptureRequest(
            @NotNull UUID deviceId, @NotNull @Positive Long amountMinor, String currency, String method) {}

    // ── Views ─────────────────────────────────────────────────────────────────

    public record BharatQrView(
            String id, Long amountMinor, boolean dynamic, String currency, String merchantName,
            String reference, String payload, Instant createdAt) {
        static BharatQrView of(BharatQr q) {
            return new BharatQrView(
                    q.getId().toString(), q.getAmountMinor(), q.isDynamic(), q.getCurrency(),
                    q.getMerchantName(), q.getReference(), q.getPayload(), q.getCreatedAt());
        }
    }

    public record WalletView(
            String id, String customerRef, long balanceMinor, String currency, String status,
            Instant createdAt, Instant closedAt) {
        static WalletView of(UpiLiteWallet w) {
            return new WalletView(
                    w.getId().toString(), w.getCustomerRef(), w.getBalanceMinor(), w.getCurrency(),
                    w.getStatus().name(), w.getCreatedAt(), w.getClosedAt());
        }
    }

    public record TxnView(
            String id, String walletId, String type, long amountMinor, String currency,
            String ledgerEntryId, Instant createdAt) {
        static TxnView of(UpiLiteTxn t) {
            return new TxnView(
                    t.getId().toString(), t.getWalletId().toString(), t.getType().name(),
                    t.getAmountMinor(), t.getCurrency(), t.getLedgerEntryId().toString(),
                    t.getCreatedAt());
        }
    }

    public record WalletWithTxnsView(WalletView wallet, List<TxnView> txns) {
        static WalletWithTxnsView of(UpiLiteService.WalletWithTxns w) {
            return new WalletWithTxnsView(
                    WalletView.of(w.wallet()), w.txns().stream().map(TxnView::of).toList());
        }
    }

    public record IntentView(
            String id, String payerMobile, long amountMinor, String currency, String status,
            String ledgerEntryId, Instant createdAt, Instant confirmedAt) {
        static IntentView of(Pay123Intent i) {
            return new IntentView(
                    i.getId().toString(), i.getPayerMobile(), i.getAmountMinor(), i.getCurrency(),
                    i.getStatus().name(),
                    i.getLedgerEntryId() == null ? null : i.getLedgerEntryId().toString(),
                    i.getCreatedAt(), i.getConfirmedAt());
        }
    }

    public record DeviceView(
            String id, String label, String serialNo, String status, Instant createdAt) {
        static DeviceView of(PosDevice d) {
            return new DeviceView(
                    d.getId().toString(), d.getLabel(), d.getSerialNo(), d.getStatus().name(),
                    d.getCreatedAt());
        }
    }

    public record PosTxnView(
            String id, String deviceId, long amountMinor, String currency, String method, String rrn,
            String ledgerEntryId, Instant createdAt) {
        static PosTxnView of(PosTransaction t) {
            return new PosTxnView(
                    t.getId().toString(), t.getDeviceId().toString(), t.getAmountMinor(),
                    t.getCurrency(), t.getMethod().name(), t.getRrn(),
                    t.getLedgerEntryId().toString(), t.getCreatedAt());
        }
    }
}
