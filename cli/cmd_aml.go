package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
)

// runAML dispatches the `aml` command group (sanctions screening, mule scans,
// transaction monitoring, cases and STR reports).
func runAML(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("aml: expected a subcommand (screen, mule-scan, monitor, alerts, cases, case-create, case-close, str-list, str-create)")
	}
	switch args[0] {
	case "screen":
		return amlScreen(args[1:])
	case "mule-scan":
		return amlMuleScan(args[1:])
	case "monitor":
		return amlMonitor(args[1:])
	case "alerts":
		return amlAlerts(args[1:])
	case "cases":
		return amlCases(args[1:])
	case "case-create":
		return amlCaseCreate(args[1:])
	case "case-close":
		return amlCaseClose(args[1:])
	case "str-list":
		return amlStrList(args[1:])
	case "str-create":
		return amlStrCreate(args[1:])
	default:
		return fmt.Errorf("aml: unknown subcommand %q", args[0])
	}
}

// amlScreen implements `aml screen` → POST /v1/aml/screen.
func amlScreen(args []string) error {
	fs, cf := newFlagSet("aml screen")
	partyType := fs.String("party-type", "", "party type: INDIVIDUAL|BUSINESS|BENEFICIARY (required)")
	partyName := fs.String("party-name", "", "party name (required)")
	identifier := fs.String("identifier", "", "party identifier (PAN/GSTIN/account)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *partyType == "" || *partyName == "" {
		return fmt.Errorf("aml screen: --party-type and --party-name are required")
	}
	body := map[string]any{
		"partyType": *partyType,
		"partyName": *partyName,
	}
	if *identifier != "" {
		body["identifier"] = *identifier
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/aml/screen", body, nil)
}

// amlMuleScan implements `aml mule-scan` → POST /v1/aml/mule-scan.
func amlMuleScan(args []string) error {
	fs, cf := newFlagSet("aml mule-scan")
	beneficiary := fs.String("beneficiary-ref", "", "beneficiary reference (required)")
	inbound := fs.Int64("inbound", 0, "inbound amount in paise / minor units")
	outbound := fs.Int64("outbound", 0, "outbound amount in paise / minor units")
	inboundCount := fs.Int("inbound-count", 0, "number of inbound transactions")
	outboundCount := fs.Int("outbound-count", 0, "number of outbound transactions")
	counterparties := fs.Int("distinct-counterparties", 0, "distinct counterparties")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *beneficiary == "" {
		return fmt.Errorf("aml mule-scan: --beneficiary-ref is required")
	}
	body := map[string]any{"beneficiaryRef": *beneficiary}
	if *inbound > 0 {
		body["inboundMinor"] = *inbound
	}
	if *outbound > 0 {
		body["outboundMinor"] = *outbound
	}
	if *inboundCount > 0 {
		body["inboundCount"] = *inboundCount
	}
	if *outboundCount > 0 {
		body["outboundCount"] = *outboundCount
	}
	if *counterparties > 0 {
		body["distinctCounterparties"] = *counterparties
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/aml/mule-scan", body, nil)
}

// amlMonitor implements `aml monitor` → POST /v1/aml/monitor.
func amlMonitor(args []string) error {
	fs, cf := newFlagSet("aml monitor")
	txnRef := fs.String("transaction-ref", "", "transaction reference (required)")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	mcc := fs.Int("mcc", 0, "merchant category code")
	country := fs.String("country", "", "ISO country code")
	beneficiary := fs.String("beneficiary-ref", "", "beneficiary reference")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *txnRef == "" || *amount <= 0 {
		return fmt.Errorf("aml monitor: --transaction-ref and --amount (>0) are required")
	}
	body := map[string]any{
		"transactionRef": *txnRef,
		"amountMinor":    *amount,
		"currency":       *currency,
	}
	if *mcc > 0 {
		body["mcc"] = *mcc
	}
	if *country != "" {
		body["countryCode"] = *country
	}
	if *beneficiary != "" {
		body["beneficiaryRef"] = *beneficiary
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/aml/monitor", body, nil)
}

// amlAlerts implements `aml alerts` → GET /v1/aml/alerts.
func amlAlerts(args []string) error {
	fs, cf := newFlagSet("aml alerts")
	status := fs.String("status", "", "filter by status: OPEN|DISMISSED|ESCALATED")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	q := url.Values{}
	if *status != "" {
		q.Set("status", *status)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, withQuery("/v1/aml/alerts", q), nil, nil)
}

// amlCases implements `aml cases` → GET /v1/aml/cases.
func amlCases(args []string) error {
	fs, cf := newFlagSet("aml cases")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/aml/cases", nil, nil)
}

// amlCaseCreate implements `aml case-create` → POST /v1/aml/cases.
func amlCaseCreate(args []string) error {
	fs, cf := newFlagSet("aml case-create")
	subject := fs.String("subject", "", "case subject (required)")
	description := fs.String("description", "", "case description")
	alertIds := fs.String("alert-ids", "", "JSON array of alert ids, e.g. '[\"<uuid>\"]'")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *subject == "" {
		return fmt.Errorf("aml case-create: --subject is required")
	}
	body := map[string]any{"subject": *subject}
	if *description != "" {
		body["description"] = *description
	}
	if *alertIds != "" {
		var parsed []string
		if err := json.Unmarshal([]byte(*alertIds), &parsed); err != nil {
			return fmt.Errorf("invalid --alert-ids JSON: %w", err)
		}
		body["alertIds"] = parsed
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/aml/cases", body, nil)
}

// amlCaseClose implements `aml case-close` → POST /v1/aml/cases/{id}/close.
func amlCaseClose(args []string) error {
	fs, cf := newFlagSet("aml case-close")
	id := fs.String("id", "", "case id (required)")
	disposition := fs.String("disposition", "", "disposition: CLEARED|FALSE_POSITIVE|STR_FILED (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *disposition == "" {
		return fmt.Errorf("aml case-close: --id and --disposition are required")
	}
	body := map[string]any{"disposition": *disposition}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/aml/cases/"+*id+"/close", body, nil)
}

// amlStrList implements `aml str-list` → GET /v1/aml/str-reports.
func amlStrList(args []string) error {
	fs, cf := newFlagSet("aml str-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/aml/str-reports", nil, nil)
}

// amlStrCreate implements `aml str-create` → POST /v1/aml/str-reports.
func amlStrCreate(args []string) error {
	fs, cf := newFlagSet("aml str-create")
	subject := fs.String("subject", "", "STR subject (required)")
	grounds := fs.String("grounds", "", "grounds for suspicion (required)")
	caseId := fs.String("case", "", "related case id")
	fileNow := fs.Bool("file-immediately", false, "file the STR with the FIU immediately")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *subject == "" || *grounds == "" {
		return fmt.Errorf("aml str-create: --subject and --grounds are required")
	}
	body := map[string]any{
		"subject": *subject,
		"grounds": *grounds,
	}
	if *caseId != "" {
		body["caseId"] = *caseId
	}
	if *fileNow {
		body["fileImmediately"] = true
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/aml/str-reports", body, nil)
}
