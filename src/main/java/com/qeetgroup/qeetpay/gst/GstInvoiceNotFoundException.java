package com.qeetgroup.qeetpay.gst;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No GST invoice with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class GstInvoiceNotFoundException extends RuntimeException {
    public GstInvoiceNotFoundException(String message) {
        super(message);
    }
}
