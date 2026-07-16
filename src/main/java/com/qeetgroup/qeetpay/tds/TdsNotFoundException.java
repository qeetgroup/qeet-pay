package com.qeetgroup.qeetpay.tds;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No TDS/TCS deduction with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TdsNotFoundException extends RuntimeException {
    public TdsNotFoundException(String message) {
        super(message);
    }
}
