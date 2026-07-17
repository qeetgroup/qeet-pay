package com.qeetgroup.qeetpay.gst;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link GstInvoiceService#fiscalYear(Instant)} — the Indian financial year
 * (Apr–Mar) label used in invoice numbers. No Spring/DB. The key risk is the year boundary: the FY
 * flips on 1 April <em>IST</em>, so an instant near midnight UTC must be resolved in Asia/Kolkata
 * (UTC+5:30), not UTC.
 */
class GstFiscalYearTest {

    @Test
    void aprilStartsNewFiscalYear() {
        // 1 April 2026 (midday IST) → FY 2026-27.
        assertThat(GstInvoiceService.fiscalYear(Instant.parse("2026-04-01T06:30:00Z")))
                .isEqualTo("2026-27");
    }

    @Test
    void marchIsPreviousFiscalYear() {
        // 31 March 2026 (midday IST) → still FY 2025-26.
        assertThat(GstInvoiceService.fiscalYear(Instant.parse("2026-03-31T06:30:00Z")))
                .isEqualTo("2025-26");
    }

    @Test
    void januaryIsPreviousFiscalYear() {
        assertThat(GstInvoiceService.fiscalYear(Instant.parse("2026-01-15T06:30:00Z")))
                .isEqualTo("2025-26");
    }

    @Test
    void decemberIsCurrentFiscalYear() {
        // December 2026 falls in FY 2026-27 (Apr 2026 – Mar 2027).
        assertThat(GstInvoiceService.fiscalYear(Instant.parse("2026-12-15T06:30:00Z")))
                .isEqualTo("2026-27");
    }

    @Test
    void boundaryResolvesInISTNotUtc() {
        // 1 April 2026 00:00 IST == 31 March 2026 18:30 UTC.
        // An instant one minute after that IST midnight is April → FY 2026-27,
        // even though it is still 31 March in UTC.
        assertThat(GstInvoiceService.fiscalYear(Instant.parse("2026-03-31T18:31:00Z")))
                .as("just after 1 Apr IST midnight → new FY")
                .isEqualTo("2026-27");

        // One minute before IST midnight is still 31 March IST → FY 2025-26.
        assertThat(GstInvoiceService.fiscalYear(Instant.parse("2026-03-31T18:29:00Z")))
                .as("just before 1 Apr IST midnight → previous FY")
                .isEqualTo("2025-26");
    }

    @Test
    void twoDigitSuffixIsZeroPadded() {
        // FY that ends in a single-digit year must be zero-padded: 2009-10, not 2009-1... and
        // 2005-06 not 2005-6.
        assertThat(GstInvoiceService.fiscalYear(Instant.parse("2005-06-01T06:30:00Z")))
                .isEqualTo("2005-06");
        assertThat(GstInvoiceService.fiscalYear(Instant.parse("2009-06-01T06:30:00Z")))
                .isEqualTo("2009-10");
    }
}
