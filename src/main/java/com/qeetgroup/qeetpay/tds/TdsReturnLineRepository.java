package com.qeetgroup.qeetpay.tds;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TdsReturnLineRepository extends JpaRepository<TdsReturnLine, UUID> {

    List<TdsReturnLine> findByReturnIdOrderByDeductedOnAscDeducteeNameAsc(UUID returnId);

    /** Clears the detail rows when a not-yet-filed return is re-prepared. */
    void deleteByReturnId(UUID returnId);
}
