package com.qeetgroup.qeetpay.payroll;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No payroll batch with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PayrollBatchNotFoundException extends RuntimeException {
    public PayrollBatchNotFoundException(String message) {
        super(message);
    }
}
