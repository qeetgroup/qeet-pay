package com.qeetgroup.qeetpay.aml;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Sandbox STR filing — synthesises an FIU-IND-style acknowledgement reference id without contacting
 * any regulator. Active whenever no {@code liveFiuFilingAdapter} bean is present (mirrors
 * {@code SandboxKybAdapter}).
 */
@Component
@ConditionalOnMissingBean(name = "liveFiuFilingAdapter")
public class SandboxFiuFilingAdapter implements FiuFilingAdapter {

    @Override
    public String file(UUID merchantId, String payloadJson) {
        return "FIUIND-STR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
