package com.qeetgroup.qeetpay.agentic;

import java.util.Map;

/**
 * One entry in the MCP tool manifest: a safe operation an AI agent may call, its human-readable
 * description, the OIDC scope required to invoke it, and a JSON-Schema descriptor of its input. This
 * is a <em>descriptor only</em> — publishing a tool never executes anything.
 */
public record McpTool(
        String name, String description, String requiredScope, Map<String, Object> inputSchema) {}
