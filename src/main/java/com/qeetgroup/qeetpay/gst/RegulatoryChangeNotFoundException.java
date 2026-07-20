package com.qeetgroup.qeetpay.gst;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No regulatory change with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RegulatoryChangeNotFoundException extends RuntimeException {
    public RegulatoryChangeNotFoundException(String message) {
        super(message);
    }
}
