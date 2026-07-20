package com.qeetgroup.qeetpay.kyb;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No V-CIP session with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class VcipNotFoundException extends RuntimeException {
    public VcipNotFoundException(String message) {
        super(message);
    }
}
