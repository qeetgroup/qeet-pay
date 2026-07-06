package com.qeetgroup.qeetpay.payouts;

/** One requested disbursement in a bulk batch: how much, over which rail, to which destination. */
public record PayoutInstruction(long amountMinor, PayoutRail rail, String destination, String description) {}
