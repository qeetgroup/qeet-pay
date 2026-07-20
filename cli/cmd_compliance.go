package main

import (
	"fmt"
	"net/http"
	"net/url"
	"strconv"
)

// runCompliance dispatches the `compliance` command group (aggregate
// compliance-health scorecard across KYB/KYC/AML/GST/TDS surfaces).
func runCompliance(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("compliance: expected a subcommand (health)")
	}
	switch args[0] {
	case "health":
		return complianceHealth(args[1:])
	default:
		return fmt.Errorf("compliance: unknown subcommand %q (want: health)", args[0])
	}
}

// complianceHealth implements `compliance health` →
// GET /v1/analytics/compliance-health.
func complianceHealth(args []string) error {
	fs, cf := newFlagSet("compliance health")
	windowDays := fs.Int("window-days", 30, "trailing window in days")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	q := url.Values{}
	q.Set("windowDays", strconv.Itoa(*windowDays))
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, withQuery("/v1/analytics/compliance-health", q), nil, nil)
}
