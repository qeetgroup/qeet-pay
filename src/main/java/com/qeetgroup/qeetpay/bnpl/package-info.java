/**
 * Buy-Now-Pay-Later module (PRD Module 10 "Embedded Finance", TAD §5, Phase 2/3) — a credit line on
 * UPI split into installments at checkout. The BNPL provider funds the merchant the full order amount
 * immediately (posted once via the {@code ledger} module: debit {@code settlement} / credit
 * {@code revenue}) while the customer repays the platform over N monthly installments. Optional flat
 * interest ({@code interestBps}) is added to the order amount to form the total payable, split into
 * equal installments with the rounding remainder carried on the last one. Installment repayments are
 * customer &lt;-&gt; platform, so they do not touch the merchant ledger. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "BNPL")
package com.qeetgroup.qeetpay.bnpl;
