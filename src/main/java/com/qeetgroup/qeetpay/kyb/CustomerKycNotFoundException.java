package com.qeetgroup.qeetpay.kyb;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No customer-KYC record with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CustomerKycNotFoundException extends RuntimeException {
    public CustomerKycNotFoundException(String message) {
        super(message);
    }
}
