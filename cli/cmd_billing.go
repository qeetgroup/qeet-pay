package main

import (
	"fmt"
	"net/http"
	"net/url"
	"strconv"
)

// runBilling dispatches the `billing` command group (plans / subscriptions / invoices).
func runBilling(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("billing: expected a subcommand (plan-create, subscription-create, subscription-get, subscription-pause, subscription-resume, subscription-cancel, invoice-get, invoice-pay)")
	}
	switch args[0] {
	case "plan-create":
		return billingPlanCreate(args[1:])
	case "subscription-create":
		return billingSubscriptionCreate(args[1:])
	case "subscription-get":
		return billingSubscriptionGet(args[1:])
	case "subscription-pause":
		return billingSubscriptionAction(args[1:], "pause")
	case "subscription-resume":
		return billingSubscriptionAction(args[1:], "resume")
	case "subscription-cancel":
		return billingSubscriptionCancel(args[1:])
	case "invoice-get":
		return billingInvoiceGet(args[1:])
	case "invoice-pay":
		return billingInvoicePay(args[1:])
	default:
		return fmt.Errorf("billing: unknown subcommand %q", args[0])
	}
}

// billingPlanCreate implements `billing plan-create` → POST /v1/plans.
func billingPlanCreate(args []string) error {
	fs, cf := newFlagSet("billing plan-create")
	code := fs.String("code", "", "unique plan code (required)")
	name := fs.String("name", "", "plan name (required)")
	amount := fs.Int64("amount", 0, "recurring amount in paise / minor units (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	interval := fs.String("interval", "MONTH", "billing interval: MONTH|YEAR")
	pricingModel := fs.String("pricing-model", "", "FLAT|PER_UNIT|TIERED|VOLUME|HYBRID")
	tiers := fs.String("tiers", "", "tier configuration JSON (for tiered/volume models)")
	usageMetricKey := fs.String("usage-metric-key", "", "metric key for usage-based pricing")
	trialDays := fs.Int("trial-days", 0, "free trial length in days")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *code == "" || *name == "" || *amount <= 0 {
		return fmt.Errorf("billing plan-create: --code, --name and --amount (>0) are required")
	}

	body := map[string]any{
		"code":        *code,
		"name":        *name,
		"amountMinor": *amount,
		"currency":    *currency,
		"interval":    *interval,
	}
	if *pricingModel != "" {
		body["pricingModel"] = *pricingModel
	}
	if *tiers != "" {
		body["tiers"] = *tiers
	}
	if *usageMetricKey != "" {
		body["usageMetricKey"] = *usageMetricKey
	}
	if *trialDays > 0 {
		body["trialDays"] = *trialDays
	}

	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/plans", body, nil)
}

// billingSubscriptionCreate implements `billing subscription-create` → POST /v1/subscriptions.
func billingSubscriptionCreate(args []string) error {
	fs, cf := newFlagSet("billing subscription-create")
	plan := fs.String("plan", "", "plan id (required)")
	customer := fs.String("customer", "", "your customer reference (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *plan == "" || *customer == "" {
		return fmt.Errorf("billing subscription-create: --plan and --customer are required")
	}

	body := map[string]any{
		"planId":      *plan,
		"customerRef": *customer,
	}

	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/subscriptions", body, nil)
}

// billingSubscriptionGet implements `billing subscription-get` → GET /v1/subscriptions/{id}.
func billingSubscriptionGet(args []string) error {
	fs, cf := newFlagSet("billing subscription-get")
	id := fs.String("id", "", "subscription id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("billing subscription-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/subscriptions/"+*id, nil, nil)
}

// billingSubscriptionAction handles pause/resume → POST /v1/subscriptions/{id}/{action}.
func billingSubscriptionAction(args []string, action string) error {
	fs, cf := newFlagSet("billing subscription-" + action)
	id := fs.String("id", "", "subscription id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("billing subscription-%s: --id is required", action)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/subscriptions/"+*id+"/"+action, nil, nil)
}

// billingSubscriptionCancel implements `billing subscription-cancel`
// → POST /v1/subscriptions/{id}/cancel?atPeriodEnd=.
func billingSubscriptionCancel(args []string) error {
	fs, cf := newFlagSet("billing subscription-cancel")
	id := fs.String("id", "", "subscription id (required)")
	atPeriodEnd := fs.Bool("at-period-end", false, "cancel at the end of the current period instead of now")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("billing subscription-cancel: --id is required")
	}
	q := url.Values{}
	if *atPeriodEnd {
		q.Set("atPeriodEnd", strconv.FormatBool(*atPeriodEnd))
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, withQuery("/v1/subscriptions/"+*id+"/cancel", q), nil, nil)
}

// billingInvoiceGet implements `billing invoice-get` → GET /v1/invoices/{id}.
func billingInvoiceGet(args []string) error {
	fs, cf := newFlagSet("billing invoice-get")
	id := fs.String("id", "", "invoice id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("billing invoice-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/invoices/"+*id, nil, nil)
}

// billingInvoicePay implements `billing invoice-pay` → POST /v1/invoices/{id}/pay.
func billingInvoicePay(args []string) error {
	fs, cf := newFlagSet("billing invoice-pay")
	id := fs.String("id", "", "invoice id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("billing invoice-pay: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/invoices/"+*id+"/pay", nil, idempotent())
}
