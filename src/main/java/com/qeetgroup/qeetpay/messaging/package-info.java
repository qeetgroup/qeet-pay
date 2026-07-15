/**
 * Messaging module (PRD Module 09 "Messaging", TAD §5, Phase 2) — WhatsApp-native invoice delivery,
 * dunning nudges, and payout confirmations. Renders a merchant-configured {@link
 * com.qeetgroup.qeetpay.messaging.MessageTemplate} and persists a {@link
 * com.qeetgroup.qeetpay.messaging.MessageDispatch}, emitting it via the transactional outbox for
 * qeet-notify to deliver (the relay ships disabled by default, §9.5). A delivery callback marks the
 * dispatch SENT/FAILED. Depends only on {@code platform}; merchant-scoped via RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Messaging")
package com.qeetgroup.qeetpay.messaging;
