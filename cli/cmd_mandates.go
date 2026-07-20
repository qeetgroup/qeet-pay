package main

import (
	"fmt"
	"net/http"
)

// runMandates dispatches the `mandates` command group (UPI AutoPay / NACH
// recurring debit mandates).
func runMandates(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("mandates: expected a subcommand (create, list, get, activate, pause, revoke, debit, debits)")
	}
	switch args[0] {
	case "create":
		return mandatesCreate(args[1:])
	case "list":
		return mandatesList(args[1:])
	case "get":
		return mandatesGet(args[1:])
	case "activate":
		return mandatesActivate(args[1:])
	case "pause":
		return mandatesAction(args[1:], "pause")
	case "revoke":
		return mandatesAction(args[1:], "revoke")
	case "debit":
		return mandatesDebit(args[1:])
	case "debits":
		return mandatesDebits(args[1:])
	default:
		return fmt.Errorf("mandates: unknown subcommand %q", args[0])
	}
}

// mandatesCreate implements `mandates create` → POST /v1/mandates.
func mandatesCreate(args []string) error {
	fs, cf := newFlagSet("mandates create")
	customer := fs.String("customer", "", "customer reference (required)")
	mandateType := fs.String("type", "", "mandate type: UPI_AUTOPAY|NACH (required)")
	limit := fs.Int64("limit", 0, "per-debit limit in paise / minor units")
	currency := fs.String("currency", "INR", "ISO currency code")
	frequency := fs.String("frequency", "", "frequency: DAILY|WEEKLY|MONTHLY|YEARLY|AS_PRESENTED (required)")
	startDate := fs.String("start-date", "", "mandate start date YYYY-MM-DD (required)")
	endDate := fs.String("end-date", "", "mandate end date YYYY-MM-DD")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *customer == "" || *mandateType == "" || *frequency == "" || *startDate == "" {
		return fmt.Errorf("mandates create: --customer, --type, --frequency and --start-date are required")
	}
	body := map[string]any{
		"customerRef": *customer,
		"type":        *mandateType,
		"currency":    *currency,
		"frequency":   *frequency,
		"startDate":   *startDate,
	}
	if *limit > 0 {
		body["limitMinor"] = *limit
	}
	if *endDate != "" {
		body["endDate"] = *endDate
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/mandates", body, nil)
}

// mandatesList implements `mandates list` → GET /v1/mandates.
func mandatesList(args []string) error {
	fs, cf := newFlagSet("mandates list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/mandates", nil, nil)
}

// mandatesGet implements `mandates get` → GET /v1/mandates/{id}.
func mandatesGet(args []string) error {
	fs, cf := newFlagSet("mandates get")
	id := fs.String("id", "", "mandate id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("mandates get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/mandates/"+*id, nil, nil)
}

// mandatesActivate implements `mandates activate` →
// POST /v1/mandates/{id}/activate.
func mandatesActivate(args []string) error {
	fs, cf := newFlagSet("mandates activate")
	id := fs.String("id", "", "mandate id (required)")
	providerMandateID := fs.String("provider-mandate-id", "", "provider mandate id")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("mandates activate: --id is required")
	}
	var body map[string]any
	if *providerMandateID != "" {
		body = map[string]any{"providerMandateId": *providerMandateID}
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/mandates/"+*id+"/activate", body, nil)
}

// mandatesAction implements pause/revoke → POST /v1/mandates/{id}/{action}.
func mandatesAction(args []string, action string) error {
	fs, cf := newFlagSet("mandates " + action)
	id := fs.String("id", "", "mandate id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("mandates %s: --id is required", action)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/mandates/"+*id+"/"+action, nil, nil)
}

// mandatesDebit implements `mandates debit` → POST /v1/mandates/{id}/debit.
func mandatesDebit(args []string) error {
	fs, cf := newFlagSet("mandates debit")
	id := fs.String("id", "", "mandate id (required)")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (required)")
	description := fs.String("description", "", "debit description")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *amount <= 0 {
		return fmt.Errorf("mandates debit: --id and --amount (>0) are required")
	}
	body := map[string]any{"amountMinor": *amount}
	if *description != "" {
		body["description"] = *description
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/mandates/"+*id+"/debit", body, idempotent())
}

// mandatesDebits implements `mandates debits` → GET /v1/mandates/{id}/debits.
func mandatesDebits(args []string) error {
	fs, cf := newFlagSet("mandates debits")
	id := fs.String("id", "", "mandate id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("mandates debits: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/mandates/"+*id+"/debits", nil, nil)
}
