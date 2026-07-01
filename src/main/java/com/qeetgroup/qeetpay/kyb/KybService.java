package com.qeetgroup.qeetpay.kyb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** KYB orchestrator (TAD Module 06). Each verification step is idempotent (re-running re-verifies). */
@Service
public class KybService {

    private final MerchantKybRepository kybRepo;
    private final KybVerificationAdapter adapter;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public KybService(
            MerchantKybRepository kybRepo,
            KybVerificationAdapter adapter,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.kybRepo = kybRepo;
        this.adapter = adapter;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MerchantKyb submitPan(UUID merchantId, String pan) {
        merchantScope.apply(merchantId);
        MerchantKyb kyb = getOrCreate(merchantId);
        String result = adapter.verifyPan(pan);
        kyb.submitPan(pan, result);
        kybRepo.save(kyb);
        emitEvent(merchantId, "kyb.pan_verified", kyb);
        return kyb;
    }

    @Transactional
    public MerchantKyb submitGstin(UUID merchantId, String gstin) {
        merchantScope.apply(merchantId);
        MerchantKyb kyb = getOrCreate(merchantId);
        String result = adapter.verifyGstin(gstin);
        kyb.submitGstin(gstin, result);
        kybRepo.save(kyb);
        emitEvent(merchantId, "kyb.gstin_verified", kyb);
        return kyb;
    }

    @Transactional
    public MerchantKyb submitBank(UUID merchantId, String accountNumber, String ifsc) {
        merchantScope.apply(merchantId);
        MerchantKyb kyb = getOrCreate(merchantId);
        String result = adapter.verifyBankAccount(accountNumber, ifsc);
        kyb.submitBank(accountNumber, ifsc, result);
        kybRepo.save(kyb);
        if (MerchantKyb.VERIFIED.equals(kyb.getOverallStatus())) {
            emitEvent(merchantId, "kyb.completed", kyb);
        }
        return kyb;
    }

    @Transactional(readOnly = true)
    public MerchantKyb status(UUID merchantId) {
        merchantScope.apply(merchantId);
        return getOrCreate(merchantId);
    }

    private MerchantKyb getOrCreate(UUID merchantId) {
        return kybRepo.findByMerchantId(merchantId)
                .orElseGet(() -> kybRepo.save(new MerchantKyb(merchantId)));
    }

    private void emitEvent(UUID merchantId, String type, MerchantKyb kyb) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "merchantId", merchantId.toString(),
                    "overallStatus", kyb.getOverallStatus()));
            outbox.enqueue(merchantId, type, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("kyb event serialisation failed", e);
        }
    }
}
