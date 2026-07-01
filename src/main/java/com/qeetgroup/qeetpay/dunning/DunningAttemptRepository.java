package com.qeetgroup.qeetpay.dunning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DunningAttemptRepository extends JpaRepository<DunningAttempt, UUID> {
    List<DunningAttempt> findBySubscriptionIdOrderByAttemptNumberAsc(UUID subscriptionId);
    int countBySubscriptionIdAndResult(UUID subscriptionId, String result);
}
