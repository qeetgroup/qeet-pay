package main

import (
	"fmt"
	"net/http"
	"net/url"
	"strconv"
)

// runAnalytics dispatches the `analytics` command group.
func runAnalytics(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("analytics: expected a subcommand (tpv, mrr, arr, success-rate, cash-flow)")
	}
	switch args[0] {
	case "tpv":
		return analyticsTPV(args[1:])
	case "mrr":
		return analyticsMRR(args[1:])
	case "arr":
		return analyticsARR(args[1:])
	case "success-rate":
		return analyticsSuccessRate(args[1:])
	case "cash-flow":
		return analyticsCashFlow(args[1:])
	default:
		return fmt.Errorf("analytics: unknown subcommand %q (want: tpv, mrr, arr, success-rate, cash-flow)", args[0])
	}
}

// analyticsTPV implements `analytics tpv` → GET /v1/analytics/tpv.
func analyticsTPV(args []string) error {
	fs, cf := newFlagSet("analytics tpv")
	from := fs.String("from", "", "RFC-3339 start, e.g. 2026-06-01T00:00:00Z (required)")
	to := fs.String("to", "", "RFC-3339 end (required)")
	granularity := fs.String("granularity", "DAY", "bucket granularity: HOUR|DAY|WEEK|MONTH")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *from == "" || *to == "" {
		return fmt.Errorf("analytics tpv: --from and --to are required")
	}
	q := url.Values{}
	q.Set("from", *from)
	q.Set("to", *to)
	q.Set("granularity", *granularity)
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, withQuery("/v1/analytics/tpv", q), nil, nil)
}

// analyticsMRR implements `analytics mrr` → GET /v1/analytics/mrr.
func analyticsMRR(args []string) error {
	fs, cf := newFlagSet("analytics mrr")
	from := fs.String("from", "", "RFC-3339 start (required)")
	to := fs.String("to", "", "RFC-3339 end (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *from == "" || *to == "" {
		return fmt.Errorf("analytics mrr: --from and --to are required")
	}
	q := url.Values{}
	q.Set("from", *from)
	q.Set("to", *to)
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, withQuery("/v1/analytics/mrr", q), nil, nil)
}

// analyticsARR implements `analytics arr` → GET /v1/analytics/arr.
func analyticsARR(args []string) error {
	fs, cf := newFlagSet("analytics arr")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/analytics/arr", nil, nil)
}

// analyticsSuccessRate implements `analytics success-rate` → GET /v1/analytics/success-rate.
func analyticsSuccessRate(args []string) error {
	fs, cf := newFlagSet("analytics success-rate")
	from := fs.String("from", "", "RFC-3339 start (required)")
	to := fs.String("to", "", "RFC-3339 end (required)")
	method := fs.String("method", "", "filter by payment method: UPI|CARD|NET_BANKING|WALLET")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *from == "" || *to == "" {
		return fmt.Errorf("analytics success-rate: --from and --to are required")
	}
	q := url.Values{}
	q.Set("from", *from)
	q.Set("to", *to)
	if *method != "" {
		q.Set("method", *method)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, withQuery("/v1/analytics/success-rate", q), nil, nil)
}

// analyticsCashFlow implements `analytics cash-flow` → GET /v1/analytics/cash-flow-forecast.
func analyticsCashFlow(args []string) error {
	fs, cf := newFlagSet("analytics cash-flow")
	horizonDays := fs.Int("horizon-days", 30, "projection horizon in days")
	windowDays := fs.Int("window-days", 30, "trailing window used for the net-TPV trend")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	q := url.Values{}
	q.Set("horizonDays", strconv.Itoa(*horizonDays))
	q.Set("windowDays", strconv.Itoa(*windowDays))
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, withQuery("/v1/analytics/cash-flow-forecast", q), nil, nil)
}
