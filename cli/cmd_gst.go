package main

import (
	"encoding/json"
	"fmt"
	"net/http"
)

// runGST dispatches the `gst` command group (GST invoices, IRN e-invoicing, returns).
func runGST(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("gst: expected a subcommand (invoice-create, invoice-get, invoice-pay, irn-generate, irn-get, irn-cancel, return-prepare, return-list, return-get, return-file)")
	}
	switch args[0] {
	case "invoice-create":
		return gstInvoiceCreate(args[1:])
	case "invoice-get":
		return gstInvoiceGet(args[1:])
	case "invoice-pay":
		return gstInvoicePay(args[1:])
	case "irn-generate":
		return gstIrnGenerate(args[1:])
	case "irn-get":
		return gstIrnGet(args[1:])
	case "irn-cancel":
		return gstIrnCancel(args[1:])
	case "return-prepare":
		return gstReturnPrepare(args[1:])
	case "return-list":
		return gstReturnList(args[1:])
	case "return-get":
		return gstReturnGet(args[1:])
	case "return-file":
		return gstReturnFile(args[1:])
	default:
		return fmt.Errorf("gst: unknown subcommand %q", args[0])
	}
}

// gstInvoiceCreate implements `gst invoice-create` → POST /v1/gst/invoices.
func gstInvoiceCreate(args []string) error {
	fs, cf := newFlagSet("gst invoice-create")
	supplierGstin := fs.String("supplier-gstin", "", "supplier GSTIN (required)")
	buyerGstin := fs.String("buyer-gstin", "", "buyer GSTIN (B2B; omit for B2C)")
	placeOfSupply := fs.String("place-of-supply", "", "place-of-supply state code, e.g. 27 (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	lines := fs.String("lines", "", `line items JSON array, e.g. `+
		`'[{"description":"Widget","hsnSac":"8471","quantity":2,"unitPriceMinor":50000,"gstRate":18}]' (required)`)
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *supplierGstin == "" || *placeOfSupply == "" || *lines == "" {
		return fmt.Errorf("gst invoice-create: --supplier-gstin, --place-of-supply and --lines are required")
	}

	var parsedLines []map[string]any
	if err := json.Unmarshal([]byte(*lines), &parsedLines); err != nil {
		return fmt.Errorf("invalid --lines JSON: %w", err)
	}

	body := map[string]any{
		"supplierGstin": *supplierGstin,
		"placeOfSupply": *placeOfSupply,
		"currency":      *currency,
		"lines":         parsedLines,
	}
	if *buyerGstin != "" {
		body["buyerGstin"] = *buyerGstin
	}

	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/gst/invoices", body, nil)
}

// gstInvoiceGet implements `gst invoice-get` → GET /v1/gst/invoices/{id}.
func gstInvoiceGet(args []string) error {
	fs, cf := newFlagSet("gst invoice-get")
	id := fs.String("id", "", "invoice id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("gst invoice-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/gst/invoices/"+*id, nil, nil)
}

// gstInvoicePay implements `gst invoice-pay` → POST /v1/gst/invoices/{id}/pay.
func gstInvoicePay(args []string) error {
	fs, cf := newFlagSet("gst invoice-pay")
	id := fs.String("id", "", "invoice id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("gst invoice-pay: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/gst/invoices/"+*id+"/pay", nil, idempotent())
}

// gstIrnGenerate implements `gst irn-generate` → POST /v1/gst/invoices/{id}/irn.
func gstIrnGenerate(args []string) error {
	fs, cf := newFlagSet("gst irn-generate")
	invoice := fs.String("invoice", "", "invoice id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *invoice == "" {
		return fmt.Errorf("gst irn-generate: --invoice is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/gst/invoices/"+*invoice+"/irn", nil, nil)
}

// gstIrnGet implements `gst irn-get` → GET /v1/gst/invoices/{id}/irn.
func gstIrnGet(args []string) error {
	fs, cf := newFlagSet("gst irn-get")
	invoice := fs.String("invoice", "", "invoice id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *invoice == "" {
		return fmt.Errorf("gst irn-get: --invoice is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/gst/invoices/"+*invoice+"/irn", nil, nil)
}

// gstIrnCancel implements `gst irn-cancel` → POST /v1/gst/invoices/{id}/irn/cancel.
func gstIrnCancel(args []string) error {
	fs, cf := newFlagSet("gst irn-cancel")
	invoice := fs.String("invoice", "", "invoice id (required)")
	reason := fs.String("reason", "", "cancellation reason (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *invoice == "" || *reason == "" {
		return fmt.Errorf("gst irn-cancel: --invoice and --reason are required")
	}
	body := map[string]any{"reason": *reason}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/gst/invoices/"+*invoice+"/irn/cancel", body, nil)
}

// gstReturnPrepare implements `gst return-prepare` → POST /v1/gst/returns/prepare.
func gstReturnPrepare(args []string) error {
	fs, cf := newFlagSet("gst return-prepare")
	returnType := fs.String("type", "GSTR1", "return type: GSTR1|GSTR3B")
	period := fs.String("period", "", "tax period, e.g. 2026-06 (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *period == "" {
		return fmt.Errorf("gst return-prepare: --period is required")
	}
	body := map[string]any{
		"type":   *returnType,
		"period": *period,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/gst/returns/prepare", body, nil)
}

// gstReturnList implements `gst return-list` → GET /v1/gst/returns.
func gstReturnList(args []string) error {
	fs, cf := newFlagSet("gst return-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/gst/returns", nil, nil)
}

// gstReturnGet implements `gst return-get` → GET /v1/gst/returns/{returnId}.
func gstReturnGet(args []string) error {
	fs, cf := newFlagSet("gst return-get")
	id := fs.String("id", "", "return id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("gst return-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/gst/returns/"+*id, nil, nil)
}

// gstReturnFile implements `gst return-file` → POST /v1/gst/returns/{returnId}/file.
func gstReturnFile(args []string) error {
	fs, cf := newFlagSet("gst return-file")
	id := fs.String("id", "", "return id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("gst return-file: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/gst/returns/"+*id+"/file", nil, nil)
}
