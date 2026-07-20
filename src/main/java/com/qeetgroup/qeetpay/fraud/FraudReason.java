package com.qeetgroup.qeetpay.fraud;

/**
 * One explainable feature contribution to a fraud score (TAD §8.4 Explainable AI). Mirrors the
 * fraud-svc {@code explanation[]} entries — a deterministic SHAP-style attribution: {@code contribution}
 * is the signed risk-points the feature added (positive) or removed (negative), {@code value} the
 * normalised feature value, and {@code reason} a plain-English, RBI-audit-friendly explanation.
 */
public record FraudReason(String feature, double contribution, double value, String reason) {}
