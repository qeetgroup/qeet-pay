package com.qeetgroup.qeetpay.offline;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpiLiteWalletRepository extends JpaRepository<UpiLiteWallet, UUID> {

    List<UpiLiteWallet> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Optional<UpiLiteWallet> findByMerchantIdAndCustomerRefAndStatus(
            UUID merchantId, String customerRef, UpiLiteWalletStatus status);
}
