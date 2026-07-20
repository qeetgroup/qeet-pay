package com.qeetgroup.qeetpay.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No AI decision with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AiDecisionNotFoundException extends RuntimeException {
    public AiDecisionNotFoundException(String message) {
        super(message);
    }
}
