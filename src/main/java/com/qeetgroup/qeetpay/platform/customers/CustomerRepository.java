package com.qeetgroup.qeetpay.platform.customers;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByMerchantIdAndRef(UUID merchantId, String ref);
}
