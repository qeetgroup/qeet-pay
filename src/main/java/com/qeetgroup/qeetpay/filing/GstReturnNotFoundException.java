package com.qeetgroup.qeetpay.filing;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No GST return with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class GstReturnNotFoundException extends RuntimeException {
    public GstReturnNotFoundException(String message) {
        super(message);
    }
}
