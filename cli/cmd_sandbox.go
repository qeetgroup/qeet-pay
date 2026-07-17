package main

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"strings"
	"text/tabwriter"
)

// runSandbox dispatches the `sandbox` command group.
func runSandbox(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("sandbox: expected a subcommand (seed)")
	}
	switch args[0] {
	case "seed":
		return sandboxSeed(args[1:])
	default:
		return fmt.Errorf("sandbox: unknown subcommand %q (want: seed)", args[0])
	}
}

// seedItem is one resource the seeder attempted to create.
type seedItem struct {
	Domain   string `json:"domain"`
	Resource string `json:"resource"`
	ID       string `json:"id"`
	Detail   string `json:"detail"`
	Status   string `json:"status"` // created | skipped | error
}

// seedRun accumulates the outcome of each seeding call against one merchant.
type seedRun struct {
	client *Client
	items  []seedItem
}

// call performs one seeding request and records its outcome. It returns the raw
// response body and true on a 2xx; on any failure it records a skipped/error item
// and returns (nil, false) so the seeder can continue best-effort.
func (s *seedRun) call(domain, resource, method, path string, body any, headers map[string]string) ([]byte, bool) {
	status, data, err := s.client.request(method, path, body, headers)
	if err != nil {
		s.items = append(s.items, seedItem{domain, resource, "", err.Error(), "error"})
		return nil, false
	}
	if status < 200 || status >= 300 {
		s.items = append(s.items, seedItem{domain, resource, "", fmt.Sprintf("HTTP %d %s", status, problemSummary(data)), "skipped"})
		return nil, false
	}
	return data, true
}

// created records a successfully created resource, extracting its id from idPath.
func (s *seedRun) created(domain, resource string, body []byte, idPath, detail string) string {
	id := jsonField(body, idPath)
	s.items = append(s.items, seedItem{domain, resource, id, detail, "created"})
	return id
}

