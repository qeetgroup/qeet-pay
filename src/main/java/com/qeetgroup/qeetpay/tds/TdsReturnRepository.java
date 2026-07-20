package com.qeetgroup.qeetpay.tds;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TdsReturnRepository extends JpaRepository<TdsReturn, UUID> {

    Optional<TdsReturn> findByMerchantIdAndFormAndFyAndQuarter(
            UUID merchantId, TdsReturnForm form, String fy, String quarter);

    List<TdsReturn> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
