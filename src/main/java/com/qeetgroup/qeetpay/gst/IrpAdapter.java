package com.qeetgroup.qeetpay.gst;

import java.util.List;

/**
 * Pluggable e-invoicing backend — sandbox or a live IRP (GSTN / NIC IRP, or an ASP/GSP such as
 * ClearTax) (TAD §7.3). Registers an issued invoice to obtain an IRN + signed QR, and cancels an
 * IRN within the regulatory window.
 */
public interface IrpAdapter {

    /** Registers {@code invoice} (with its {@code lines}) at the IRP and returns the IRN payload. */
    IrpResult generateIrn(GstInvoice invoice, List<GstInvoiceLine> lines);

    /** Cancels a previously generated IRN with a reason code (e.g. "1" = duplicate, "2" = data entry). */
    void cancelIrn(String irn, String reason);
}
