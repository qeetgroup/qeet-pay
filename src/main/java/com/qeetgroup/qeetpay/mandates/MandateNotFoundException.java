package com.qeetgroup.qeetpay.mandates;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No mandate with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MandateNotFoundException extends RuntimeException {
    public MandateNotFoundException(String message) {
        super(message);
    }
}
