package com.qeetgroup.qeetpay.marketplace;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No split with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class SplitNotFoundException extends RuntimeException {
    public SplitNotFoundException(String message) {
        super(message);
    }
}
