package com.qeetgroup.qeetpay.filing;

/**
 * Kind of GST return (TAD §7.4). {@code GSTR1} carries the per-invoice outward-supply detail;
 * {@code GSTR3B} is the consolidated outward-supply summary for the period (§3.1(a) liability).
 */
public enum GstReturnType {
    GSTR1,
    GSTR3B
}
