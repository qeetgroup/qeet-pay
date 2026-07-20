package com.qeetgroup.qeetpay.tds;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The statutory quarterly return a set of tax-at-source facts rolls up into (PRD Module 06.4).
 *
 * <ul>
 *   <li>{@code FORM_24Q} — TDS on <em>salary</em> (Income-Tax §192).
 *   <li>{@code FORM_26Q} — TDS on <em>non-salary</em> payments (§§194C/194H/194J, §194-O, …).
 *   <li>{@code FORM_27EQ} — <em>TCS</em> (Income-Tax §206C / CGST Act §52).
 * </ul>
 *
 * <p>Each form owns a disjoint slice of the merchant's {@link TdsDeduction} rows for a quarter; {@link
 * #matches(TdsDeduction)} is the membership test used when a return is prepared, so the three forms
 * together partition the quarter's deductions with no double-counting.
 */
public enum TdsReturnForm {
    FORM_24Q("24Q", "TDS on salary (§192)"),
    FORM_26Q("26Q", "TDS on non-salary payments (§194C/§194H/§194J/§194-O)"),
    FORM_27EQ("27EQ", "TCS (§206C / §52)");

    /** The salary section — TDS under §192 is reported on Form 24Q; everything else TDS on 26Q. */
    static final String SALARY_SECTION = "192";

    private final String code;
    private final String description;

    TdsReturnForm(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /** Accepts either the NSDL code ({@code "26Q"}) or the enum name ({@code "FORM_26Q"}), case-insensitively. */
    @JsonCreator
    public static TdsReturnForm fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        for (TdsReturnForm f : values()) {
            if (f.name().equalsIgnoreCase(v) || f.code.equalsIgnoreCase(v)) {
                return f;
            }
        }
        throw new IllegalArgumentException("unknown TDS return form '" + value + "' (use 24Q | 26Q | 27EQ)");
    }

    /** The NSDL form number, e.g. {@code "26Q"}. */
    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    /** Whether this form reports tax <em>collected</em> (TCS) rather than <em>deducted</em> (TDS). */
    public boolean isTcs() {
        return this == FORM_27EQ;
    }

    /** Whether {@code d} belongs on this form (the three forms partition a quarter's deductions). */
    public boolean matches(TdsDeduction d) {
        boolean salary = SALARY_SECTION.equals(d.getSection());
        return switch (this) {
            case FORM_27EQ -> d.getKind() == TaxKind.TCS;
            case FORM_24Q -> d.getKind() == TaxKind.TDS && salary;
            case FORM_26Q -> d.getKind() == TaxKind.TDS && !salary;
        };
    }
}
