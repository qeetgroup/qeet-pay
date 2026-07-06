package com.qeetgroup.qeetpay.reconciliation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a settlement is not found for the active merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class SettlementNotFoundException extends RuntimeException {
    public SettlementNotFoundException(String message) {
        super(message);
    }
}
