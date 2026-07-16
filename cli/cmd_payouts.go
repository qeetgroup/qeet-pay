package main

import (
	"fmt"
	"net/http"
)

// runPayouts dispatches the `payouts` command group.
func runPayouts(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("payouts: expected a subcommand (create, get, approve, reject)")
	}
	switch args[0] {
	case "create":
		return payoutsCreate(args[1:])
	case "get":
		return payoutsGet(args[1:])
	case "approve":
		return payoutsApprove(args[1:])
	case "reject":
		return payoutsReject(args[1:])
	default:
		return fmt.Errorf("payouts: unknown subcommand %q (want: create, get, approve, reject)", args[0])
	}
}

// payoutsCreate implements `payouts create` → POST /v1/payouts.
func payoutsCreate(args []string) error {
	fs, cf := newFlagSet("payouts create")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	rail := fs.String("rail", "", "disbursement rail: UPI|IMPS|NEFT|RTGS (required)")
	destination := fs.String("destination", "", "destination VPA / account (required)")
	description := fs.String("description", "", "free-text description")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *amount <= 0 || *rail == "" || *destination == "" {
		return fmt.Errorf("payouts create: --amount (>0), --rail and --destination are required")
	}

	body := map[string]any{
		"amountMinor": *amount,
		"currency":    *currency,
		"rail":        *rail,
		"destination": *destination,
	}
	if *description != "" {
		body["description"] = *description
	}

	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payouts", body, nil)
}

// payoutsGet implements `payouts get` → GET /v1/payouts/{id}.
func payoutsGet(args []string) error {
	fs, cf := newFlagSet("payouts get")
	id := fs.String("id", "", "payout id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("payouts get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/payouts/"+*id, nil, nil)
}

// payoutsApprove implements `payouts approve` → POST /v1/payouts/{id}/approve
// (the maker-checker disburse step; idempotent).
func payoutsApprove(args []string) error {
	fs, cf := newFlagSet("payouts approve")
	id := fs.String("id", "", "payout id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("payouts approve: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payouts/"+*id+"/approve", nil, idempotent())
}

// payoutsReject implements `payouts reject` → POST /v1/payouts/{id}/reject.
func payoutsReject(args []string) error {
	fs, cf := newFlagSet("payouts reject")
	id := fs.String("id", "", "payout id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("payouts reject: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payouts/"+*id+"/reject", nil, nil)
}
