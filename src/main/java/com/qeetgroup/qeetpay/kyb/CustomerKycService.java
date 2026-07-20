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
 * Customer-KYC orchestrator (PRD Module 19): Aadhaar-OTP e-KYC (simulated) + PAN verification (via
 * the shared {@link KybVerificationAdapter}, so it uses the live provider when enabled) + consent
 * capture. Merchant-scoped via platform RLS.
 *
 * <p>The Aadhaar-OTP flow is <b>simulated</b> deterministically (no live UIDAI call): a 12-digit
 * Aadhaar yields an OTP transaction; any well-formed 6-digit OTP other than {@code 000000} verifies.
 * The full Aadhaar number is never persisted — only its last four digits.
 */
@Service
public class CustomerKycService {

    private final CustomerKycRepository repo;
    private final KybVerificationAdapter adapter;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public CustomerKycService(
            CustomerKycRepository repo,
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
    public CustomerKyc create(
            UUID merchantId, String customerRef, String fullName, boolean consentGiven, String consentArtifact) {
        merchantScope.apply(merchantId);
        if (customerRef == null || customerRef.isBlank()) {
            throw new IllegalArgumentException("customerRef is required");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName is required");
        }
        repo.findByMerchantIdAndCustomerRef(merchantId, customerRef)
                .ifPresent(existing -> {
                    throw new IllegalStateException("customer KYC already exists for ref " + customerRef);
                });
        CustomerKyc kyc = new CustomerKyc(merchantId, customerRef, fullName);
        if (consentGiven) {
            kyc.giveConsent(consentArtifact);
        }
        return repo.save(kyc);
    }

    /** Records consent (required before Aadhaar e-KYC). */
    @Transactional
    public CustomerKyc consent(UUID merchantId, UUID id, String artifact) {
        merchantScope.apply(merchantId);
        CustomerKyc kyc = load(merchantId, id);
        kyc.giveConsent(artifact);
        return repo.save(kyc);
    }

    /**
     * Initiates an Aadhaar-OTP challenge (simulated). Requires consent and a 12-digit Aadhaar number;
     * returns the record with a fresh {@code aadhaarTxnId}. Only the last four Aadhaar digits are kept.
     */
    @Transactional
    public CustomerKyc initiateAadhaar(UUID merchantId, UUID id, String aadhaarNumber) {
        merchantScope.apply(merchantId);
        CustomerKyc kyc = load(merchantId, id);
        if (!kyc.isConsentGiven()) {
            throw new IllegalStateException("consent is required before Aadhaar e-KYC");
        }
        String digits = aadhaarNumber == null ? "" : aadhaarNumber.replaceAll("\\s", "");
        if (!digits.matches("\\d{12}")) {
            throw new IllegalArgumentException("aadhaar must be 12 digits");
        }
        String txnId = "aadhaar_" + UUID.randomUUID().toString().substring(0, 12);
        kyc.initiateAadhaar(digits.substring(8), txnId);
        return repo.save(kyc);
    }

    /**
     * Verifies an Aadhaar OTP (simulated). The txn must match the last initiated one; a well-formed
     * 6-digit OTP other than {@code 000000} verifies, otherwise it is rejected.
     */
    @Transactional
    public CustomerKyc verifyAadhaar(UUID merchantId, UUID id, String txnId, String otp) {
        merchantScope.apply(merchantId);
        CustomerKyc kyc = load(merchantId, id);
        if (kyc.getAadhaarTxnId() == null || !kyc.getAadhaarTxnId().equals(txnId)) {
            throw new IllegalArgumentException("unknown or stale Aadhaar transaction");
        }
        boolean ok = otp != null && otp.matches("\\d{6}") && !"000000".equals(otp);
        kyc.setAadhaarStatus(ok ? KycStatus.VERIFIED : KycStatus.REJECTED);
        repo.save(kyc);
        maybeEmitCompleted(merchantId, kyc);
        return kyc;
    }

    /** Submits and verifies the customer's PAN via the shared KYB adapter. */
    @Transactional
    public CustomerKyc submitPan(UUID merchantId, UUID id, String pan) {
        merchantScope.apply(merchantId);
        CustomerKyc kyc = load(merchantId, id);
        String result = adapter.verifyPan(pan);
        kyc.submitPan(pan, MerchantKyb.VERIFIED.equals(result) ? KycStatus.VERIFIED : KycStatus.REJECTED);
        repo.save(kyc);
        maybeEmitCompleted(merchantId, kyc);
        return kyc;
    }

    @Transactional(readOnly = true)
    public CustomerKyc get(UUID merchantId, UUID id) {
        merchantScope.apply(merchantId);
        return load(merchantId, id);
    }

    @Transactional(readOnly = true)
    public List<CustomerKyc> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return repo.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    private void maybeEmitCompleted(UUID merchantId, CustomerKyc kyc) {
        if (kyc.getOverallStatus() == KycStatus.VERIFIED) {
            emit(merchantId, "kyc.customer_verified", kyc);
        }
    }

    private CustomerKyc load(UUID merchantId, UUID id) {
        return repo.findById(id)
                .filter(k -> k.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new CustomerKycNotFoundException("no customer KYC " + id));
    }

    private void emit(UUID merchantId, String type, CustomerKyc kyc) {
        try {
            String payload =
                    objectMapper.writeValueAsString(Map.of(
                            "merchantId", merchantId.toString(),
                            "customerKycId", kyc.getId().toString(),
                            "customerRef", kyc.getCustomerRef(),
                            "overallStatus", kyc.getOverallStatus().name()));
            outbox.enqueue(merchantId, type, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("customer kyc event serialisation failed", e);
        }
    }
}
