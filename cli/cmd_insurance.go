package main

import (
	"fmt"
	"net/http"
)

// runInsurance dispatches the `insurance` command group (embedded cover policies
// + claims lifecycle).
func runInsurance(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("insurance: expected a subcommand (policy-issue, policy-list, policy-get, policy-cancel, claim-file, claim-approve, claim-reject)")
	}
	switch args[0] {
	case "policy-issue":
		return insurancePolicyIssue(args[1:])
	case "policy-list":
		return insurancePolicyList(args[1:])
	case "policy-get":
		return insurancePolicyGet(args[1:])
	case "policy-cancel":
		return insurancePolicyCancel(args[1:])
	case "claim-file":
		return insuranceClaimFile(args[1:])
	case "claim-approve":
		return insuranceClaimAction(args[1:], "approve")
	case "claim-reject":
		return insuranceClaimReject(args[1:])
	default:
		return fmt.Errorf("insurance: unknown subcommand %q", args[0])
	}
}

// insurancePolicyIssue implements `insurance policy-issue` →
// POST /v1/insurance/policies.
func insurancePolicyIssue(args []string) error {
	fs, cf := newFlagSet("insurance policy-issue")
	product := fs.String("product", "", "product: PAYMENT_PROTECTION|FRAUD_COVER|SUBSCRIPTION_INTERRUPTION (required)")
	holder := fs.String("holder", "", "policy holder reference (required)")
	premium := fs.Int64("premium", 0, "premium in paise / minor units (required)")
	cover := fs.Int64("cover", 0, "cover amount in paise / minor units (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *product == "" || *holder == "" || *premium <= 0 || *cover <= 0 {
		return fmt.Errorf("insurance policy-issue: --product, --holder, --premium (>0) and --cover (>0) are required")
	}
	body := map[string]any{
		"product":          *product,
		"holderRef":        *holder,
		"premiumMinor":     *premium,
		"coverAmountMinor": *cover,
		"currency":         *currency,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/insurance/policies", body, nil)
}

// insurancePolicyList implements `insurance policy-list` →
// GET /v1/insurance/policies.
func insurancePolicyList(args []string) error {
	fs, cf := newFlagSet("insurance policy-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/insurance/policies", nil, nil)
}

// insurancePolicyGet implements `insurance policy-get` →
// GET /v1/insurance/policies/{policyId}.
func insurancePolicyGet(args []string) error {
	fs, cf := newFlagSet("insurance policy-get")
	id := fs.String("id", "", "policy id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("insurance policy-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/insurance/policies/"+*id, nil, nil)
}

// insurancePolicyCancel implements `insurance policy-cancel` →
// POST /v1/insurance/policies/{policyId}/cancel.
func insurancePolicyCancel(args []string) error {
	fs, cf := newFlagSet("insurance policy-cancel")
	id := fs.String("id", "", "policy id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("insurance policy-cancel: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/insurance/policies/"+*id+"/cancel", nil, nil)
}

// insuranceClaimFile implements `insurance claim-file` →
// POST /v1/insurance/policies/{policyId}/claims.
func insuranceClaimFile(args []string) error {
	fs, cf := newFlagSet("insurance claim-file")
	policy := fs.String("policy", "", "policy id (required)")
	amount := fs.Int64("amount", 0, "claim amount in paise / minor units (required)")
	reason := fs.String("reason", "", "claim reason")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *policy == "" || *amount <= 0 {
		return fmt.Errorf("insurance claim-file: --policy and --amount (>0) are required")
	}
	body := map[string]any{"amountMinor": *amount}
	if *reason != "" {
		body["reason"] = *reason
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/insurance/policies/"+*policy+"/claims", body, nil)
}

// insuranceClaimAction implements approve →
// POST /v1/insurance/claims/{claimId}/{action}.
func insuranceClaimAction(args []string, action string) error {
	fs, cf := newFlagSet("insurance claim-" + action)
	id := fs.String("id", "", "claim id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("insurance claim-%s: --id is required", action)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/insurance/claims/"+*id+"/"+action, nil, nil)
}

// insuranceClaimReject implements `insurance claim-reject` →
// POST /v1/insurance/claims/{claimId}/reject.
func insuranceClaimReject(args []string) error {
	fs, cf := newFlagSet("insurance claim-reject")
	id := fs.String("id", "", "claim id (required)")
	note := fs.String("note", "", "rejection note")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("insurance claim-reject: --id is required")
	}
	var body map[string]any
	if *note != "" {
		body = map[string]any{"note": *note}
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/insurance/claims/"+*id+"/reject", body, nil)
}
