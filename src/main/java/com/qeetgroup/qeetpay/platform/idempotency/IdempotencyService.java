package com.qeetgroup.qeetpay.platform.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and replays mutating-request outcomes by ({@code merchantId}, {@code Idempotency-Key}).
 * Callers look up first; on a miss they execute and {@link #save} the result.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;

    public IdempotencyService(IdempotencyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> lookup(UUID merchantId, String idempotencyKey) {
        return repository.findByMerchantIdAndIdemKey(merchantId, idempotencyKey);
    }

    @Transactional
    public void save(UUID merchantId, String idempotencyKey, int status, String body) {
        repository.save(new IdempotencyRecord(merchantId, idempotencyKey, status, body));
    }
}
