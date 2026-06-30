package com.qeetgroup.qeetpay.ledger;

/**
 * Chart-of-accounts types and their normal balance side (TAD §7.1). Asset-like accounts are
 * debit-normal; liability/revenue accounts are credit-normal. The normal side determines the sign
 * of a reported account balance.
 */
public enum AccountType {
    SETTLEMENT(Direction.DEBIT),
    BANK(Direction.DEBIT),
    REVENUE(Direction.CREDIT),
    LIABILITY(Direction.CREDIT),
    TAX_PAYABLE(Direction.CREDIT),
    DEFERRED_REVENUE(Direction.CREDIT);

    private final Direction normalSide;

    AccountType(Direction normalSide) {
        this.normalSide = normalSide;
    }

    public Direction normalSide() {
        return normalSide;
    }
}
