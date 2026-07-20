package com.qeetgroup.qeetpay.treasury;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No sweep rule with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class SweepRuleNotFoundException extends RuntimeException {
    public SweepRuleNotFoundException(String message) {
        super(message);
    }
}
