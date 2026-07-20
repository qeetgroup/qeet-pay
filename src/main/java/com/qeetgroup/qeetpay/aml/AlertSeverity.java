package com.qeetgroup.qeetpay.aml;

/** Severity band derived from an alert's 0–100 risk score. */
public enum AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** Maps a 0–100 risk score onto a severity band. */
    public static AlertSeverity fromScore(int score) {
        if (score >= 80) {
            return CRITICAL;
        }
        if (score >= 60) {
            return HIGH;
        }
        if (score >= 40) {
            return MEDIUM;
        }
        return LOW;
    }
}
