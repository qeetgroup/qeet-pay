package com.qeetgroup.qeetpay.copilot;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a copilot conversation is not found for the active merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CopilotConversationNotFoundException extends RuntimeException {
    public CopilotConversationNotFoundException(String message) {
        super(message);
    }
}
