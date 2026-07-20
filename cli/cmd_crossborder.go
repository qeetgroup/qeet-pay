package main

import (
	"fmt"
	"net/http"
)

// runCrossborder dispatches the `crossborder` command group (outbound LRS
// remittances + FX quotes, and export invoices settled by inward FIRA).
func runCrossborder(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("crossborder: expected a subcommand (outbound-create, outbound-list, outbound-get, outbound-quote, outbound-mark-remitted, outbound-mark-failed, export-create, export-list, export-get, export-remittance)")
	}
	switch args[0] {
	case "outbound-create":
		return crossborderOutboundCreate(args[1:])
	case "outbound-list":
		return crossborderOutboundList(args[1:])
	case "outbound-get":
		return crossborderOutboundGet(args[1:])
	case "outbound-quote":
		return crossborderOutboundQuote(args[1:])
	case "outbound-mark-remitted":
		return crossborderMarkRemitted(args[1:])
	case "outbound-mark-failed":
		return crossborderMarkFailed(args[1:])
	case "export-create":
		return crossborderExportCreate(args[1:])
	case "export-list":
		return crossborderExportList(args[1:])
	case "export-get":
		return crossborderExportGet(args[1:])
	case "export-remittance":
		return crossborderExportRemittance(args[1:])
	default:
		return fmt.Errorf("crossborder: unknown subcommand %q", args[0])
	}
}

