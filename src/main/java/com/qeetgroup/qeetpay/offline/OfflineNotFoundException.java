package com.qeetgroup.qeetpay.offline;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No offline entity (wallet, intent, or POS device) with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OfflineNotFoundException extends RuntimeException {
    public OfflineNotFoundException(String message) {
        super(message);
    }
}
