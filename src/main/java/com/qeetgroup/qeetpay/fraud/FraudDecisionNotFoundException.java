package com.qeetgroup.qeetpay.fraud;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No fraud decision with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class FraudDecisionNotFoundException extends RuntimeException {
    public FraudDecisionNotFoundException(String message) {
        super(message);
    }
}
