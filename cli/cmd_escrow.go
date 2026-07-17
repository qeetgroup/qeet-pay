package main

import (
	"fmt"
	"net/http"
)

// runEscrow dispatches the `escrow` command group (conditional hold/release/refund).
func runEscrow(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("escrow: expected a subcommand (hold, list, get, release, refund)")
	}
	switch args[0] {
	case "hold":
		return escrowHold(args[1:])
	case "list":
		return escrowList(args[1:])
	case "get":
		return escrowGet(args[1:])
	case "release":
		return escrowMovement(args[1:], "release")
	case "refund":
		return escrowMovement(args[1:], "refund")
	default:
		return fmt.Errorf("escrow: unknown subcommand %q (want: hold, list, get, release, refund)", args[0])
	}
}

// escrowHold implements `escrow hold` → POST /v1/escrow.
func escrowHold(args []string) error {
	fs, cf := newFlagSet("escrow hold")
	buyer := fs.String("buyer", "", "buyer reference (required)")
	seller := fs.String("seller", "", "seller reference (required)")
	amount := fs.Int64("amount", 0, "amount to hold in paise / minor units (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	description := fs.String("description", "", "agreement description")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *buyer == "" || *seller == "" || *amount <= 0 {
		return fmt.Errorf("escrow hold: --buyer, --seller and --amount (>0) are required")
	}
	body := map[string]any{
		"buyerRef":    *buyer,
		"sellerRef":   *seller,
		"amountMinor": *amount,
		"currency":    *currency,
	}
	if *description != "" {
		body["description"] = *description
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/escrow", body, nil)
}

// escrowList implements `escrow list` → GET /v1/escrow.
func escrowList(args []string) error {
	fs, cf := newFlagSet("escrow list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/escrow", nil, nil)
}

// escrowGet implements `escrow get` → GET /v1/escrow/{escrowId}.
func escrowGet(args []string) error {
	fs, cf := newFlagSet("escrow get")
	id := fs.String("id", "", "escrow id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("escrow get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/escrow/"+*id, nil, nil)
}

// escrowMovement implements release/refund → POST /v1/escrow/{escrowId}/{action}.
func escrowMovement(args []string, action string) error {
	fs, cf := newFlagSet("escrow " + action)
	id := fs.String("id", "", "escrow id (required)")
	amount := fs.Int64("amount", 0, "amount to "+action+" in paise / minor units (required)")
	note := fs.String("note", "", "movement note")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *amount <= 0 {
		return fmt.Errorf("escrow %s: --id and --amount (>0) are required", action)
	}
	body := map[string]any{"amountMinor": *amount}
	if *note != "" {
		body["note"] = *note
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/escrow/"+*id+"/"+action, body, nil)
}
