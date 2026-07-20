package com.qeetgroup.qeetpay.cards;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The upstream card-issuing rail failed or returned an unusable response (HTTP 502 Bad Gateway). */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class CardIssuingException extends RuntimeException {

    public CardIssuingException(String message) {
        super(message);
    }

    public CardIssuingException(String message, Throwable cause) {
        super(message, cause);
    }
}
