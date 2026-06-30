package com.qeetgroup.qeetpay.ledger;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a journal entry's debits do not equal its credits (HTTP 422). */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class LedgerImbalanceException extends RuntimeException {
    public LedgerImbalanceException(String message) {
        super(message);
    }
}
