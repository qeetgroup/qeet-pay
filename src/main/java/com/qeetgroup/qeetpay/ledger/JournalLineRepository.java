package com.qeetgroup.qeetpay.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {
    List<JournalLine> findByAccountId(UUID accountId);
}
