package com.qeetgroup.qeetpay.agentic;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP tool-manifest API (PRD Module 17.5, Novel N1). Returns the static, curated list of safe Qeet
 * Pay tools an AI agent may call — a descriptor only, it never executes anything. Agents fetch this to
 * discover tool names, input schemas and required scopes; a mandate's allowlisted operations are drawn
 * from these tool names.
 */
@Tag(name = "Agentic MCP", description = "Model-Context-Protocol tool manifest — the safe tools an AI agent may call.")
@RestController
@RequestMapping("/v1/agentic/mcp")
public class McpController {

    private final McpManifestService manifest;

    public McpController(McpManifestService manifest) {
        this.manifest = manifest;
    }

    @GetMapping("/manifest")
    public McpManifest manifest() {
        return manifest.manifest();
    }
}
