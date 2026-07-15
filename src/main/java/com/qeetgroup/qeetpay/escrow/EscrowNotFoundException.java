package com.qeetgroup.qeetpay.escrow;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No escrow agreement with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EscrowNotFoundException extends RuntimeException {
    public EscrowNotFoundException(String message) {
        super(message);
    }
}
