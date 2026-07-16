package com.qeetgroup.qeetpay.itc;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, UUID> {

    List<PurchaseInvoice> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
