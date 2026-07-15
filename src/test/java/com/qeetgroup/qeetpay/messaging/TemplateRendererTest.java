package com.qeetgroup.qeetpay.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pure template rendering: substitute {{placeholders}}, leave unknowns literal. */
class TemplateRendererTest {

    @Test
    void substitutesPlaceholders() {
        String out =
                TemplateRenderer.render(
                        "Hi {{name}}, invoice {{number}} for {{amount}} is ready.",
                        Map.of("name", "Asha", "number", "QP/2026-27/00001", "amount", "₹1,180"));
        assertThat(out).isEqualTo("Hi Asha, invoice QP/2026-27/00001 for ₹1,180 is ready.");
    }

    @Test
    void leavesUnknownPlaceholdersLiteral() {
        String out = TemplateRenderer.render("Hello {{name}}, ref {{missing}}", Map.of("name", "Ravi"));
        assertThat(out).isEqualTo("Hello Ravi, ref {{missing}}");
    }

    @Test
    void handlesNullVariables() {
        assertThat(TemplateRenderer.render("static text", null)).isEqualTo("static text");
    }

    @Test
    void rejectsNullTemplate() {
        assertThatThrownBy(() -> TemplateRenderer.render(null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
