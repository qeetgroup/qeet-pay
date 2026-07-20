package com.qeetgroup.qeetpay.filing;

/** A live GSTN filing call failed or returned an unusable payload (TAD §7.4). */
public class GstnFilingException extends RuntimeException {
    public GstnFilingException(String message) {
        super(message);
    }

    public GstnFilingException(String message, Throwable cause) {
        super(message, cause);
    }
}
