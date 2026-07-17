package com.qeetgroup.qeetpay.revrec;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No recognition schedule with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecognitionScheduleNotFoundException extends RuntimeException {
    public RecognitionScheduleNotFoundException(String message) {
        super(message);
    }
}
