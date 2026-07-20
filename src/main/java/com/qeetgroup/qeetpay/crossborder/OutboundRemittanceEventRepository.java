package com.qeetgroup.qeetpay.crossborder;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundRemittanceEventRepository extends JpaRepository<OutboundRemittanceEvent, UUID> {

    List<OutboundRemittanceEvent> findByRemittanceIdOrderByCreatedAt(UUID remittanceId);
}
