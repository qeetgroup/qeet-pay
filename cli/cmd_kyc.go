package main

import (
	"fmt"
	"net/http"
)

// runKYC dispatches the `kyc` command group (customer KYC with PAN/Aadhaar,
// merchant V-CIP video sessions, and UBO / beneficial-owner registry).
func runKYC(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("kyc: expected a subcommand (customer-create, customer-list, customer-get, customer-pan, customer-consent, aadhaar-initiate, aadhaar-verify, vcip-schedule, vcip-list, vcip-get, vcip-start, vcip-complete, vcip-fail, ubo-add, ubo-list, ubo-get, ubo-remove)")
	}
	switch args[0] {
	case "customer-create":
		return kycCustomerCreate(args[1:])
	case "customer-list":
		return kycCustomerList(args[1:])
	case "customer-get":
		return kycCustomerGet(args[1:])
	case "customer-pan":
		return kycCustomerPan(args[1:])
	case "customer-consent":
		return kycCustomerConsent(args[1:])
	case "aadhaar-initiate":
		return kycAadhaarInitiate(args[1:])
	case "aadhaar-verify":
		return kycAadhaarVerify(args[1:])
	case "vcip-schedule":
		return kycVcipSchedule(args[1:])
	case "vcip-list":
		return kycVcipList(args[1:])
	case "vcip-get":
		return kycVcipGet(args[1:])
	case "vcip-start":
		return kycVcipStart(args[1:])
	case "vcip-complete":
		return kycVcipComplete(args[1:])
	case "vcip-fail":
		return kycVcipFail(args[1:])
	case "ubo-add":
		return kycUboAdd(args[1:])
	case "ubo-list":
		return kycUboList(args[1:])
	case "ubo-get":
		return kycUboGet(args[1:])
	case "ubo-remove":
		return kycUboRemove(args[1:])
	default:
		return fmt.Errorf("kyc: unknown subcommand %q", args[0])
	}
}

