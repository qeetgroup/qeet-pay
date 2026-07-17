package com.qeetgroup.qeetpay.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageDispatchRepository extends JpaRepository<MessageDispatch, UUID> {

    List<MessageDispatch> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
