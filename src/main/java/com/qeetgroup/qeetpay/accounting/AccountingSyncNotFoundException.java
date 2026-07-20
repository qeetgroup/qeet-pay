package com.qeetgroup.qeetpay.accounting;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No accounting export run with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AccountingSyncNotFoundException extends RuntimeException {
    public AccountingSyncNotFoundException(String message) {
        super(message);
    }
}
