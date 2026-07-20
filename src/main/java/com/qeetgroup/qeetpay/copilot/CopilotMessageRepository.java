package com.qeetgroup.qeetpay.copilot;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CopilotMessageRepository extends JpaRepository<CopilotMessage, UUID> {

    List<CopilotMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
