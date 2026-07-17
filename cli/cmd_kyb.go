package main

import (
	"fmt"
	"net/http"
)

// runKYB dispatches the `kyb` command group (merchant Know-Your-Business).
func runKYB(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("kyb: expected a subcommand (pan, gstin, bank, status)")
	}
	switch args[0] {
	case "pan":
		return kybPan(args[1:])
	case "gstin":
		return kybGstin(args[1:])
	case "bank":
		return kybBank(args[1:])
	case "status":
		return kybStatus(args[1:])
	default:
		return fmt.Errorf("kyb: unknown subcommand %q (want: pan, gstin, bank, status)", args[0])
	}
}

// kybPan implements `kyb pan` → POST /v1/merchants/kyb/pan.
func kybPan(args []string) error {
	fs, cf := newFlagSet("kyb pan")
	pan := fs.String("pan", "", "PAN, e.g. ABCDE1234F (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *pan == "" {
		return fmt.Errorf("kyb pan: --pan is required")
	}
	body := map[string]any{"pan": *pan}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/merchants/kyb/pan", body, nil)
}

// kybGstin implements `kyb gstin` → POST /v1/merchants/kyb/gstin.
func kybGstin(args []string) error {
	fs, cf := newFlagSet("kyb gstin")
	gstin := fs.String("gstin", "", "GSTIN, e.g. 27ABCDE1234F1Z5 (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *gstin == "" {
		return fmt.Errorf("kyb gstin: --gstin is required")
	}
	body := map[string]any{"gstin": *gstin}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/merchants/kyb/gstin", body, nil)
}

// kybBank implements `kyb bank` → POST /v1/merchants/kyb/bank.
func kybBank(args []string) error {
	fs, cf := newFlagSet("kyb bank")
	account := fs.String("account", "", "bank account number (required)")
	ifsc := fs.String("ifsc", "", "IFSC code (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *account == "" || *ifsc == "" {
		return fmt.Errorf("kyb bank: --account and --ifsc are required")
	}
	body := map[string]any{
		"accountNumber": *account,
		"ifsc":          *ifsc,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/merchants/kyb/bank", body, nil)
}

// kybStatus implements `kyb status` → GET /v1/merchants/kyb/status.
func kybStatus(args []string) error {
	fs, cf := newFlagSet("kyb status")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/merchants/kyb/status", nil, nil)
}
