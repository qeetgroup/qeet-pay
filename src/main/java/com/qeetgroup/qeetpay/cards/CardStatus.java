package com.qeetgroup.qeetpay.cards;

/**
 * Virtual-card lifecycle. ACTIVE cards can be loaded and spent; a FROZEN card is temporarily blocked
 * from spending; a CLOSED card is permanently retired.
 */
public enum CardStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
