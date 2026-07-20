package com.qeetgroup.qeetpay.gst;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstInvoiceLineRepository extends JpaRepository<GstInvoiceLine, UUID> {
    List<GstInvoiceLine> findByInvoiceId(UUID invoiceId);

    /** A merchant's invoice lines carrying a given HSN/SAC — the exposure the reg-change radar scans. */
    List<GstInvoiceLine> findByMerchantIdAndHsnSac(UUID merchantId, String hsnSac);
}
