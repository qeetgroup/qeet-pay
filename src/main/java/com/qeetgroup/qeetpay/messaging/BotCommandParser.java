package com.qeetgroup.qeetpay.messaging;

import java.util.Locale;

/**
 * Parses the leading keyword of an inbound WhatsApp message into a {@link BotCommand} (PRD Module
 * 09.3). Pure + deterministic (no Spring/DB) so it is unit-testable in isolation. Only the first
 * whitespace-delimited token is considered; unrecognised text maps to {@link BotCommand#UNKNOWN}.
 */
public final class BotCommandParser {

    private BotCommandParser() {}

    public static BotCommand parse(String text) {
        if (text == null || text.isBlank()) {
            return BotCommand.UNKNOWN;
        }
        String keyword = text.strip().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        return switch (keyword) {
            case "PAUSE", "HOLD" -> BotCommand.PAUSE;
            case "CANCEL", "STOP" -> BotCommand.CANCEL;
            case "INVOICE", "BILL" -> BotCommand.INVOICE;
            case "PLAN" -> BotCommand.PLAN;
            case "USAGE" -> BotCommand.USAGE;
            case "BALANCE", "BAL" -> BotCommand.BALANCE;
            case "PAY" -> BotCommand.PAY;
            default -> BotCommand.UNKNOWN;
        };
    }
}
