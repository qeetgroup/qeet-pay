package com.qeetgroup.qeetpay.tds;

/**
 * Pluggable TDS/TCS return-filing backend — sandbox or a live TIN-FC / TRACES upload gateway (PRD
 * Module 06.4). Submits a prepared quarterly return and returns the acknowledgement the gateway issues
 * on acceptance — the 15-digit <em>provisional receipt number</em> (PRN / token) of the e-TDS/TCS
 * statement.
 */
public interface TdsFilingAdapter {

    /** Files {@code ret} at the TIN gateway and returns its provisional receipt number (token). */
    String file(TdsReturn ret);
}
