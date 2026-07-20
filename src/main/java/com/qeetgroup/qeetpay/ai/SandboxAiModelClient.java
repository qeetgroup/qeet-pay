package com.qeetgroup.qeetpay.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Offline stand-in {@link AiModelClient} — deterministic canned responses, no network or LLM. Active
 * whenever no {@code liveAiModelClient} bean is present, mirroring the sandbox-adapter pattern used by
 * {@code kyb/} and the other external-rail modules.
 *
 * <p>Its output is a fixed stub keyed off the masked input, with sentinel substrings (mirroring the
 * {@code kyb} {@code "fail_"} convention) that let tests and the console exercise every safety-matrix
 * branch without a real model:
 *
 * <ul>
 *   <li>{@code "fail_"} → throws {@link AiModelUnavailableException} (simulated timeout/error)
 *   <li>{@code "lowconf"} → returns a low-confidence (0.10) result
 *   <li>{@code "stale"} → marks the result stale
 *   <li>{@code "ambiguous"} → marks the result ambiguous
 *   <li>otherwise → a clean, confident (0.90) stub
 * </ul>
 *
 * <p>Replace by registering a real bean named {@code liveAiModelClient}; no gateway change is needed.
 */
@Component
@ConditionalOnMissingBean(name = "liveAiModelClient")
public class SandboxAiModelClient implements AiModelClient {

    static final String MODEL_ID = "sandbox-offline-v1";
    private static final double CONFIDENT = 0.90;
    private static final double LOW_CONFIDENCE = 0.10;

    private final ObjectMapper objectMapper;

    public SandboxAiModelClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Inference infer(String feature, String model, String maskedInput, Set<String> scopes) {
        String probe = maskedInput == null ? "" : maskedInput.toLowerCase(Locale.ROOT);
        if (probe.contains("fail_")) {
            throw new AiModelUnavailableException("sandbox: simulated model timeout/error");
        }
        boolean stale = probe.contains("stale");
        boolean ambiguous = probe.contains("ambiguous");
        double confidence = probe.contains("lowconf") ? LOW_CONFIDENCE : CONFIDENT;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("model", MODEL_ID);
        out.put("feature", feature);
        out.put("decision", "sandbox-offline-stub");
        out.put("confidence", confidence);
        out.put("note", "offline stand-in — not a real model result");
        return new Inference(toJson(out), confidence, stale, ambiguous);
    }

    @Override
    public String modelId() {
        return MODEL_ID;
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise sandbox inference", e);
        }
    }
}
