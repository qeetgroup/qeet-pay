package com.qeetgroup.qeetpay.filing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstReturnLineRepository extends JpaRepository<GstReturnLine, UUID> {

    List<GstReturnLine> findByReturnIdOrderByInvoiceNumber(UUID returnId);

    /** Clears the worksheet lines when a not-yet-filed return is re-prepared. */
    void deleteByReturnId(UUID returnId);
}
