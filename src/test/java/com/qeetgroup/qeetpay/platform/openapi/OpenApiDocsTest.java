package com.qeetgroup.qeetpay.platform.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the context and dumps the generated OpenAPI spec to {@code build/openapi/qeet-pay-openapi.json}
 * (build dir = gitignored). Doubles as a smoke test that springdoc is wired, the doc endpoint is
 * publicly reachable under the test security chain, and the {@code X-Api-Key} scheme is present.
 *
 * <p>Regenerate on demand: {@code ./gradlew test --tests '*OpenApiDocsTest'}.
 */
@AutoConfigureMockMvc
class OpenApiDocsTest extends AbstractIntegrationTest {

    private static final Path OUTPUT = Path.of("build", "openapi", "qeet-pay-openapi.json");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void dumpsOpenApiSpecToBuildDir() throws Exception {
        String json =
                mvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(json).contains("Qeet Pay API").contains("ApiKeyAuth").contains("X-Api-Key");

        Object tree = objectMapper.readValue(json, Object.class);
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree));

        assertThat(OUTPUT).exists();
    }
}
