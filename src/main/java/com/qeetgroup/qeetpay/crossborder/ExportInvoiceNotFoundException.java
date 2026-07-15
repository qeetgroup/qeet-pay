package com.qeetgroup.qeetpay.crossborder;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No export invoice with that id for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ExportInvoiceNotFoundException extends RuntimeException {
    public ExportInvoiceNotFoundException(String message) {
        super(message);
    }
}
