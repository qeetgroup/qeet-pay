package com.qeetgroup.qeetpay.crossborder;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportInvoiceRepository extends JpaRepository<ExportInvoice, UUID> {

    List<ExportInvoice> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
