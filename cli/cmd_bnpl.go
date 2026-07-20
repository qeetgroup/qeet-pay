package main

import (
	"fmt"
	"net/http"
	"strconv"
)

// runBNPL dispatches the `bnpl` command group (checkout installment agreements).
func runBNPL(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("bnpl: expected a subcommand (create, list, get, pay)")
	}
	switch args[0] {
	case "create":
		return bnplCreate(args[1:])
	case "list":
		return bnplList(args[1:])
	case "get":
		return bnplGet(args[1:])
	case "pay":
		return bnplPay(args[1:])
	default:
		return fmt.Errorf("bnpl: unknown subcommand %q (want: create, list, get, pay)", args[0])
	}
}

// bnplCreate implements `bnpl create` → POST /v1/bnpl/agreements.
func bnplCreate(args []string) error {
	fs, cf := newFlagSet("bnpl create")
	customer := fs.String("customer", "", "customer reference (required)")
	orderRef := fs.String("order-ref", "", "order reference (required)")
	orderAmount := fs.Int64("order-amount", 0, "order amount in paise / minor units (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	installments := fs.Int("installments", 0, "number of installments (required)")
	interestBps := fs.Int("interest-bps", 0, "interest in basis points (required)")
	firstDue := fs.String("first-due-date", "", "first installment due date YYYY-MM-DD (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *customer == "" || *orderRef == "" || *orderAmount <= 0 || *installments <= 0 || *firstDue == "" {
		return fmt.Errorf("bnpl create: --customer, --order-ref, --order-amount (>0), --installments (>0) and --first-due-date are required")
	}
	body := map[string]any{
		"customerRef":      *customer,
		"orderRef":         *orderRef,
		"orderAmountMinor": *orderAmount,
		"currency":         *currency,
		"installments":     *installments,
		"interestBps":      *interestBps,
		"firstDueDate":     *firstDue,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/bnpl/agreements", body, nil)
}

// bnplList implements `bnpl list` → GET /v1/bnpl/agreements.
func bnplList(args []string) error {
	fs, cf := newFlagSet("bnpl list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/bnpl/agreements", nil, nil)
}

// bnplGet implements `bnpl get` → GET /v1/bnpl/agreements/{agreementId}.
func bnplGet(args []string) error {
	fs, cf := newFlagSet("bnpl get")
	id := fs.String("id", "", "agreement id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("bnpl get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/bnpl/agreements/"+*id, nil, nil)
}

// bnplPay implements `bnpl pay` →
// POST /v1/bnpl/agreements/{agreementId}/installments/{seq}/pay.
func bnplPay(args []string) error {
	fs, cf := newFlagSet("bnpl pay")
	id := fs.String("id", "", "agreement id (required)")
	seq := fs.Int("seq", 0, "installment sequence number (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *seq <= 0 {
		return fmt.Errorf("bnpl pay: --id and --seq (>0) are required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/bnpl/agreements/"+*id+"/installments/"+strconv.Itoa(*seq)+"/pay", nil, nil)
}
