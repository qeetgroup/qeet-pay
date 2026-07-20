package com.qeetgroup.qeetpay.gst;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegulatoryChangeRepository extends JpaRepository<RegulatoryChange, UUID> {

    /** A merchant's announced changes, soonest-effective first. */
    List<RegulatoryChange> findByMerchantIdOrderByEffectiveDateAsc(UUID merchantId);
}
