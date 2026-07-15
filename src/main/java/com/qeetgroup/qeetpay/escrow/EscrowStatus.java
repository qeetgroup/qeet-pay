package com.qeetgroup.qeetpay.escrow;

/**
 * Escrow lifecycle. HELD while funds remain unallocated; then RELEASED (all to seller), REFUNDED
 * (all to buyer), or SETTLED (a mix of both, fully allocated).
 */
public enum EscrowStatus {
    HELD,
    RELEASED,
    REFUNDED,
    SETTLED
}
