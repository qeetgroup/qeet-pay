package com.qeetgroup.qeetpay.crossborder;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No outbound remittance with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OutboundRemittanceNotFoundException extends RuntimeException {
    public OutboundRemittanceNotFoundException(String message) {
        super(message);
    }
}
