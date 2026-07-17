package com.qeetgroup.qeetpay.messaging;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No message template / dispatch with that id or key for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MessagingNotFoundException extends RuntimeException {
    public MessagingNotFoundException(String message) {
        super(message);
    }
}
