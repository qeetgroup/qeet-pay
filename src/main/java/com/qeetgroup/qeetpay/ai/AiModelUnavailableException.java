package com.qeetgroup.qeetpay.ai;

/**
 * Thrown by an {@link AiModelClient} when a model call times out or errors. {@link AiGateway} catches
 * it (and any {@link RuntimeException}) and fails closed to the caller's deterministic fallback.
 */
public class AiModelUnavailableException extends RuntimeException {

    public AiModelUnavailableException(String message) {
        super(message);
    }

    public AiModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
