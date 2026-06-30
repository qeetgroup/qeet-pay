package com.qeetgroup.qeetpay.billing;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No such plan/subscription/invoice for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BillingNotFoundException extends RuntimeException {
    public BillingNotFoundException(String message) {
        super(message);
    }
}
