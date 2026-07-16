package main

import (
	"fmt"
	"net/http"
)

// runPayments dispatches the `payments` command group.
func runPayments(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("payments: expected a subcommand (create, get, capture, refund, refunds)")
	}
	switch args[0] {
	case "create":
		return paymentsCreate(args[1:])
	case "get":
		return paymentsGet(args[1:])
	case "capture":
		return paymentsCapture(args[1:])
	case "refund":
		return paymentsRefund(args[1:])
	case "refunds":
		return paymentsRefunds(args[1:])
	default:
		return fmt.Errorf("payments: unknown subcommand %q (want: create, get, capture, refund, refunds)", args[0])
	}
}

// paymentsCreate implements `payments create` → POST /v1/payments.
func paymentsCreate(args []string) error {
	fs, cf := newFlagSet("payments create")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	method := fs.String("method", "", "payment method: UPI|CARD|NET_BANKING|WALLET (required)")
	description := fs.String("description", "", "free-text description")
	simulateFailure := fs.Bool("simulate-failure", false, "ask the sandbox provider to fail authorization")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *amount <= 0 || *method == "" {
		return fmt.Errorf("payments create: --amount (>0) and --method are required")
	}

	body := map[string]any{
		"amountMinor": *amount,
		"currency":    *currency,
		"method":      *method,
	}
	if *description != "" {
		body["description"] = *description
	}
	if *simulateFailure {
		body["simulateFailure"] = true
	}

	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payments", body, nil)
}

// paymentsGet implements `payments get` → GET /v1/payments/{id}.
func paymentsGet(args []string) error {
	fs, cf := newFlagSet("payments get")
	id := fs.String("id", "", "payment id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("payments get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/payments/"+*id, nil, nil)
}

// paymentsCapture implements `payments capture` → POST /v1/payments/{id}/capture.
func paymentsCapture(args []string) error {
	fs, cf := newFlagSet("payments capture")
	id := fs.String("id", "", "payment id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("payments capture: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payments/"+*id+"/capture", nil, idempotent())
}

// paymentsRefund implements `payments refund` → POST /v1/payments/{id}/refund.
func paymentsRefund(args []string) error {
	fs, cf := newFlagSet("payments refund")
	id := fs.String("id", "", "payment id (required)")
	amount := fs.Int64("amount", 0, "refund amount in paise / minor units (required)")
	reason := fs.String("reason", "", "refund reason")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *amount <= 0 {
		return fmt.Errorf("payments refund: --id and --amount (>0) are required")
	}

	body := map[string]any{"amountMinor": *amount}
	if *reason != "" {
		body["reason"] = *reason
	}

	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payments/"+*id+"/refund", body, idempotent())
}

// paymentsRefunds implements `payments refunds` → GET /v1/payments/{id}/refunds.
func paymentsRefunds(args []string) error {
	fs, cf := newFlagSet("payments refunds")
	id := fs.String("id", "", "payment id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("payments refunds: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/payments/"+*id+"/refunds", nil, nil)
}
