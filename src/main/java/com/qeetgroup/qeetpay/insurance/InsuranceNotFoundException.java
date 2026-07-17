package com.qeetgroup.qeetpay.insurance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No insurance policy or claim with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InsuranceNotFoundException extends RuntimeException {
    public InsuranceNotFoundException(String message) {
        super(message);
    }
}
