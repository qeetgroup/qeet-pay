/**
 * Virtual accounts module (PRD Module 01 "Virtual Accounts", Phase 2) — a unique account number +
 * IFSC minted per customer so inbound bank/UPI credits auto-reconcile to the right merchant+customer
 * without a manual match. An inbound credit posts the canonical money-in entry (debit
 * {@code settlement} / credit {@code revenue}) via the {@code ledger} module, identical to a payment
 * capture, and is idempotent on the bank UTR. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "VirtualAccounts")
package com.qeetgroup.qeetpay.virtualaccounts;