// sandboxSeed provisions a demo merchant then seeds representative data across
// domains, so a developer gets a populated sandbox in a single command.
//
// Merchant onboarding (POST /v1/merchants) is unauthenticated and mints a
// qp_test_… API key; every subsequent call authenticates with that key. The
// backend's sandbox adapters run by default when no live keys are set, so this
// exercises the real sandbox end-to-end.
func sandboxSeed(args []string) error {
	fs, cf := newFlagSet("sandbox seed")
	slug := fs.String("slug", "", "merchant slug (default: demo-<random>)")
	name := fs.String("name", "Demo Merchant", "merchant display name")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}

	if *slug == "" {
		*slug = "demo-" + shortID()
	}

	baseURL := cf.resolvedBaseURL()

	// 1. Onboard the merchant (unauthenticated; open endpoint).
	boot := newClient("", baseURL, "", false)
	status, body, err := boot.request(http.MethodPost, "/v1/merchants", map[string]any{
		"slug": *slug,
		"name": *name,
	}, nil)
	if err != nil {
		return fmt.Errorf("create merchant: %w", err)
	}
	if status < 200 || status >= 300 {
		printProblem(os.Stderr, http.MethodPost, "/v1/merchants", status, body)
		return errRequestFailed
	}

	merchantID := jsonField(body, "id")
	apiKey := jsonField(body, "apiKey")
	if apiKey == "" {
		return fmt.Errorf("merchant created but no apiKey was returned; cannot seed")
	}

	run := &seedRun{client: newClient(apiKey, baseURL, "", false)}
	run.items = append(run.items, seedItem{"merchants", "merchant", merchantID, *slug, "created"})

	// 2. KYB submissions.
	if b, ok := run.call("kyb", "pan", http.MethodPost, "/v1/merchants/kyb/pan", map[string]any{"pan": "ABCDE1234F"}, nil); ok {
		run.created("kyb", "pan", b, "overallStatus", "PAN ABCDE1234F")
	}
	if b, ok := run.call("kyb", "gstin", http.MethodPost, "/v1/merchants/kyb/gstin", map[string]any{"gstin": "27ABCDE1234F1Z5"}, nil); ok {
		run.created("kyb", "gstin", b, "overallStatus", "GSTIN 27ABCDE1234F1Z5")
	}

	// 3. Payments — create + capture (a captured payment credits the settlement account).
	var capturedTotal string
	if b, ok := run.call("payments", "payment #1", http.MethodPost, "/v1/payments", map[string]any{
		"amountMinor": 150000, "currency": "INR", "method": "UPI", "description": "Sandbox demo order #1",
	}, nil); ok {
		pid := run.created("payments", "payment #1", b, "id", "₹1,500.00 UPI")
		if pid != "" {
			run.call("payments", "capture #1", http.MethodPost, "/v1/payments/"+pid+"/capture", nil, idempotent())
			capturedTotal = pid
		}
	}
	if b, ok := run.call("payments", "payment #2", http.MethodPost, "/v1/payments", map[string]any{
		"amountMinor": 250000, "currency": "INR", "method": "CARD", "description": "Sandbox demo order #2",
	}, nil); ok {
		pid := run.created("payments", "payment #2", b, "id", "₹2,500.00 CARD")
		if pid != "" {
			run.call("payments", "capture #2", http.MethodPost, "/v1/payments/"+pid+"/capture", nil, idempotent())
			if rb, ok := run.call("payments", "refund #2", http.MethodPost, "/v1/payments/"+pid+"/refund", map[string]any{
				"amountMinor": 50000, "reason": "sandbox demo partial refund",
			}, idempotent()); ok {
				run.created("payments", "refund #2", rb, "id", "₹500.00 partial refund")
			}
		}
	}
	_ = capturedTotal

	// 4. Payment link (fixed amount).
	if b, ok := run.call("links", "payment link", http.MethodPost, "/v1/payment-links", map[string]any{
		"title": "Sandbox Invoice #42", "amountMinor": 99900, "currency": "INR", "reference": "demo-42",
	}, nil); ok {
		run.created("links", "payment link", b, "id", jsonField(b, "code"))
	}

	// 5. GST invoice — create then pay.
	if b, ok := run.call("gst", "gst invoice", http.MethodPost, "/v1/gst/invoices", map[string]any{
		"supplierGstin": "27ABCDE1234F1Z5",
		"buyerGstin":    "29PQRSX6789K2Z1",
		"placeOfSupply": "29",
		"currency":      "INR",
		"lines": []map[string]any{{
			"description":    "Consulting services",
			"hsnSac":         "9983",
			"quantity":       1,
			"unitPriceMinor": 500000,
			"gstRate":        18,
		}},
	}, nil); ok {
		iid := run.created("gst", "gst invoice", b, "id", jsonField(b, "invoiceNumber"))
		if iid != "" {
			run.call("gst", "gst invoice pay", http.MethodPost, "/v1/gst/invoices/"+iid+"/pay", nil, idempotent())
		}
	}

	// 6. Billing — plan then subscription.
	if b, ok := run.call("billing", "plan", http.MethodPost, "/v1/plans", map[string]any{
		"code": "demo-pro-" + shortID(), "name": "Demo Pro", "amountMinor": 99900, "currency": "INR", "interval": "MONTH",
	}, nil); ok {
		planID := run.created("billing", "plan", b, "id", "Demo Pro ₹999/mo")
		if planID != "" {
			if sb, ok := run.call("billing", "subscription", http.MethodPost, "/v1/subscriptions", map[string]any{
				"planId": planID, "customerRef": "cust-" + shortID(),
			}, nil); ok {
				run.created("billing", "subscription", sb, "id", "on plan "+planID)
			}
		}
	}

	// 7. Lending — request an offer, then accept it (disburses).
	if b, ok := run.call("lending", "loan offer", http.MethodPost, "/v1/lending/offers", map[string]any{
		"currency": "INR", "avgMonthlyVolumeMinor": 5000000,
	}, nil); ok {
		offerID := run.created("lending", "loan offer", b, "id", "principal "+jsonField(b, "principalMinor")+" paise")
		if offerID != "" {
			if lb, ok := run.call("lending", "loan", http.MethodPost, "/v1/lending/offers/"+offerID+"/accept", nil, nil); ok {
				run.created("lending", "loan", lb, "loan.id", "accepted offer "+offerID)
			}
		}
	}

	// 8. Virtual card — issue then load.
	if b, ok := run.call("cards", "virtual card", http.MethodPost, "/v1/cards", map[string]any{
		"holderRef": "emp-" + shortID(), "type": "EXPENSE", "currency": "INR",
	}, nil); ok {
		cardID := run.created("cards", "virtual card", b, "card.id", jsonField(b, "card.maskedPan"))
		if cardID != "" {
			run.call("cards", "card load", http.MethodPost, "/v1/cards/"+cardID+"/load", map[string]any{"amountMinor": 200000}, nil)
		}
	}

	// 9. Escrow — hold buyer funds.
	if b, ok := run.call("escrow", "escrow hold", http.MethodPost, "/v1/escrow", map[string]any{
		"buyerRef": "buyer-" + shortID(), "sellerRef": "seller-" + shortID(),
		"amountMinor": 300000, "currency": "INR", "description": "Sandbox marketplace order",
	}, nil); ok {
		run.created("escrow", "escrow hold", b, "escrow.id", "₹3,000.00 held")
	}

	// 10. Webhook endpoint.
	if b, ok := run.call("webhooks", "webhook endpoint", http.MethodPost, "/v1/webhooks/endpoints", map[string]any{
		"url": "https://example.com/qeet-pay/webhook", "events": "payment.captured,payout.processed",
		"signingSecret": "whsec_" + shortID() + shortID(),
	}, nil); ok {
		run.created("webhooks", "webhook endpoint", b, "id", jsonField(b, "url"))
	}

	printSeedSummary(os.Stdout, *cf.jsonOut, *slug, merchantID, apiKey, baseURL, run.items)
	return nil
}

