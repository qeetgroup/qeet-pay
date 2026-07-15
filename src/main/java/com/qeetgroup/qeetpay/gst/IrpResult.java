package com.qeetgroup.qeetpay.gst;

import java.time.Instant;

/**
 * The IRP's response to an e-invoice registration (TAD §7.3): the Invoice Reference Number, the
 * acknowledgement number/date, and the signed QR code embedded on the invoice PDF.
 */
public record IrpResult(String irn, String ackNo, Instant ackDate, String signedQrCode) {}
