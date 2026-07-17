package com.qeetgroup.qeetpay.revrec;

/**
 * How a contract amount is allocated across time (TAD §5 "RevRec"). {@code STRAIGHT_LINE} spreads it
 * evenly over N equal monthly periods; {@code IMMEDIATE} recognises the whole amount at the start
 * (a point-in-time obligation).
 */
public enum RecognitionMethod {
    STRAIGHT_LINE,
    IMMEDIATE
}
