package com.qeetgroup.qeetpay.bnpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BnplInstallmentRepository extends JpaRepository<BnplInstallment, UUID> {

    List<BnplInstallment> findByAgreementIdOrderBySeq(UUID agreementId);

    Optional<BnplInstallment> findByAgreementIdAndSeq(UUID agreementId, int seq);
}
