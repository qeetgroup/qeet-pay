package com.qeetgroup.qeetpay.gst;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InvoiceCounterRepository extends JpaRepository<InvoiceCounter, UUID> {

    /** Pessimistic write-lock so concurrent invoice numbering for a merchant/FY is serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvoiceCounter> findByMerchantIdAndFiscalYear(UUID merchantId, String fiscalYear);
}