// crossborderOutboundCreate implements `crossborder outbound-create` →
// POST /v1/crossborder/outbound.
func crossborderOutboundCreate(args []string) error {
	fs, cf := newFlagSet("crossborder outbound-create")
	name := fs.String("beneficiary-name", "", "beneficiary name (required)")
	swift := fs.String("beneficiary-swift", "", "beneficiary SWIFT/BIC (required)")
	account := fs.String("beneficiary-account", "", "beneficiary account number/IBAN (required)")
	country := fs.String("beneficiary-country", "", "beneficiary ISO country code (required)")
	currency := fs.String("currency", "", "foreign ISO currency code, e.g. USD (required)")
	amount := fs.Int64("amount", 0, "foreign amount in minor units, e.g. cents (required)")
	purpose := fs.String("purpose-code", "", "RBI purpose code (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *name == "" || *swift == "" || *account == "" || *country == "" || *currency == "" || *amount <= 0 || *purpose == "" {
		return fmt.Errorf("crossborder outbound-create: --beneficiary-name, --beneficiary-swift, --beneficiary-account, --beneficiary-country, --currency, --amount (>0) and --purpose-code are required")
	}
	body := map[string]any{
		"beneficiaryName":    *name,
		"beneficiarySwift":   *swift,
		"beneficiaryAccount": *account,
		"beneficiaryCountry": *country,
		"currency":           *currency,
		"foreignAmountMinor": *amount,
		"purposeCode":        *purpose,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/crossborder/outbound", body, nil)
}

// crossborderOutboundList implements `crossborder outbound-list` →
// GET /v1/crossborder/outbound.
func crossborderOutboundList(args []string) error {
	fs, cf := newFlagSet("crossborder outbound-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/crossborder/outbound", nil, nil)
}

// crossborderOutboundGet implements `crossborder outbound-get` →
// GET /v1/crossborder/outbound/{remittanceId}.
func crossborderOutboundGet(args []string) error {
	fs, cf := newFlagSet("crossborder outbound-get")
	id := fs.String("id", "", "remittance id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("crossborder outbound-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/crossborder/outbound/"+*id, nil, nil)
}

// crossborderOutboundQuote implements `crossborder outbound-quote` →
// POST /v1/crossborder/outbound/quote.
func crossborderOutboundQuote(args []string) error {
	fs, cf := newFlagSet("crossborder outbound-quote")
	currency := fs.String("currency", "", "foreign ISO currency code (required)")
	amount := fs.Int64("amount", 0, "foreign amount in minor units (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *currency == "" || *amount <= 0 {
		return fmt.Errorf("crossborder outbound-quote: --currency and --amount (>0) are required")
	}
	body := map[string]any{
		"currency":           *currency,
		"foreignAmountMinor": *amount,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/crossborder/outbound/quote", body, nil)
}

// crossborderMarkRemitted implements `crossborder outbound-mark-remitted` →
// POST /v1/crossborder/outbound/{remittanceId}/mark-remitted.
func crossborderMarkRemitted(args []string) error {
	fs, cf := newFlagSet("crossborder outbound-mark-remitted")
	id := fs.String("id", "", "remittance id (required)")
	reference := fs.String("reference", "", "remittance reference (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *reference == "" {
		return fmt.Errorf("crossborder outbound-mark-remitted: --id and --reference are required")
	}
	body := map[string]any{"remittanceReference": *reference}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/crossborder/outbound/"+*id+"/mark-remitted", body, nil)
}

// crossborderMarkFailed implements `crossborder outbound-mark-failed` →
// POST /v1/crossborder/outbound/{remittanceId}/mark-failed.
func crossborderMarkFailed(args []string) error {
	fs, cf := newFlagSet("crossborder outbound-mark-failed")
	id := fs.String("id", "", "remittance id (required)")
	reason := fs.String("reason", "", "failure reason (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *reason == "" {
		return fmt.Errorf("crossborder outbound-mark-failed: --id and --reason are required")
	}
	body := map[string]any{"reason": *reason}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/crossborder/outbound/"+*id+"/mark-failed", body, nil)
}

// crossborderExportCreate implements `crossborder export-create` →
// POST /v1/crossborder/export-invoices.
func crossborderExportCreate(args []string) error {
	fs, cf := newFlagSet("crossborder export-create")
	invoiceNumber := fs.String("invoice-number", "", "export invoice number (required)")
	buyerCountry := fs.String("buyer-country", "", "buyer ISO country code (required)")
	currency := fs.String("currency", "", "foreign ISO currency code (required)")
	amount := fs.Int64("amount", 0, "foreign amount in minor units (required)")
	purpose := fs.String("purpose-code", "", "RBI purpose code (required)")
	lut := fs.Bool("lut", false, "supply under LUT (no IGST)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *invoiceNumber == "" || *buyerCountry == "" || *currency == "" || *amount <= 0 || *purpose == "" {
		return fmt.Errorf("crossborder export-create: --invoice-number, --buyer-country, --currency, --amount (>0) and --purpose-code are required")
	}
	body := map[string]any{
		"invoiceNumber":      *invoiceNumber,
		"buyerCountry":       *buyerCountry,
		"currency":           *currency,
		"foreignAmountMinor": *amount,
		"purposeCode":        *purpose,
	}
	if *lut {
		body["lut"] = true
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/crossborder/export-invoices", body, nil)
}

// crossborderExportList implements `crossborder export-list` →
// GET /v1/crossborder/export-invoices.
func crossborderExportList(args []string) error {
	fs, cf := newFlagSet("crossborder export-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/crossborder/export-invoices", nil, nil)
}

// crossborderExportGet implements `crossborder export-get` →
// GET /v1/crossborder/export-invoices/{exportInvoiceId}.
func crossborderExportGet(args []string) error {
	fs, cf := newFlagSet("crossborder export-get")
	id := fs.String("id", "", "export invoice id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("crossborder export-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/crossborder/export-invoices/"+*id, nil, nil)
}

// crossborderExportRemittance implements `crossborder export-remittance` →
// POST /v1/crossborder/export-invoices/{exportInvoiceId}/remittances.
func crossborderExportRemittance(args []string) error {
	fs, cf := newFlagSet("crossborder export-remittance")
	id := fs.String("id", "", "export invoice id (required)")
	amount := fs.Int64("amount", 0, "foreign amount received in minor units (required)")
	fira := fs.String("fira", "", "FIRA reference (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *amount <= 0 || *fira == "" {
		return fmt.Errorf("crossborder export-remittance: --id, --amount (>0) and --fira are required")
	}
	body := map[string]any{
		"foreignAmountMinor": *amount,
		"firaReference":      *fira,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/crossborder/export-invoices/"+*id+"/remittances", body, nil)
}
