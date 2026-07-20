package com.qeetgroup.qeetpay.aml;

import java.util.UUID;

/**
 * Pluggable STR filing backend — sandbox or the live FIU-IND FINnet gateway (PMLA). Accepts a
 * generated FIU-IND-style report payload and returns the acknowledgement / reference id assigned by
 * the regulator.
 */
public interface FiuFilingAdapter {

    String file(UUID merchantId, String payloadJson);
}
