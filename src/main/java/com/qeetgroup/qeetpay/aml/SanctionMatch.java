package com.qeetgroup.qeetpay.aml;

/**
 * One hit against a watchlist. {@code listName} is OFAC / UN / PEP; {@code field} is NAME or
 * IDENTIFIER; {@code score} is a 0–100 confidence for this match.
 */
public record SanctionMatch(String listName, String field, String matchedEntry, int score) {}
