package com.qeetgroup.qeetpay.gst;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstNoteRepository extends JpaRepository<GstNote, UUID> {
    List<GstNote> findByMerchantIdOrderByIssuedAtDesc(UUID merchantId);
    List<GstNote> findByOriginalInvoiceId(UUID originalInvoiceId);
}
