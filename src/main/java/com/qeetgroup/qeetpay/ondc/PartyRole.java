package com.qeetgroup.qeetpay.ondc;

/**
 * The role a party plays in an ONDC network order. An order may carry an arbitrary number of legs
 * (PRD Module 13.4 edge case: e.g. a logistics partner also taking a cut), so the settlement engine
 * is not limited to a fixed buyer/seller/platform split.
 */
public enum PartyRole {
    SELLER,
    LOGISTICS,
    PLATFORM,
    OTHER
}
