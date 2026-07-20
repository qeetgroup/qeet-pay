package com.qeetgroup.qeetpay.messaging;

/**
 * A command the WhatsApp subscription bot understands (PRD Module 09.3). {@code UNKNOWN} is the
 * fall-through for text that isn't a recognised keyword — the bot replies with a help menu.
 */
public enum BotCommand {
    PAUSE,
    CANCEL,
    INVOICE,
    PLAN,
    USAGE,
    BALANCE,
    PAY,
    UNKNOWN
}
