package com.qeetgroup.qeetpay.esg;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No ESG carbon record with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EsgNotFoundException extends RuntimeException {
    public EsgNotFoundException(String message) {
        super(message);
    }
}
