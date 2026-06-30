package com.qeetgroup.qeetpay.payouts;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No payout with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PayoutNotFoundException extends RuntimeException {
    public PayoutNotFoundException(String message) {
        super(message);
    }
}
