package main

import (
	"fmt"
	"net/http"
	"net/url"
)

// runTDS dispatches the `tds` command group (TDS/TCS deductions, certificates
// and Form 24Q/26Q/27EQ returns).
func runTDS(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("tds: expected a subcommand (deduction-record, deduction-list, deduction-get, certificate, summary, return-prepare, return-list, return-get, return-file, return-export)")
	}
	switch args[0] {
	case "deduction-record":
		return tdsDeductionRecord(args[1:])
	case "deduction-list":
		return tdsDeductionList(args[1:])
	case "deduction-get":
		return tdsDeductionGet(args[1:])
	case "certificate":
		return tdsCertificate(args[1:])
	case "summary":
		return tdsSummary(args[1:])
	case "return-prepare":
		return tdsReturnPrepare(args[1:])
	case "return-list":
		return tdsReturnList(args[1:])
	case "return-get":
		return tdsReturnGet(args[1:])
	case "return-file":
		return tdsReturnFile(args[1:])
	case "return-export":
		return tdsReturnExport(args[1:])
	default:
		return fmt.Errorf("tds: unknown subcommand %q", args[0])
	}
}

// tdsDeductionRecord implements `tds deduction-record` → POST /v1/tds/deductions.
func tdsDeductionRecord(args []string) error {
	fs, cf := newFlagSet("tds deduction-record")
	kind := fs.String("kind", "TDS", "deduction kind: TDS|TCS")
	section := fs.String("section", "", "IT Act section, e.g. 194J (required)")
	deducteeName := fs.String("deductee-name", "", "deductee name (required)")
	deducteePan := fs.String("deductee-pan", "", "deductee PAN")
	gross := fs.Int64("gross", 0, "gross amount in paise / minor units (required)")
	rateBps := fs.Int("rate-bps", 0, "deduction rate in basis points (required)")
	txnRef := fs.String("transaction-ref", "", "source transaction reference")
	deductedOn := fs.String("deducted-on", "", "deduction date YYYY-MM-DD (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *section == "" || *deducteeName == "" || *gross <= 0 || *rateBps <= 0 || *deductedOn == "" {
		return fmt.Errorf("tds deduction-record: --section, --deductee-name, --gross (>0), --rate-bps (>0) and --deducted-on are required")
	}
	body := map[string]any{
		"kind":         *kind,
		"section":      *section,
		"deducteeName": *deducteeName,
		"grossMinor":   *gross,
		"rateBps":      *rateBps,
		"deductedOn":   *deductedOn,
	}
	if *deducteePan != "" {
		body["deducteePan"] = *deducteePan
	}
	if *txnRef != "" {
		body["transactionRef"] = *txnRef
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/tds/deductions", body, nil)
}

// tdsDeductionList implements `tds deduction-list` → GET /v1/tds/deductions.
func tdsDeductionList(args []string) error {
	fs, cf := newFlagSet("tds deduction-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/tds/deductions", nil, nil)
}

// tdsDeductionGet implements `tds deduction-get` →
// GET /v1/tds/deductions/{deductionId}.
func tdsDeductionGet(args []string) error {
	fs, cf := newFlagSet("tds deduction-get")
	id := fs.String("id", "", "deduction id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("tds deduction-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/tds/deductions/"+*id, nil, nil)
}

// tdsCertificate implements `tds certificate` →
// POST /v1/tds/deductions/{deductionId}/certificate.
func tdsCertificate(args []string) error {
	fs, cf := newFlagSet("tds certificate")
	id := fs.String("id", "", "deduction id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("tds certificate: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/tds/deductions/"+*id+"/certificate", nil, nil)
}

// tdsSummary implements `tds summary` → GET /v1/tds/summary.
func tdsSummary(args []string) error {
	fs, cf := newFlagSet("tds summary")
	quarter := fs.String("quarter", "", "quarter, e.g. 2026-Q1 (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *quarter == "" {
		return fmt.Errorf("tds summary: --quarter is required")
	}
	q := url.Values{}
	q.Set("quarter", *quarter)
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, withQuery("/v1/tds/summary", q), nil, nil)
}

// tdsReturnPrepare implements `tds return-prepare` → POST /v1/tds/returns/prepare.
func tdsReturnPrepare(args []string) error {
	fs, cf := newFlagSet("tds return-prepare")
	form := fs.String("form", "FORM_26Q", "return form: FORM_24Q|FORM_26Q|FORM_27EQ")
	quarter := fs.String("quarter", "", "quarter, e.g. 2026-Q1 (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *quarter == "" {
		return fmt.Errorf("tds return-prepare: --quarter is required")
	}
	body := map[string]any{
		"form":    *form,
		"quarter": *quarter,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/tds/returns/prepare", body, nil)
}

// tdsReturnList implements `tds return-list` → GET /v1/tds/returns.
func tdsReturnList(args []string) error {
	fs, cf := newFlagSet("tds return-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/tds/returns", nil, nil)
}

// tdsReturnGet implements `tds return-get` → GET /v1/tds/returns/{returnId}.
func tdsReturnGet(args []string) error {
	fs, cf := newFlagSet("tds return-get")
	id := fs.String("id", "", "return id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("tds return-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/tds/returns/"+*id, nil, nil)
}

// tdsReturnFile implements `tds return-file` →
// POST /v1/tds/returns/{returnId}/file.
func tdsReturnFile(args []string) error {
	fs, cf := newFlagSet("tds return-file")
	id := fs.String("id", "", "return id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("tds return-file: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/tds/returns/"+*id+"/file", nil, nil)
}

// tdsReturnExport implements `tds return-export` →
// GET /v1/tds/returns/{returnId}/export (FVU/text export).
func tdsReturnExport(args []string) error {
	fs, cf := newFlagSet("tds return-export")
	id := fs.String("id", "", "return id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("tds return-export: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/tds/returns/"+*id+"/export", nil, nil)
}
