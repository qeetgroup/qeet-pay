package com.qeetgroup.qeetpay.bnpl;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No BNPL agreement / installment with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BnplNotFoundException extends RuntimeException {
    public BnplNotFoundException(String message) {
        super(message);
    }
}
