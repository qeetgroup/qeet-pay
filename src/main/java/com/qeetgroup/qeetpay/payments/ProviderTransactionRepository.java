package com.qeetgroup.qeetpay.payments;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderTransactionRepository extends JpaRepository<ProviderTransaction, UUID> {}
