package com.qeetgroup.qeetpay.virtualaccounts;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No virtual account with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class VirtualAccountNotFoundException extends RuntimeException {
    public VirtualAccountNotFoundException(String message) {
        super(message);
    }
}
