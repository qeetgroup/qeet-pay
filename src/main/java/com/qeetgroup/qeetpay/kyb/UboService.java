package com.qeetgroup.qeetpay.kyb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ultimate Beneficial Owner (UBO) registry (PRD Module 19). Registers natural persons holding
 * {@code > 10%} equity (RBI Master Directions), verifying each owner's PAN via the shared
 * {@link KybVerificationAdapter}. Merchant-scoped via platform RLS.
 */
@Service
public class UboService {

    private final BeneficialOwnerRepository repo;
    private final KybVerificationAdapter adapter;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public UboService(
            BeneficialOwnerRepository repo,
            KybVerificationAdapter adapter,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.repo = repo;
        this.adapter = adapter;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BeneficialOwner add(
            UUID merchantId, String name, String pan, String din, String nationality,
            int ownershipBps, boolean controlPerson) {
        merchantScope.apply(merchantId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (ownershipBps <= BeneficialOwner.MIN_OWNERSHIP_BPS) {
            throw new IllegalArgumentException(
                    "a beneficial owner must hold > 10% equity (ownershipBps > 1000, per RBI Master Directions)");
        }
        if (ownershipBps > 10_000) {
            throw new IllegalArgumentException("ownershipBps cannot exceed 10000 (100%)");
        }
        BeneficialOwner owner =
                new BeneficialOwner(merchantId, name, pan, din, nationality, ownershipBps, controlPerson);
        if (pan != null && !pan.isBlank()) {
            owner.setPanStatus(
                    MerchantKyb.VERIFIED.equals(adapter.verifyPan(pan)) ? KycStatus.VERIFIED : KycStatus.REJECTED);
        }
        repo.save(owner);
        emit(merchantId, "kyb.ubo_added", owner);
        return owner;
    }

    @Transactional(readOnly = true)
    public BeneficialOwner get(UUID merchantId, UUID id) {
        merchantScope.apply(merchantId);
        return load(merchantId, id);
    }

    @Transactional(readOnly = true)
    public List<BeneficialOwner> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return repo.findByMerchantIdOrderByOwnershipBpsDesc(merchantId);
    }

    @Transactional
    public void remove(UUID merchantId, UUID id) {
        merchantScope.apply(merchantId);
        BeneficialOwner owner = load(merchantId, id);
        repo.delete(owner);
        emit(merchantId, "kyb.ubo_removed", owner);
    }

    private BeneficialOwner load(UUID merchantId, UUID id) {
        return repo.findById(id)
                .filter(o -> o.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new BeneficialOwnerNotFoundException("no beneficial owner " + id));
    }

    private void emit(UUID merchantId, String type, BeneficialOwner owner) {
        try {
            String payload =
                    objectMapper.writeValueAsString(Map.of(
                            "merchantId", merchantId.toString(),
                            "beneficialOwnerId", owner.getId().toString(),
                            "ownershipBps", owner.getOwnershipBps()));
            outbox.enqueue(merchantId, type, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("ubo event serialisation failed", e);
        }
    }
}
