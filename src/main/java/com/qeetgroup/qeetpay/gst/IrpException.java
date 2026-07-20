package com.qeetgroup.qeetpay.gst;

/** A live IRP call failed or returned an unusable payload (TAD §7.3). */
public class IrpException extends RuntimeException {
    public IrpException(String message) {
        super(message);
    }

    public IrpException(String message, Throwable cause) {
        super(message, cause);
    }
}