// kycCustomerCreate implements `kyc customer-create` → POST /v1/kyc/customers.
func kycCustomerCreate(args []string) error {
	fs, cf := newFlagSet("kyc customer-create")
	customerRef := fs.String("customer-ref", "", "customer reference (required)")
	fullName := fs.String("full-name", "", "customer full name (required)")
	consentGiven := fs.Bool("consent-given", false, "customer consent captured")
	consentArtifact := fs.String("consent-artifact", "", "consent artifact reference")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *customerRef == "" || *fullName == "" {
		return fmt.Errorf("kyc customer-create: --customer-ref and --full-name are required")
	}
	body := map[string]any{
		"customerRef": *customerRef,
		"fullName":    *fullName,
	}
	if *consentGiven {
		body["consentGiven"] = true
	}
	if *consentArtifact != "" {
		body["consentArtifact"] = *consentArtifact
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/kyc/customers", body, nil)
}

// kycCustomerList implements `kyc customer-list` → GET /v1/kyc/customers.
func kycCustomerList(args []string) error {
	fs, cf := newFlagSet("kyc customer-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/kyc/customers", nil, nil)
}

// kycCustomerGet implements `kyc customer-get` → GET /v1/kyc/customers/{id}.
func kycCustomerGet(args []string) error {
	fs, cf := newFlagSet("kyc customer-get")
	id := fs.String("id", "", "customer id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("kyc customer-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/kyc/customers/"+*id, nil, nil)
}

// kycCustomerPan implements `kyc customer-pan` → POST /v1/kyc/customers/{id}/pan.
func kycCustomerPan(args []string) error {
	fs, cf := newFlagSet("kyc customer-pan")
	id := fs.String("id", "", "customer id (required)")
	pan := fs.String("pan", "", "PAN (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *pan == "" {
		return fmt.Errorf("kyc customer-pan: --id and --pan are required")
	}
	body := map[string]any{"pan": *pan}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/kyc/customers/"+*id+"/pan", body, nil)
}

// kycCustomerConsent implements `kyc customer-consent` →
// POST /v1/kyc/customers/{id}/consent.
func kycCustomerConsent(args []string) error {
	fs, cf := newFlagSet("kyc customer-consent")
	id := fs.String("id", "", "customer id (required)")
	artifact := fs.String("consent-artifact", "", "consent artifact reference")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("kyc customer-consent: --id is required")
	}
	var body map[string]any
	if *artifact != "" {
		body = map[string]any{"consentArtifact": *artifact}
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/kyc/customers/"+*id+"/consent", body, nil)
}

// kycAadhaarInitiate implements `kyc aadhaar-initiate` →
// POST /v1/kyc/customers/{id}/aadhaar/initiate.
func kycAadhaarInitiate(args []string) error {
	fs, cf := newFlagSet("kyc aadhaar-initiate")
	id := fs.String("id", "", "customer id (required)")
	aadhaar := fs.String("aadhaar", "", "Aadhaar number (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *aadhaar == "" {
		return fmt.Errorf("kyc aadhaar-initiate: --id and --aadhaar are required")
	}
	body := map[string]any{"aadhaar": *aadhaar}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/kyc/customers/"+*id+"/aadhaar/initiate", body, nil)
}

// kycAadhaarVerify implements `kyc aadhaar-verify` →
// POST /v1/kyc/customers/{id}/aadhaar/verify.
func kycAadhaarVerify(args []string) error {
	fs, cf := newFlagSet("kyc aadhaar-verify")
	id := fs.String("id", "", "customer id (required)")
	txnID := fs.String("txn-id", "", "Aadhaar OTP transaction id (required)")
	otp := fs.String("otp", "", "OTP (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *txnID == "" || *otp == "" {
		return fmt.Errorf("kyc aadhaar-verify: --id, --txn-id and --otp are required")
	}
	body := map[string]any{
		"txnId": *txnID,
		"otp":   *otp,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/kyc/customers/"+*id+"/aadhaar/verify", body, nil)
}

// kycVcipSchedule implements `kyc vcip-schedule` → POST /v1/merchants/kyb/vcip.
func kycVcipSchedule(args []string) error {
	fs, cf := newFlagSet("kyc vcip-schedule")
	subjectName := fs.String("subject-name", "", "subject name (required)")
	subjectRef := fs.String("subject-ref", "", "subject reference")
	agentID := fs.String("agent", "", "agent id")
	scheduledAt := fs.String("scheduled-at", "", "RFC-3339 scheduled time")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *subjectName == "" {
		return fmt.Errorf("kyc vcip-schedule: --subject-name is required")
	}
	body := map[string]any{"subjectName": *subjectName}
	if *subjectRef != "" {
		body["subjectRef"] = *subjectRef
	}
	if *agentID != "" {
		body["agentId"] = *agentID
	}
	if *scheduledAt != "" {
		body["scheduledAt"] = *scheduledAt
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/merchants/kyb/vcip", body, nil)
}

// kycVcipList implements `kyc vcip-list` → GET /v1/merchants/kyb/vcip.
func kycVcipList(args []string) error {
	fs, cf := newFlagSet("kyc vcip-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/merchants/kyb/vcip", nil, nil)
}

// kycVcipGet implements `kyc vcip-get` → GET /v1/merchants/kyb/vcip/{sessionId}.
func kycVcipGet(args []string) error {
	fs, cf := newFlagSet("kyc vcip-get")
	id := fs.String("id", "", "session id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("kyc vcip-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/merchants/kyb/vcip/"+*id, nil, nil)
}

// kycVcipStart implements `kyc vcip-start` →
// POST /v1/merchants/kyb/vcip/{sessionId}/start.
func kycVcipStart(args []string) error {
	fs, cf := newFlagSet("kyc vcip-start")
	id := fs.String("id", "", "session id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("kyc vcip-start: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/merchants/kyb/vcip/"+*id+"/start", nil, nil)
}

// kycVcipComplete implements `kyc vcip-complete` →
// POST /v1/merchants/kyb/vcip/{sessionId}/complete.
func kycVcipComplete(args []string) error {
	fs, cf := newFlagSet("kyc vcip-complete")
	id := fs.String("id", "", "session id (required)")
	biometricRef := fs.String("biometric-ref", "", "biometric capture reference (required)")
	livenessScore := fs.Int("liveness-score", -1, "liveness score 0-100")
	geoTag := fs.String("geo-tag", "", "geo tag")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *biometricRef == "" {
		return fmt.Errorf("kyc vcip-complete: --id and --biometric-ref are required")
	}
	body := map[string]any{"biometricRef": *biometricRef}
	if *livenessScore >= 0 {
		body["livenessScore"] = *livenessScore
	}
	if *geoTag != "" {
		body["geoTag"] = *geoTag
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/merchants/kyb/vcip/"+*id+"/complete", body, nil)
}

// kycVcipFail implements `kyc vcip-fail` →
// POST /v1/merchants/kyb/vcip/{sessionId}/fail.
func kycVcipFail(args []string) error {
	fs, cf := newFlagSet("kyc vcip-fail")
	id := fs.String("id", "", "session id (required)")
	reason := fs.String("reason", "", "failure reason")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("kyc vcip-fail: --id is required")
	}
	var body map[string]any
	if *reason != "" {
		body = map[string]any{"reason": *reason}
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/merchants/kyb/vcip/"+*id+"/fail", body, nil)
}

// kycUboAdd implements `kyc ubo-add` → POST /v1/merchants/kyb/ubo.
func kycUboAdd(args []string) error {
	fs, cf := newFlagSet("kyc ubo-add")
	name := fs.String("name", "", "owner name (required)")
	pan := fs.String("pan", "", "owner PAN")
	din := fs.String("din", "", "director identification number")
	nationality := fs.String("nationality", "", "nationality")
	ownershipBps := fs.Int("ownership-bps", 0, "ownership in basis points (>1000; required)")
	controlPerson := fs.Bool("control-person", false, "is a person of significant control")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *name == "" || *ownershipBps <= 0 {
		return fmt.Errorf("kyc ubo-add: --name and --ownership-bps (>0) are required")
	}
	body := map[string]any{
		"name":         *name,
		"ownershipBps": *ownershipBps,
	}
	if *pan != "" {
		body["pan"] = *pan
	}
	if *din != "" {
		body["din"] = *din
	}
	if *nationality != "" {
		body["nationality"] = *nationality
	}
	if *controlPerson {
		body["controlPerson"] = true
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/merchants/kyb/ubo", body, nil)
}

// kycUboList implements `kyc ubo-list` → GET /v1/merchants/kyb/ubo.
func kycUboList(args []string) error {
	fs, cf := newFlagSet("kyc ubo-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/merchants/kyb/ubo", nil, nil)
}

// kycUboGet implements `kyc ubo-get` → GET /v1/merchants/kyb/ubo/{id}.
func kycUboGet(args []string) error {
	fs, cf := newFlagSet("kyc ubo-get")
	id := fs.String("id", "", "owner id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("kyc ubo-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/merchants/kyb/ubo/"+*id, nil, nil)
}

// kycUboRemove implements `kyc ubo-remove` → DELETE /v1/merchants/kyb/ubo/{id}.
func kycUboRemove(args []string) error {
	fs, cf := newFlagSet("kyc ubo-remove")
	id := fs.String("id", "", "owner id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("kyc ubo-remove: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodDelete, "/v1/merchants/kyb/ubo/"+*id, nil, nil)
}
