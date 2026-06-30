package com.qeetgroup.qeetpay.ledger;

import java.util.UUID;

/** One requested debit/credit line: which account, which side, how many minor units. */
public record LedgerLineInput(UUID accountId, Direction direction, long amountMinor) {}
