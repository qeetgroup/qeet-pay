package com.qeetgroup.qeetpay.tds;

/**
 * Whether a recorded amount was <em>deducted</em> or <em>collected</em> at source. {@code TDS} =
 * Tax Deducted at Source (Income-Tax §§194C/194H/194J, §194-O); {@code TCS} = Tax Collected at
 * Source (CGST Act §52 / Income-Tax §206C).
 */
public enum TaxKind {
    TDS,
    TCS
}
