package com.qeetgroup.qeetpay.kyb;

/** Pluggable KYB verification backend — sandbox or live provider (SurePASS / SignDesk). */
public interface KybVerificationAdapter {

    /** Returns VERIFIED or REJECTED for the given PAN number. */
    String verifyPan(String pan);

    /** Returns VERIFIED or REJECTED for the given GSTIN. */
    String verifyGstin(String gstin);

    /**
     * Initiates a penny-drop verification for the given bank account + IFSC.
     * Returns VERIFIED or REJECTED.
     */
    String verifyBankAccount(String accountNumber, String ifsc);
}
