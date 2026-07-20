package main

import (
	"encoding/json"
	"fmt"
	"net/http"
)

// runPayroll dispatches the `payroll` command group (salary batches paid out
// with statutory deductions, maker-checker at create→approve).
func runPayroll(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("payroll: expected a subcommand (batch-create, batch-list, batch-get, batch-approve, batch-reject, lines, slip)")
	}
	switch args[0] {
	case "batch-create":
		return payrollBatchCreate(args[1:])
	case "batch-list":
		return payrollBatchList(args[1:])
	case "batch-get":
		return payrollBatchGet(args[1:])
	case "batch-approve":
		return payrollBatchApprove(args[1:])
	case "batch-reject":
		return payrollBatchReject(args[1:])
	case "lines":
		return payrollLines(args[1:])
	case "slip":
		return payrollSlip(args[1:])
	default:
		return fmt.Errorf("payroll: unknown subcommand %q", args[0])
	}
}

// payrollBatchCreate implements `payroll batch-create` → POST /v1/payroll/batches.
func payrollBatchCreate(args []string) error {
	fs, cf := newFlagSet("payroll batch-create")
	currency := fs.String("currency", "INR", "ISO currency code")
	period := fs.String("period", "", "pay period, e.g. 2026-06")
	description := fs.String("description", "", "batch description")
	lines := fs.String("lines", "", `salary lines JSON array, e.g. `+
		`'[{"employeeRef":"E1","rail":"IMPS","destination":"HDFC0000001|12345","grossMinor":5000000,"pfMinor":180000,"tdsMinor":250000}]' (required)`)
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *lines == "" {
		return fmt.Errorf("payroll batch-create: --lines is required")
	}
	var parsedLines []map[string]any
	if err := json.Unmarshal([]byte(*lines), &parsedLines); err != nil {
		return fmt.Errorf("invalid --lines JSON: %w", err)
	}
	body := map[string]any{
		"currency": *currency,
		"lines":    parsedLines,
	}
	if *period != "" {
		body["period"] = *period
	}
	if *description != "" {
		body["description"] = *description
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payroll/batches", body, nil)
}

// payrollBatchList implements `payroll batch-list` → GET /v1/payroll/batches.
func payrollBatchList(args []string) error {
	fs, cf := newFlagSet("payroll batch-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/payroll/batches", nil, nil)
}

// payrollBatchGet implements `payroll batch-get` → GET /v1/payroll/batches/{id}.
func payrollBatchGet(args []string) error {
	fs, cf := newFlagSet("payroll batch-get")
	id := fs.String("id", "", "batch id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("payroll batch-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/payroll/batches/"+*id, nil, nil)
}

// payrollBatchApprove implements `payroll batch-approve` →
// POST /v1/payroll/batches/{id}/approve.
func payrollBatchApprove(args []string) error {
	fs, cf := newFlagSet("payroll batch-approve")
	id := fs.String("id", "", "batch id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("payroll batch-approve: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payroll/batches/"+*id+"/approve", nil, idempotent())
}

// payrollBatchReject implements `payroll batch-reject` →
// POST /v1/payroll/batches/{id}/reject.
func payrollBatchReject(args []string) error {
	fs, cf := newFlagSet("payroll batch-reject")
	id := fs.String("id", "", "batch id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("payroll batch-reject: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/payroll/batches/"+*id+"/reject", nil, nil)
}

// payrollLines implements `payroll lines` → GET /v1/payroll/batches/{id}/lines.
func payrollLines(args []string) error {
	fs, cf := newFlagSet("payroll lines")
	id := fs.String("id", "", "batch id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("payroll lines: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/payroll/batches/"+*id+"/lines", nil, nil)
}

// payrollSlip implements `payroll slip` →
// GET /v1/payroll/batches/{id}/lines/{lineId}/slip.
func payrollSlip(args []string) error {
	fs, cf := newFlagSet("payroll slip")
	id := fs.String("id", "", "batch id (required)")
	line := fs.String("line", "", "line id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *line == "" {
		return fmt.Errorf("payroll slip: --id and --line are required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/payroll/batches/"+*id+"/lines/"+*line+"/slip", nil, nil)
}
