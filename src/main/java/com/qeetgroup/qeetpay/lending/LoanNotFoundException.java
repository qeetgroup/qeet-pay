package com.qeetgroup.qeetpay.lending;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No loan / offer with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class LoanNotFoundException extends RuntimeException {
    public LoanNotFoundException(String message) {
        super(message);
    }
}
