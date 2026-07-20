package com.qeetgroup.qeetpay.aml;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No AML alert with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AmlAlertNotFoundException extends RuntimeException {
    public AmlAlertNotFoundException(String message) {
        super(message);
    }
}
