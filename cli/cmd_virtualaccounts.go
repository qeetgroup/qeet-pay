package main

import (
	"fmt"
	"net/http"
)

// runVirtualAccounts dispatches the `virtual-accounts` command group (per-customer
// VA mint + inbound-credit auto-reconcile).
func runVirtualAccounts(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("virtual-accounts: expected a subcommand (mint, list, get, credit, close)")
	}
	switch args[0] {
	case "mint":
		return virtualAccountsMint(args[1:])
	case "list":
		return virtualAccountsList(args[1:])
	case "get":
		return virtualAccountsGet(args[1:])
	case "credit":
		return virtualAccountsCredit(args[1:])
	case "close":
		return virtualAccountsClose(args[1:])
	default:
		return fmt.Errorf("virtual-accounts: unknown subcommand %q (want: mint, list, get, credit, close)", args[0])
	}
}

// virtualAccountsMint implements `virtual-accounts mint` → POST /v1/virtual-accounts.
func virtualAccountsMint(args []string) error {
	fs, cf := newFlagSet("virtual-accounts mint")
	customer := fs.String("customer", "", "customer reference (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *customer == "" {
		return fmt.Errorf("virtual-accounts mint: --customer is required")
	}
	body := map[string]any{"customerRef": *customer}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/virtual-accounts", body, nil)
}

// virtualAccountsList implements `virtual-accounts list` → GET /v1/virtual-accounts.
func virtualAccountsList(args []string) error {
	fs, cf := newFlagSet("virtual-accounts list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/virtual-accounts", nil, nil)
}

// virtualAccountsGet implements `virtual-accounts get` →
// GET /v1/virtual-accounts/{vaId}.
func virtualAccountsGet(args []string) error {
	fs, cf := newFlagSet("virtual-accounts get")
	id := fs.String("id", "", "virtual account id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("virtual-accounts get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/virtual-accounts/"+*id, nil, nil)
}

// virtualAccountsCredit implements `virtual-accounts credit` →
// POST /v1/virtual-accounts/{vaId}/credits (simulate an inbound bank credit).
func virtualAccountsCredit(args []string) error {
	fs, cf := newFlagSet("virtual-accounts credit")
	id := fs.String("id", "", "virtual account id (required)")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	utr := fs.String("utr", "", "bank UTR (idempotency key on the credit) (required)")
	payerName := fs.String("payer-name", "", "payer name")
	payerRef := fs.String("payer-ref", "", "payer reference")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *amount <= 0 || *utr == "" {
		return fmt.Errorf("virtual-accounts credit: --id, --amount (>0) and --utr are required")
	}
	body := map[string]any{
		"amountMinor": *amount,
		"currency":    *currency,
		"utr":         *utr,
	}
	if *payerName != "" {
		body["payerName"] = *payerName
	}
	if *payerRef != "" {
		body["payerRef"] = *payerRef
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/virtual-accounts/"+*id+"/credits", body, nil)
}

// virtualAccountsClose implements `virtual-accounts close` →
// POST /v1/virtual-accounts/{vaId}/close.
func virtualAccountsClose(args []string) error {
	fs, cf := newFlagSet("virtual-accounts close")
	id := fs.String("id", "", "virtual account id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("virtual-accounts close: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/virtual-accounts/"+*id+"/close", nil, nil)
}
