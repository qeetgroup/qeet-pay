package com.qeetgroup.qeetpay.aml;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No AML case with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AmlCaseNotFoundException extends RuntimeException {
    public AmlCaseNotFoundException(String message) {
        super(message);
    }
}
