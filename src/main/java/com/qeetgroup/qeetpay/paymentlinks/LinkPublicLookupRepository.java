package com.qeetgroup.qeetpay.paymentlinks;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Resolves a link share code to its owning merchant/link via the (non-RLS) public routing map. This is
 * the one read that must work with <em>no</em> merchant context — it is how the hosted-checkout path
 * discovers which tenant a public code belongs to before applying the merchant scope.
 */
public interface LinkPublicLookupRepository extends JpaRepository<LinkPublicLookup, String> {

    Optional<LinkPublicLookup> findByCode(String code);
}
