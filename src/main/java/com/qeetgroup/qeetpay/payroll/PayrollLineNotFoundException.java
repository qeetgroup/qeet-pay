package com.qeetgroup.qeetpay.payroll;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No payroll line with that id in the given batch for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PayrollLineNotFoundException extends RuntimeException {
    public PayrollLineNotFoundException(String message) {
        super(message);
    }
}
