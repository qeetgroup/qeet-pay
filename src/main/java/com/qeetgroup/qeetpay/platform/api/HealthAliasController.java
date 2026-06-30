package com.qeetgroup.qeetpay.platform.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Short health aliases matching the QG house convention (qeet-id exposes {@code /readyz}).
 * Detailed probes remain at {@code /actuator/health/{liveness,readiness}}.
 */
@RestController
public class HealthAliasController {

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "ok");
    }

    @GetMapping("/readyz")
    public Map<String, String> readyz() {
        return Map.of("status", "ready");
    }
}
