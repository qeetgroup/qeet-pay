package com.qeetgroup.qeetpay.ondc;

/**
 * One requested party leg of an ONDC order. {@code grossMinor} is the amount attributable to the
 * party; {@code commissionBps}, {@code commissionGstRate} and {@code tcsBps} default to the statutory
 * values in {@link OndcSplitCalculator} when null. {@code role} defaults to {@link PartyRole#SELLER}.
 */
public record OndcLineInput(
        String partyRef,
        PartyRole role,
        long grossMinor,
        Integer commissionBps,
        Integer commissionGstRate,
        Integer tcsBps) {}
