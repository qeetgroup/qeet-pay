package com.qeetgroup.qeetpay.paymentlinks;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No payment link with that id/code for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PaymentLinkNotFoundException extends RuntimeException {
    public PaymentLinkNotFoundException(String message) {
        super(message);
    }
}
