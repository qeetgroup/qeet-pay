package com.qeetgroup.qeetpay.fraud;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence + read access for the merchant-scoped {@code fraud.fraud_decision} audit trail. Writes
 * happen inside the {@link FraudGatewayAuditor} transaction (fraud posture is recorded even for blocked
 * or rolled-back payments); reads back the trail for the {@link FraudController}. Every method binds the
 * merchant RLS scope so Postgres enforces tenant isolation.
 */
@Service
public class FraudDecisionService {

    private final FraudDecisionRepository repository;
    private final MerchantScope merchantScope;

    public FraudDecisionService(FraudDecisionRepository repository, MerchantScope merchantScope) {
        this.repository = repository;
        this.merchantScope = merchantScope;
    }

    /** Persist one fraud decision. Joins the caller's (audit) transaction. */
    @Transactional
    public FraudDecisionRecord record(
            UUID merchantId,
            UUID paymentId,
            int score,
            String decision,
            String topReasonsJson,
            String model,
            UUID aiDecisionId) {
        merchantScope.apply(merchantId);
        return repository.save(
                new FraudDecisionRecord(
                        merchantId, paymentId, score, decision, topReasonsJson, model, aiDecisionId));
    }

    @Transactional(readOnly = true)
    public List<FraudDecisionRecord> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return repository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public FraudDecisionRecord get(UUID merchantId, UUID id) {
        merchantScope.apply(merchantId);
        return repository
                .findById(id)
                .filter(r -> r.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new FraudDecisionNotFoundException("no fraud decision " + id));
    }
}
