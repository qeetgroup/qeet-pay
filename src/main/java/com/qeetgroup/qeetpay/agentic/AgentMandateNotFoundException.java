package com.qeetgroup.qeetpay.agentic;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No agent mandate with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AgentMandateNotFoundException extends RuntimeException {
    public AgentMandateNotFoundException(String message) {
        super(message);
    }
}
