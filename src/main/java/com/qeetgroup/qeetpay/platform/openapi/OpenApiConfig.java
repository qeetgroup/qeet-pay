package com.qeetgroup.qeetpay.platform.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 / Swagger UI configuration (springdoc). Publishes the machine-readable spec at
 * {@code /v3/api-docs} and the interactive explorer at {@code /swagger-ui.html}; both paths are
 * permitted in every {@link com.qeetgroup.qeetpay.platform.security.SecurityConfig} chain (docs are
 * public-safe — auth is still enforced on {@code /v1}).
 *
 * <p>Declares a single global {@code X-Api-Key} API-key security scheme so the Swagger UI
 * "Authorize" dialog attaches the same header programmatic callers use (TAD §10.1). The Qeet ID
 * bearer path is left implicit; API keys are the documented programmatic contract.
 */
@Configuration
public class OpenApiConfig {

    static final String API_KEY_SCHEME = "ApiKeyAuth";
    static final String API_KEY_HEADER = "X-Api-Key";

    @Bean
    public OpenAPI qeetPayOpenAPI(
            @Value("${spring.application.name:qeet-pay}") String appName,
            @Value("${server.port:4201}") int serverPort) {

        SecurityScheme apiKeyScheme =
                new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(API_KEY_HEADER)
                        .description(
                                "Programmatic API key (`qp_live_…` / `qp_test_…`) presented as the"
                                        + " `X-Api-Key` header.");

        return new OpenAPI()
                .info(
                        new Info()
                                .title("Qeet Pay API")
                                .version("v1")
                                .description(
                                        "Qeet Pay — India-first payments, billing and GST"
                                            + " infrastructure (UPI / Cards / NACH). Programmatic"
                                            + " `/v1` calls authenticate with a `qp_live_` /"
                                            + " `qp_test_` API key via the `X-Api-Key` header;"
                                            + " money is always in integer minor units (paise).")
                                .contact(new Contact().name("Qeet Group").url("https://qeet.in"))
                                .license(
                                        new License()
                                                .name("Proprietary")
                                                .url("https://qeet.in")))
                .tags(curatedTags())
                .servers(
                        List.of(
                                new Server()
                                        .url("http://localhost:" + serverPort)
                                        .description("Local development"),
                                new Server()
                                        .url("https://api.pay.qeet.in")
                                        .description("Production")))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, apiKeyScheme))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }

    /**
     * Enforces the curated tag ORDER + descriptions in the generated spec. While building paths,
     * springdoc merges every controller {@code @Tag} into {@code openApi.tags} and rebuilds the list
     * from an unordered set — which drops our order and duplicates any name whose controller-level
     * description differs from the curated one. This customizer runs last (after path/tag building) and
     * re-applies {@link #curatedTags()} verbatim, so {@code /v3/api-docs} and Swagger UI group the
     * operations in a deliberate order with one clean tag per group. Operations keep their own tag refs.
     */
    @Bean
    public OpenApiCustomizer curatedTagOrderCustomizer() {
        return openApi -> openApi.setTags(new ArrayList<>(curatedTags()));
    }

    // --- Per-bounded-context API groups. springdoc serves each self-contained spec at
    // /v3/api-docs/{group} (+ .yaml); these are exported to api/openapi/<group>.yaml (no monolith). ---

    @Bean
    public GroupedOpenApi paymentsApiGroup() {
        return GroupedOpenApi.builder().group("payments")
                .pathsToMatch("/v1/payments/**", "/v1/payment-links/**", "/v1/checkout/**",
                        "/v1/mandates/**", "/v1/offline/**", "/v1/virtual-accounts/**")
                .build();
    }

    @Bean
    public GroupedOpenApi payoutsApiGroup() {
        return GroupedOpenApi.builder().group("payouts")
                .pathsToMatch("/v1/payouts/**", "/v1/payout-batches/**", "/v1/payroll/**",
                        "/v1/treasury/**", "/v1/ledger/**", "/v1/settlements/**")
                .build();
    }

    @Bean
    public GroupedOpenApi billingApiGroup() {
        return GroupedOpenApi.builder().group("billing")
                .pathsToMatch("/v1/plans/**", "/v1/subscriptions/**", "/v1/invoices/**",
                        "/v1/billing/**", "/v1/dunning/**", "/v1/revrec/**")
                .build();
    }

    @Bean
    public GroupedOpenApi taxApiGroup() {
        return GroupedOpenApi.builder().group("tax")
                .pathsToMatch("/v1/gst/**", "/v1/tds/**", "/v1/itc/**")
                .build();
    }

    @Bean
    public GroupedOpenApi commerceApiGroup() {
        return GroupedOpenApi.builder().group("commerce")
                .pathsToMatch("/v1/marketplace/**", "/v1/ondc/**", "/v1/crossborder/**",
                        "/v1/esg/**", "/v1/lending/**", "/v1/bnpl/**", "/v1/cards/**",
                        "/v1/insurance/**", "/v1/escrow/**")
                .build();
    }

    @Bean
    public GroupedOpenApi riskApiGroup() {
        return GroupedOpenApi.builder().group("risk")
                .pathsToMatch("/v1/fraud/**", "/v1/aml/**", "/v1/kyc/**", "/v1/merchants/kyb/**")
                .build();
    }

    @Bean
    public GroupedOpenApi platformApiGroup() {
        return GroupedOpenApi.builder().group("platform")
                .pathsToMatch("/v1/webhooks/**", "/v1/analytics/**", "/v1/ai/**", "/v1/agentic/**",
                        "/v1/copilot/**", "/v1/messaging/**", "/v1/accounting/**",
                        "/v1/merchants/**", "/v1/me")
                .pathsToExclude("/v1/merchants/kyb/**")
                .build();
    }

    /**
     * Curated, product-area tag list — controls the ORDER operations are grouped in {@code /v3/api-docs}
     * and Swagger UI (money-movement first, then tax/compliance, then Phase-2 financial products, then
     * platform), plus each group's one-line description. Every controller carries a matching class-level
     * {@code @Tag} whose {@code name} joins one of these groups.
     */
    private static List<Tag> curatedTags() {
        return List.of(
                tag("Payments", "Accept payments (create, capture) and inbound provider webhooks."),
                tag("Payment Links", "Shareable payment links — fixed or payer-entered amount."),
                tag("Checkout", "Public hosted-checkout — pay a link by its code, no API key."),
                tag("Orchestration", "Smart provider routing — scorecards and cost basis."),
                tag("Payouts", "Single and bulk payouts under maker-checker approval."),
                tag("Ledger", "Double-entry ledger accounts and balanced journal entries."),
                tag("Reconciliation", "Provider settlements and auto-reconciliation."),
                tag("Revenue Recognition", "IndAS 115 deferred-revenue schedules."),
                tag("Billing", "Plans, subscriptions, invoices and usage metering."),
                tag("Mandates", "Recurring mandates and idempotent mandate debits."),
                tag("Dunning", "Failed-payment retry rules and attempt history."),
                tag("GST Invoicing", "GST invoices and credit/debit notes."),
                tag("E-Invoicing (IRN)", "IRP registration for IRN + signed QR."),
                tag("GST Returns", "GSTR-1/GSTR-3B preparation and GSTN filing."),
                tag("Input Tax Credit", "Purchase invoices reconciled against GSTR-2B."),
                tag("TDS / TCS", "Tax-at-source deductions, certificates and summaries."),
                tag("Lending", "Working-capital advances repaid from settlements."),
                tag("BNPL", "Buy-Now-Pay-Later installment agreements."),
                tag("Cards", "Virtual expense/wallet cards."),
                tag("Insurance", "Embedded protection policies and claims."),
                tag("Escrow", "Conditional hold, release-to-seller and refund."),
                tag("Virtual Accounts", "Per-customer virtual accounts with auto-reconciled credits."),
                tag("Cross-Border", "Export invoices and inward FX remittances (FIRA)."),
                tag("Marketplace", "Seller registration and split settlements (TCS/TDS)."),
                tag("Messaging", "WhatsApp/SMS/email templates and dispatch."),
                tag("ESG / Carbon", "Per-transaction carbon footprint and offsets."),
                tag("Analytics", "TPV, MRR/ARR, success rate and cash-flow forecasting."),
                tag("KYB", "Merchant Know-Your-Business verification."),
                tag("Webhooks", "Webhook endpoint registration and delivery history."),
                tag("Merchants", "Merchant onboarding and current-merchant profile."),
                tag("Platform", "Request-identity introspection and health probes."));
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
