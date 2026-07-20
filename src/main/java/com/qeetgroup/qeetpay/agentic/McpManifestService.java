package com.qeetgroup.qeetpay.agentic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Publishes the static, curated MCP tool manifest — the safe operations an AI agent may call on Qeet
 * Pay (payment create, payment-link create, payout create, invoice create, balance read), each with a
 * JSON-Schema input descriptor and the OIDC scope it requires. The manifest is a descriptor only: it
 * never executes anything, and its tool names are the vocabulary a mandate's {@code allowedOperations}
 * allowlist is validated against.
 */
@Service
public class McpManifestService {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SERVER = "qeet-pay";
    private static final String VERSION = "1.0";

    private final List<McpTool> tools = buildTools();
    private final Set<String> toolNames =
            tools.stream().map(McpTool::name).collect(Collectors.toUnmodifiableSet());

    public McpManifest manifest() {
        return new McpManifest(
                PROTOCOL_VERSION,
                SERVER,
                VERSION,
                "Curated, safe Qeet Pay tools an AI agent may invoke under an agent mandate.",
                tools);
    }

    /** The set of tool names an agent mandate may allowlist. */
    public Set<String> toolNames() {
        return toolNames;
    }

    private static List<McpTool> buildTools() {
        return List.of(
                new McpTool(
                        "payment.create",
                        "Create a payment to collect funds from a customer.",
                        "payments:write",
                        objectSchema(
                                Map.of(
                                        "amountMinor", prop("integer", "Amount in minor units (paise)"),
                                        "currency", prop("string", "ISO-4217 currency code, e.g. INR"),
                                        "customerRef", prop("string", "Merchant's reference for the customer"),
                                        "description", prop("string", "What the payment is for")),
                                List.of("amountMinor", "currency"))),
                new McpTool(
                        "paymentlink.create",
                        "Create a shareable payment link a customer can pay.",
                        "payment_links:write",
                        objectSchema(
                                Map.of(
                                        "amountMinor", prop("integer", "Amount in minor units (paise)"),
                                        "currency", prop("string", "ISO-4217 currency code, e.g. INR"),
                                        "description", prop("string", "What the link is for")),
                                List.of("amountMinor", "currency"))),
                new McpTool(
                        "payout.create",
                        "Create a payout to disburse funds to a payee.",
                        "payouts:write",
                        objectSchema(
                                Map.of(
                                        "amountMinor", prop("integer", "Amount in minor units (paise)"),
                                        "currency", prop("string", "ISO-4217 currency code, e.g. INR"),
                                        "payeeRef", prop("string", "Beneficiary reference (VPA / account)"),
                                        "notes", prop("string", "Payout notes")),
                                List.of("amountMinor", "currency", "payeeRef"))),
                new McpTool(
                        "invoice.create",
                        "Create a GST tax invoice.",
                        "invoices:write",
                        objectSchema(
                                Map.of(
                                        "amountMinor", prop("integer", "Taxable amount in minor units (paise)"),
                                        "currency", prop("string", "ISO-4217 currency code, e.g. INR"),
                                        "customerRef", prop("string", "Merchant's reference for the customer"),
                                        "gstin", prop("string", "Customer GSTIN, if registered")),
                                List.of("amountMinor", "currency"))),
                new McpTool(
                        "balance.read",
                        "Read the merchant's settlement balance.",
                        "balance:read",
                        objectSchema(Map.of(), List.of())));
    }

    private static Map<String, Object> objectSchema(
            Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }
}
