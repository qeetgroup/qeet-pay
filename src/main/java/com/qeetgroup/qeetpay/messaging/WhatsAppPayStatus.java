package com.qeetgroup.qeetpay.messaging;

/** Lifecycle of an in-chat WhatsApp Pay collection: CREATED then PAID or FAILED (PRD Module 09.2). */
public enum WhatsAppPayStatus {
    CREATED,
    PAID,
    FAILED
}
