package main

import (
	"fmt"
	"net/http"
)

// runAgentic dispatches the `agentic` command group (agentic-commerce mandates
// + the MCP tool manifest).
func runAgentic(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("agentic: expected a subcommand (mandate-issue, mandate-list, mandate-get, mandate-authorize, mandate-revoke, mcp-manifest)")
	}
	switch args[0] {
	case "mandate-issue":
		return agenticMandateIssue(args[1:])
	case "mandate-list":
		return agenticMandateList(args[1:])
	case "mandate-get":
		return agenticMandateGet(args[1:])
	case "mandate-authorize":
		return agenticMandateAuthorize(args[1:])
	case "mandate-revoke":
		return agenticMandateRevoke(args[1:])
	case "mcp-manifest":
		return agenticMcpManifest(args[1:])
	default:
		return fmt.Errorf("agentic: unknown subcommand %q", args[0])
	}
}

// agenticMandateIssue implements `agentic mandate-issue` → POST /v1/agentic/mandates.
func agenticMandateIssue(args []string) error {
	fs, cf := newFlagSet("agentic mandate-issue")
	holder := fs.String("holder", "", "mandate holder reference (required)")
	mandateType := fs.String("type", "", "mandate type: EXPENSE|WALLET (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *holder == "" || *mandateType == "" {
		return fmt.Errorf("agentic mandate-issue: --holder and --type are required")
	}
	body := map[string]any{
		"holderRef": *holder,
		"type":      *mandateType,
		"currency":  *currency,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/agentic/mandates", body, nil)
}

// agenticMandateList implements `agentic mandate-list` → GET /v1/agentic/mandates.
func agenticMandateList(args []string) error {
	fs, cf := newFlagSet("agentic mandate-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/agentic/mandates", nil, nil)
}

// agenticMandateGet implements `agentic mandate-get` → GET /v1/agentic/mandates/{mandateId}.
func agenticMandateGet(args []string) error {
	fs, cf := newFlagSet("agentic mandate-get")
	id := fs.String("id", "", "mandate id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("agentic mandate-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/agentic/mandates/"+*id, nil, nil)
}

// agenticMandateAuthorize implements `agentic mandate-authorize` →
// POST /v1/agentic/mandates/{mandateId}/authorize.
func agenticMandateAuthorize(args []string) error {
	fs, cf := newFlagSet("agentic mandate-authorize")
	id := fs.String("id", "", "mandate id (required)")
	operation := fs.String("operation", "", "operation being authorized (required)")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (required)")
	payee := fs.String("payee", "", "payee reference")
	capture := fs.Bool("capture", false, "capture (spend) against the mandate on approval")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *operation == "" || *amount <= 0 {
		return fmt.Errorf("agentic mandate-authorize: --id, --operation and --amount (>0) are required")
	}
	body := map[string]any{
		"operation":   *operation,
		"amountMinor": *amount,
	}
	if *payee != "" {
		body["payeeRef"] = *payee
	}
	if *capture {
		body["capture"] = true
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/agentic/mandates/"+*id+"/authorize", body, idempotent())
}

// agenticMandateRevoke implements `agentic mandate-revoke` →
// POST /v1/agentic/mandates/{mandateId}/revoke.
func agenticMandateRevoke(args []string) error {
	fs, cf := newFlagSet("agentic mandate-revoke")
	id := fs.String("id", "", "mandate id (required)")
	reason := fs.String("reason", "", "revocation reason")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("agentic mandate-revoke: --id is required")
	}
	var body map[string]any
	if *reason != "" {
		body = map[string]any{"reason": *reason}
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/agentic/mandates/"+*id+"/revoke", body, nil)
}

// agenticMcpManifest implements `agentic mcp-manifest` → GET /v1/agentic/mcp/manifest.
func agenticMcpManifest(args []string) error {
	fs, cf := newFlagSet("agentic mcp-manifest")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/agentic/mcp/manifest", nil, nil)
}
