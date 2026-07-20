package com.qeetgroup.qeetpay.agentic;

import java.util.List;

/**
 * A Model-Context-Protocol tool manifest: the curated set of safe Qeet Pay tools an AI agent may
 * call, plus the protocol/server metadata. Static and descriptor-only.
 */
public record McpManifest(
        String protocolVersion, String server, String version, String description, List<McpTool> tools) {}
