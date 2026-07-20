package com.qeetgroup.qeetpay.ondc;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OndcSettlementLineRepository extends JpaRepository<OndcSettlementLine, UUID> {

    List<OndcSettlementLine> findByOrderId(UUID orderId);
}
