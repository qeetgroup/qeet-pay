package com.qeetgroup.qeetpay.offline;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpiLiteTxnRepository extends JpaRepository<UpiLiteTxn, UUID> {

    List<UpiLiteTxn> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    List<UpiLiteTxn> findByWalletIdAndTypeAndCreatedAtAfter(
            UUID walletId, UpiLiteTxnType type, Instant since);
}
