package com.qeetgroup.qeetpay.marketplace;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** No marketplace seller with that id/ref for the current merchant (HTTP 404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class SellerNotFoundException extends RuntimeException {
    public SellerNotFoundException(String message) {
        super(message);
    }
}
