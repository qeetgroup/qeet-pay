package com.qeetgroup.qeetpay.cards;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, UUID> {

    List<CardTransaction> findByCardIdOrderByCreatedAt(UUID cardId);
}