// printSeedSummary prints the seeded resources as a table (default) or JSON.
func printSeedSummary(w *os.File, jsonOut bool, slug, merchantID, apiKey, baseURL string, items []seedItem) {
	if jsonOut {
		out := map[string]any{
			"merchant": map[string]any{"id": merchantID, "slug": slug, "apiKey": apiKey, "baseUrl": baseURL},
			"seeded":   items,
		}
		b, _ := json.MarshalIndent(out, "", "  ")
		fmt.Fprintln(w, string(b))
		return
	}

	created, skipped := 0, 0
	for _, it := range items {
		if it.Status == "created" {
			created++
		} else {
			skipped++
		}
	}

	fmt.Fprintln(w, "Sandbox seeded.")
	fmt.Fprintln(w)
	fmt.Fprintf(w, "  Merchant : %s (%s)\n", slug, merchantID)
	fmt.Fprintf(w, "  Base URL : %s\n", baseURL)
	fmt.Fprintf(w, "  API key  : %s\n", apiKey)
	fmt.Fprintln(w, "  (test key — shown once; export it as $QEETPAY_API_KEY to keep using this sandbox)")
	fmt.Fprintln(w)

	tw := tabwriter.NewWriter(w, 0, 2, 2, ' ', 0)
	fmt.Fprintln(tw, "DOMAIN\tRESOURCE\tSTATUS\tID / DETAIL")
	for _, it := range items {
		detail := it.ID
		if detail == "" {
			detail = it.Detail
		} else if it.Detail != "" {
			detail = it.ID + "  " + it.Detail
		}
		fmt.Fprintf(tw, "%s\t%s\t%s\t%s\n", it.Domain, it.Resource, it.Status, detail)
	}
	tw.Flush() //nolint:errcheck

	fmt.Fprintln(w)
	fmt.Fprintf(w, "%d created, %d skipped. Try: qp payments create --amount 150000 --method UPI --api-key %s\n", created, skipped, apiKey)
}

// ── Small helpers ──────────────────────────────────────────────────────────────

// jsonField extracts a (possibly dotted) field path from a JSON object body,
// returning "" if absent. Numbers are rendered without scientific notation.
func jsonField(body []byte, path string) string {
	dec := json.NewDecoder(bytes.NewReader(body))
	dec.UseNumber()
	var v any
	if err := dec.Decode(&v); err != nil {
		return ""
	}
	for _, key := range strings.Split(path, ".") {
		m, ok := v.(map[string]any)
		if !ok {
			return ""
		}
		v = m[key]
	}
	switch t := v.(type) {
	case nil:
		return ""
	case string:
		return t
	case json.Number:
		return t.String()
	case bool:
		if t {
			return "true"
		}
		return "false"
	default:
		return fmt.Sprint(t)
	}
}

// problemSummary pulls a short human-readable message from an RFC-7807 body.
func problemSummary(body []byte) string {
	var p struct {
		Title  string `json:"title"`
		Detail string `json:"detail"`
	}
	if err := json.Unmarshal(bytes.TrimSpace(body), &p); err != nil {
		return strings.TrimSpace(string(body))
	}
	switch {
	case p.Detail != "":
		return p.Detail
	case p.Title != "":
		return p.Title
	default:
		return ""
	}
}

// shortID returns 4 random bytes as hex, for unique-ish demo slugs and refs.
func shortID() string {
	var b [4]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "00000000"
	}
	return hex.EncodeToString(b[:])
}
