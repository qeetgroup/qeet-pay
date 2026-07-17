/**
 * Checkout module (PRD Module 01 "links, pages, checkout surface", Phase 2) — the PUBLIC, unauthenticated
 * hosted-checkout surface. A payer opens a payment-link URL and pays it with <em>no</em> merchant API
 * key: the link {@code code} is the capability (like a Stripe/Razorpay checkout URL). Because payment-link
 * codes are stored {@code UNIQUE(merchant_id, code)} rather than globally, the public path resolves a code
 * to its owning tenant through the non-RLS {@code paymentlinks.link_public_lookup} routing map, then applies
 * the merchant scope and delegates to the {@code paymentlinks} module for the real capture. The public view
 * exposes only checkout-safe fields (no payment id, reference, internal ids or ledger info). Endpoints are
 * permitted in every {@code SecurityConfig} chain and remain rate-limited under {@code /v1/**} in prod.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Checkout")
package com.qeetgroup.qeetpay.checkout;
