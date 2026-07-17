package main

import (
	"encoding/json"
	"fmt"
	"net/http"
)

// runLedger dispatches the `ledger` command group.
func runLedger(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("ledger: expected a subcommand (accounts, balance, post)")
	}
	switch args[0] {
	case "accounts":
		return ledgerAccounts(args[1:])
	case "balance":
		return ledgerBalance(args[1:])
	case "post":
		return ledgerPost(args[1:])
	default:
		return fmt.Errorf("ledger: unknown subcommand %q (want: accounts, balance, post)", args[0])
	}
}

// ledgerAccounts implements `ledger accounts` → GET /v1/ledger/accounts.
func ledgerAccounts(args []string) error {
	fs, cf := newFlagSet("ledger accounts")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/ledger/accounts", nil, nil)
}

// ledgerBalance implements `ledger balance` → GET /v1/ledger/accounts/{id}/balance.
func ledgerBalance(args []string) error {
	fs, cf := newFlagSet("ledger balance")
	id := fs.String("id", "", "account id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("ledger balance: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/ledger/accounts/"+*id+"/balance", nil, nil)
}

// ledgerPost implements `ledger post` → POST /v1/ledger/entries (a balanced,
// double-entry journal entry; Σdebits must equal Σcredits).
func ledgerPost(args []string) error {
	fs, cf := newFlagSet("ledger post")
	description := fs.String("description", "", "entry description (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	lines := fs.String("lines", "", `journal lines JSON array, e.g. `+
		`'[{"accountId":"<uuid>","direction":"DEBIT","amountMinor":100000},`+
		`{"accountId":"<uuid>","direction":"CREDIT","amountMinor":100000}]' (required)`)
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *description == "" || *lines == "" {
		return fmt.Errorf("ledger post: --description and --lines are required")
	}

	var parsedLines []map[string]any
	if err := json.Unmarshal([]byte(*lines), &parsedLines); err != nil {
		return fmt.Errorf("invalid --lines JSON: %w", err)
	}

	body := map[string]any{
		"description": *description,
		"currency":    *currency,
		"lines":       parsedLines,
	}

	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/ledger/entries", body, idempotent())
}
