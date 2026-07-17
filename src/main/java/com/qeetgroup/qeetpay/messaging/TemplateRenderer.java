package com.qeetgroup.qeetpay.messaging;

import java.util.Map;

/**
 * Renders a message template by substituting {@code {{placeholder}}} variables (PRD Module 09). Pure +
 * deterministic — no Spring/DB — so it is unit-testable in isolation. Unknown placeholders are left
 * literal so a misconfigured template is visible rather than silently blank.
 */
public final class TemplateRenderer {

    private TemplateRenderer() {}

    public static String render(String template, Map<String, String> variables) {
        if (template == null) {
            throw new IllegalArgumentException("template body is required");
        }
        String out = template;
        if (variables != null) {
            for (Map.Entry<String, String> e : variables.entrySet()) {
                out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }
}
