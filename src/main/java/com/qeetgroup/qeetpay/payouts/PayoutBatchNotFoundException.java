package com.qeetgroup.qeetpay.payouts;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No payout batch with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PayoutBatchNotFoundException extends RuntimeException {
    public PayoutBatchNotFoundException(String message) {
        super(message);
    }
}
