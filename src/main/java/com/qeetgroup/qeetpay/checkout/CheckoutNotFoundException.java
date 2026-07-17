package com.qeetgroup.qeetpay.checkout;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * No public payment link resolves to that code (HTTP 404). Deliberately identical whether the code was
 * never issued or belongs to another tenant — the public path reveals nothing about which.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CheckoutNotFoundException extends RuntimeException {
    public CheckoutNotFoundException(String message) {
        super(message);
    }
}
