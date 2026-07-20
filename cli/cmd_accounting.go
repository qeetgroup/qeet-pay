package main

import (
	"fmt"
	"net/http"
)

// runAccounting dispatches the `accounting` command group (Tally/Zoho/etc.
// connections + period ledger exports).
func runAccounting(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("accounting: expected a subcommand (connections, connect, export-create, export-list, export-get, export-download)")
	}
	switch args[0] {
	case "connections":
		return accountingConnections(args[1:])
	case "connect":
		return accountingConnect(args[1:])
	case "export-create":
		return accountingExportCreate(args[1:])
	case "export-list":
		return accountingExportList(args[1:])
	case "export-get":
		return accountingExportGet(args[1:])
	case "export-download":
		return accountingExportDownload(args[1:])
	default:
		return fmt.Errorf("accounting: unknown subcommand %q", args[0])
	}
}

// accountingConnections implements `accounting connections` →
// GET /v1/accounting/connections.
func accountingConnections(args []string) error {
	fs, cf := newFlagSet("accounting connections")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/accounting/connections", nil, nil)
}

// accountingConnect implements `accounting connect` →
// PUT /v1/accounting/connections.
func accountingConnect(args []string) error {
	fs, cf := newFlagSet("accounting connect")
	target := fs.String("target", "", "accounting target, e.g. TALLY|ZOHO (required)")
	enabled := fs.Bool("enabled", true, "whether the connection is enabled")
	webhookURL := fs.String("webhook-url", "", "webhook URL for push sync")
	zohoOrgID := fs.String("zoho-org", "", "Zoho organization id")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *target == "" {
		return fmt.Errorf("accounting connect: --target is required")
	}
	body := map[string]any{
		"target":  *target,
		"enabled": *enabled,
	}
	if *webhookURL != "" {
		body["webhookUrl"] = *webhookURL
	}
	if *zohoOrgID != "" {
		body["zohoOrganizationId"] = *zohoOrgID
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPut, "/v1/accounting/connections", body, nil)
}

// accountingExportCreate implements `accounting export-create` →
// POST /v1/accounting/exports.
func accountingExportCreate(args []string) error {
	fs, cf := newFlagSet("accounting export-create")
	target := fs.String("target", "", "export target format (required)")
	from := fs.String("from", "", "RFC-3339 period start, e.g. 2026-06-01T00:00:00Z (required)")
	to := fs.String("to", "", "RFC-3339 period end (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *target == "" || *from == "" || *to == "" {
		return fmt.Errorf("accounting export-create: --target, --from and --to are required")
	}
	body := map[string]any{
		"target":      *target,
		"periodStart": *from,
		"periodEnd":   *to,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/accounting/exports", body, nil)
}

// accountingExportList implements `accounting export-list` →
// GET /v1/accounting/exports.
func accountingExportList(args []string) error {
	fs, cf := newFlagSet("accounting export-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/accounting/exports", nil, nil)
}

// accountingExportGet implements `accounting export-get` →
// GET /v1/accounting/exports/{id}.
func accountingExportGet(args []string) error {
	fs, cf := newFlagSet("accounting export-get")
	id := fs.String("id", "", "export id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("accounting export-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/accounting/exports/"+*id, nil, nil)
}

// accountingExportDownload implements `accounting export-download` →
// GET /v1/accounting/exports/{id}/download.
func accountingExportDownload(args []string) error {
	fs, cf := newFlagSet("accounting export-download")
	id := fs.String("id", "", "export id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("accounting export-download: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/accounting/exports/"+*id+"/download", nil, nil)
}
