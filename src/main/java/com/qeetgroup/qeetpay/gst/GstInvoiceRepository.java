package com.qeetgroup.qeetpay.gst;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstInvoiceRepository extends JpaRepository<GstInvoice, UUID> {}
