package com.qeetgroup.qeetpay.gst;

/**
 * A single HSN/SAC classification candidate (PRD Module 05 AI classification). Amounts of tax are not
 * carried here — this is the code + rate a caller would apply to a line.
 *
 * @param hsnSac the suggested HSN (goods) or SAC (services) code
 * @param kind {@code "HSN"} for goods or {@code "SAC"} for services
 * @param gstRate the whole-percent GST rate that applies to the code
 * @param confidence classification confidence in {@code [0,1]} (deterministic keyword-match strength,
 *     or a model score when a real client is wired)
 * @param label a human-readable description of the code
 */
public record HsnSuggestion(
        String hsnSac, String kind, int gstRate, double confidence, String label) {}
