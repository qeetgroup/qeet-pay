package com.qeetgroup.qeetpay.copilot;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CopilotConversationRepository extends JpaRepository<CopilotConversation, UUID> {

    List<CopilotConversation> findByMerchantIdOrderByUpdatedAtDesc(UUID merchantId);
}
