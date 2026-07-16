package com.qeetgroup.qeetpay.gst;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstInvoiceRepository extends JpaRepository<GstInvoice, UUID> {

    /** Invoices issued in the half-open window [from, to) — the tax-period slice for return filing. */
    List<GstInvoice> findByMerchantIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThanOrderByIssuedAt(
            UUID merchantId, Instant from, Instant to);

    /** A merchant's invoices, newest-issued first. */
    List<GstInvoice> findByMerchantIdOrderByIssuedAtDesc(UUID merchantId);
}
