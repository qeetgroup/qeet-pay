package main

import (
	"fmt"
	"net/http"
	"net/url"
)

// runRevRec dispatches the `revrec` command group (IndAS 115 revenue-recognition
// schedules + ratable recognition).
func runRevRec(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("revrec: expected a subcommand (schedule-create, schedule-list, schedule-get, recognize)")
	}
	switch args[0] {
	case "schedule-create":
		return revrecScheduleCreate(args[1:])
	case "schedule-list":
		return revrecScheduleList(args[1:])
	case "schedule-get":
		return revrecScheduleGet(args[1:])
	case "recognize":
		return revrecRecognize(args[1:])
	default:
		return fmt.Errorf("revrec: unknown subcommand %q", args[0])
	}
}

// revrecScheduleCreate implements `revrec schedule-create` →
// POST /v1/revrec/schedules.
func revrecScheduleCreate(args []string) error {
	fs, cf := newFlagSet("revrec schedule-create")
	sourceType := fs.String("source-type", "", "source type, e.g. INVOICE|SUBSCRIPTION (required)")
	sourceRef := fs.String("source-ref", "", "source reference")
	total := fs.Int64("total", 0, "total amount to recognize in paise (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	method := fs.String("method", "STRAIGHT_LINE", "recognition method: STRAIGHT_LINE|IMMEDIATE")
	start := fs.String("start", "", "recognition start date YYYY-MM-DD")
	periods := fs.Int("periods", 0, "number of periods to recognize over")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *sourceType == "" || *total <= 0 {
		return fmt.Errorf("revrec schedule-create: --source-type and --total (>0) are required")
	}
	body := map[string]any{
		"sourceType": *sourceType,
		"totalMinor": *total,
		"currency":   *currency,
		"method":     *method,
	}
	if *sourceRef != "" {
		body["sourceRef"] = *sourceRef
	}
	if *start != "" {
		body["start"] = *start
	}
	if *periods > 0 {
		body["periods"] = *periods
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/revrec/schedules", body, nil)
}

// revrecScheduleList implements `revrec schedule-list` → GET /v1/revrec/schedules.
func revrecScheduleList(args []string) error {
	fs, cf := newFlagSet("revrec schedule-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/revrec/schedules", nil, nil)
}

// revrecScheduleGet implements `revrec schedule-get` →
// GET /v1/revrec/schedules/{scheduleId}.
func revrecScheduleGet(args []string) error {
	fs, cf := newFlagSet("revrec schedule-get")
	id := fs.String("id", "", "schedule id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("revrec schedule-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/revrec/schedules/"+*id, nil, nil)
}

// revrecRecognize implements `revrec recognize` →
// POST /v1/revrec/schedules/{scheduleId}/recognize.
func revrecRecognize(args []string) error {
	fs, cf := newFlagSet("revrec recognize")
	id := fs.String("id", "", "schedule id (required)")
	asOf := fs.String("as-of", "", "recognize entries due on/before this date YYYY-MM-DD")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("revrec recognize: --id is required")
	}
	q := url.Values{}
	if *asOf != "" {
		q.Set("asOf", *asOf)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, withQuery("/v1/revrec/schedules/"+*id+"/recognize", q), nil, nil)
}
