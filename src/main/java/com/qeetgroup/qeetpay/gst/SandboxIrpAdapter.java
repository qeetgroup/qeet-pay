package com.qeetgroup.qeetpay.gst;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Sandbox e-invoicing adapter (TAD §7.3, §11.1) — deterministic, offline stand-in for a live IRP.
 * The IRN follows the GST spec's shape: the SHA-256 hex of {@code SupplierGSTIN + FY + DocType + DocNo}
 * (64 chars). The signed QR is a base64 payload of the mandatory QR fields (in production this is a
 * JWS the IRP signs). Active whenever no live adapter bean is present.
 */
@Component
@ConditionalOnMissingBean(name = "liveIrpAdapter")
public class SandboxIrpAdapter implements IrpAdapter {

    private final AtomicLong ackCounter = new AtomicLong(1_000_000_000L);

    @Override
    public IrpResult generateIrn(GstInvoice invoice, List<GstInvoiceLine> lines) {
        String fy = GstInvoiceService.fiscalYear(invoice.getIssuedAt());
        String irn = sha256Hex(invoice.getSupplierGstin() + fy + "INV" + invoice.getInvoiceNumber());
        String ackNo = Long.toString(ackCounter.getAndIncrement());
        Instant ackDate = Instant.now();
        String signedQr = signedQr(invoice, irn, ackNo);
        return new IrpResult(irn, ackNo, ackDate, signedQr);
    }

    @Override
    public void cancelIrn(String irn, String reason) {
        // Sandbox: acknowledge-and-drop. A live IRP would validate the 24h window + reason code.
    }

    /** Base64 of the mandatory e-invoice QR fields (a JWS in production). */
    private String signedQr(GstInvoice invoice, String irn, String ackNo) {
        String payload =
                "{\"SellerGstin\":\"" + invoice.getSupplierGstin() + "\","
                        + "\"BuyerGstin\":\"" + (invoice.getBuyerGstin() == null ? "URP" : invoice.getBuyerGstin()) + "\","
                        + "\"DocNo\":\"" + invoice.getInvoiceNumber() + "\","
                        + "\"DocTyp\":\"INV\","
                        + "\"TotInvVal\":" + minorToMajor(invoice.getTotalMinor()) + ","
                        + "\"Irn\":\"" + irn + "\","
                        + "\"AckNo\":\"" + ackNo + "\"}";
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String minorToMajor(long minor) {
        return (minor / 100) + "." + String.format("%02d", Math.abs(minor % 100));
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
