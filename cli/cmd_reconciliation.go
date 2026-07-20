package main

import (
	"encoding/json"
	"fmt"
	"net/http"
)

// runReconciliation dispatches the `reconciliation` command group (provider
// settlement reports + matching against captured payments).
func runReconciliation(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("reconciliation: expected a subcommand (ingest, settlements, settlement-get)")
	}
	switch args[0] {
	case "ingest":
		return reconciliationIngest(args[1:])
	case "settlements":
		return reconciliationSettlements(args[1:])
	case "settlement-get":
		return reconciliationSettlementGet(args[1:])
	default:
		return fmt.Errorf("reconciliation: unknown subcommand %q (want: ingest, settlements, settlement-get)", args[0])
	}
}

// reconciliationIngest implements `reconciliation ingest` → POST /v1/settlements.
func reconciliationIngest(args []string) error {
	fs, cf := newFlagSet("reconciliation ingest")
	provider := fs.String("provider", "", "provider name (required)")
	settlementID := fs.String("settlement-id", "", "provider settlement id (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	settledAt := fs.String("settled-at", "", "RFC-3339 settlement timestamp")
	reportedNet := fs.Int64("reported-net", 0, "provider-reported net amount in paise")
	items := fs.String("items", "", `settlement line items JSON array, e.g. `+
		`'[{"paymentId":"<uuid>","grossMinor":100000,"feeMinor":2000,"taxMinor":360}]' (required)`)
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *provider == "" || *settlementID == "" || *items == "" {
		return fmt.Errorf("reconciliation ingest: --provider, --settlement-id and --items are required")
	}
	var parsedItems []map[string]any
	if err := json.Unmarshal([]byte(*items), &parsedItems); err != nil {
		return fmt.Errorf("invalid --items JSON: %w", err)
	}
	body := map[string]any{
		"provider":             *provider,
		"providerSettlementId": *settlementID,
		"currency":             *currency,
		"items":                parsedItems,
	}
	if *settledAt != "" {
		body["settledAt"] = *settledAt
	}
	if *reportedNet != 0 {
		body["reportedNetMinor"] = *reportedNet
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/settlements", body, idempotent())
}

// reconciliationSettlements implements `reconciliation settlements` →
// GET /v1/settlements.
func reconciliationSettlements(args []string) error {
	fs, cf := newFlagSet("reconciliation settlements")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/settlements", nil, nil)
}

// reconciliationSettlementGet implements `reconciliation settlement-get` →
// GET /v1/settlements/{id}.
func reconciliationSettlementGet(args []string) error {
	fs, cf := newFlagSet("reconciliation settlement-get")
	id := fs.String("id", "", "settlement id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("reconciliation settlement-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/settlements/"+*id, nil, nil)
}
