package com.qeetgroup.qeetpay.platform.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Pushes the active merchant into the current DB transaction so Postgres RLS scopes every query
 * (TAD §6.1). {@code set_config(..., is_local => true)} ties the GUC to the surrounding
 * transaction, so it resets on commit/rollback and never leaks across pooled connections.
 *
 * <p>Must be called inside a {@code @Transactional} boundary. Domain services call
 * {@link #applyCurrent()} as the first statement of a unit of work; a transaction-boundary aspect
 * that does this automatically is a Phase-1 follow-up.
 */
@Component
public class MerchantScope {

    public static final String GUC = "app.current_merchant_id";

    @PersistenceContext private EntityManager entityManager;

    public void applyCurrent() {
        apply(MerchantContext.require());
    }

    public void apply(UUID merchantId) {
        entityManager
                .createNativeQuery("select set_config('" + GUC + "', :mid, true)")
                .setParameter("mid", merchantId.toString())
                .getSingleResult();
    }
}
