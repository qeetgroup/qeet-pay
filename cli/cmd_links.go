package main

import (
	"fmt"
	"net/http"
)

// runLinks dispatches the `links` (payment links) command group.
func runLinks(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("links: expected a subcommand (create, list, get, pay, cancel)")
	}
	switch args[0] {
	case "create":
		return linksCreate(args[1:])
	case "list":
		return linksList(args[1:])
	case "get":
		return linksGet(args[1:])
	case "pay":
		return linksPay(args[1:])
	case "cancel":
		return linksCancel(args[1:])
	default:
		return fmt.Errorf("links: unknown subcommand %q (want: create, list, get, pay, cancel)", args[0])
	}
}

// linksCreate implements `links create` → POST /v1/payment-links.
func linksCreate(args []string) error {
	fs, cf := newFlagSet("links create")
	title := fs.String("title", "", "link title (required)")
	amount := fs.Int64("amount", 0, "fixed amount in paise; omit or 0 for an open amount")
	currency := fs.String("currency", "INR", "ISO currency code")
	reference := fs.String("reference", "", "your reference for reconciliation")
	expiresAt := fs.String("expires-at", "", "RFC-3339 expiry, e.g. 2026-12-31T23:59:59Z")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *title == "" {
		return fmt.Errorf("links create: --title is required")
	}

	body := map[string]any{
		"title":    *title,
		"currency": *currency,
	}
	if *amount > 0 {
		body["amountMinor"] = *amount
	}
	if *reference != "" {
		body["reference"] = *reference
	}
	if *expiresAt != "" {
		body["expiresAt"] = *expiresAt
	}

	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payment-links", body, nil)
}

// linksList implements `links list` → GET /v1/payment-links.
func linksList(args []string) error {
	fs, cf := newFlagSet("links list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/payment-links", nil, nil)
}

// linksGet implements `links get` → GET /v1/payment-links/{linkId}.
func linksGet(args []string) error {
	fs, cf := newFlagSet("links get")
	id := fs.String("id", "", "link id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("links get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/payment-links/"+*id, nil, nil)
}

// linksPay implements `links pay` → POST /v1/payment-links/{code}/pay.
func linksPay(args []string) error {
	fs, cf := newFlagSet("links pay")
	code := fs.String("code", "", "public link code (required)")
	method := fs.String("method", "", "payment method: UPI|CARD|NET_BANKING|WALLET (required)")
	amount := fs.Int64("amount", 0, "amount in paise (required for open-amount links)")
	simulateFailure := fs.Bool("simulate-failure", false, "ask the sandbox provider to fail authorization")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *code == "" || *method == "" {
		return fmt.Errorf("links pay: --code and --method are required")
	}

	body := map[string]any{"method": *method}
	if *amount > 0 {
		body["amountMinor"] = *amount
	}
	if *simulateFailure {
		body["simulateFailure"] = true
	}

	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payment-links/"+*code+"/pay", body, nil)
}

// linksCancel implements `links cancel` → POST /v1/payment-links/{linkId}/cancel.
func linksCancel(args []string) error {
	fs, cf := newFlagSet("links cancel")
	id := fs.String("id", "", "link id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("links cancel: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payment-links/"+*id+"/cancel", nil, nil)
}
