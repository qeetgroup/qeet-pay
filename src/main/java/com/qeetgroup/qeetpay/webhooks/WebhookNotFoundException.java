package com.qeetgroup.qeetpay.webhooks;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class WebhookNotFoundException extends RuntimeException {
    public WebhookNotFoundException(String message) {
        super(message);
    }
}
