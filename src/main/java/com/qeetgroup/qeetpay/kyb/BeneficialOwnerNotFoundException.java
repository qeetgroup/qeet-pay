package com.qeetgroup.qeetpay.kyb;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No beneficial owner with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BeneficialOwnerNotFoundException extends RuntimeException {
    public BeneficialOwnerNotFoundException(String message) {
        super(message);
    }
}
