package com.qeetgroup.qeetpay.tds;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No TDS/TCS return with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TdsReturnNotFoundException extends RuntimeException {
    public TdsReturnNotFoundException(String message) {
        super(message);
    }
}
