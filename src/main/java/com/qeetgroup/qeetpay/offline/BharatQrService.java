package com.qeetgroup.qeetpay.offline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bharat QR (PRD Module 15.3). Generates a single unified QR whose payload accepts UPI + RuPay + Visa
 * + Mastercard in one code. A supplied amount makes the QR dynamic; an absent amount makes it a
 * static/open-amount QR. Generating a QR is not a payment — nothing is posted to the ledger; the
 * capture arrives later as a normal payment/webhook. Sandbox: the VPA + payload are synthesised.
 */
@Service
public class BharatQrService {

    private static final String DEFAULT_MERCHANT_NAME = "Qeet Merchant";

    private final BharatQrRepository qrs;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public BharatQrService(
            BharatQrRepository qrs,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.qrs = qrs;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BharatQr generate(
            UUID merchantId, Long amountMinor, String currency, String merchantName, String reference) {
        merchantScope.apply(merchantId);
        if (amountMinor != null && amountMinor <= 0) {
            throw new IllegalArgumentException("amount, when supplied, must be positive");
        }
        String cur = (currency == null || currency.isBlank()) ? "INR" : currency;
        String name = (merchantName == null || merchantName.isBlank()) ? DEFAULT_MERCHANT_NAME : merchantName;
        String ref =
                (reference == null || reference.isBlank())
                        ? "qr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                        : reference;

        String payload = buildPayload(merchantId, name, amountMinor, cur, ref);
        BharatQr qr = qrs.save(new BharatQr(merchantId, name, amountMinor, cur, ref, payload));
        outbox.enqueue(merchantId, "bharat_qr.generated", qrJson(qr));
        return qr;
    }

    @Transactional(readOnly = true)
    public List<BharatQr> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return qrs.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /** Builds the unified Bharat QR payload: a UPI intent string plus the accepted card networks. */
    private String buildPayload(
            UUID merchantId, String merchantName, Long amountMinor, String currency, String reference) {
        String vpa =
                "qeet." + merchantId.toString().replace("-", "").substring(0, 10) + "@qeetpay";
        StringBuilder upi =
                new StringBuilder("upi://pay?pa=")
                        .append(vpa)
                        .append("&pn=")
                        .append(merchantName.replace(" ", "%20"))
                        .append("&cu=")
                        .append(currency)
                        .append("&tn=")
                        .append(reference)
                        .append("&mode=15"); // 15 = QR-code initiated
        if (amountMinor != null) {
            BigDecimal rupees =
                    BigDecimal.valueOf(amountMinor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
            upi.append("&am=").append(rupees.toPlainString());
        }
        return "BHARATQR|"
                + upi
                + "|NET:UPI,RUPAY,VISA,MASTERCARD"
                + "|MID:"
                + merchantId.toString().toUpperCase(Locale.ROOT)
                + "|REF:"
                + reference;
    }

    private String qrJson(BharatQr qr) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("qrId", qr.getId().toString());
        b.put("amountMinor", qr.getAmountMinor());
        b.put("dynamic", qr.isDynamic());
        b.put("reference", qr.getReference());
        try {
            return objectMapper.writeValueAsString(b);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise bharat-qr event", e);
        }
    }
}
