package main

import (
	"bytes"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
)

// capture swaps a *os.File (os.Stdout / os.Stderr) for a pipe while fn runs and
// returns everything written to it.
func capture(t *testing.T, target **os.File, fn func()) string {
	t.Helper()
	r, w, err := os.Pipe()
	if err != nil {
		t.Fatalf("os.Pipe: %v", err)
	}
	orig := *target
	*target = w
	defer func() { *target = orig }()

	fn()
	w.Close()
	out, _ := io.ReadAll(r)
	return string(out)
}

func TestRequestSendsAuthHeaders(t *testing.T) {
	var gotKey, gotMerchant, gotAccept string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotKey = r.Header.Get(apiKeyHeader)
		gotMerchant = r.Header.Get(merchantHeader)
		gotAccept = r.Header.Get("Accept")
		if r.URL.Path != "/v1/payments" {
			t.Errorf("path = %q, want /v1/payments", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		io.WriteString(w, `{"id":"pay_1","status":"AUTHORIZED"}`)
	}))
	defer srv.Close()

	c := newClient("qp_test_abc", srv.URL, "merch_1", false)
	status, body, err := c.request(http.MethodPost, "/v1/payments", map[string]any{"amountMinor": 100}, nil)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if status != http.StatusCreated {
		t.Errorf("status = %d, want 201", status)
	}
	if gotKey != "qp_test_abc" {
		t.Errorf("X-Api-Key = %q, want qp_test_abc", gotKey)
	}
	if gotMerchant != "merch_1" {
		t.Errorf("X-Merchant-Id = %q, want merch_1", gotMerchant)
	}
	if gotAccept != "application/json" {
		t.Errorf("Accept = %q, want application/json", gotAccept)
	}
	if !strings.Contains(string(body), "pay_1") {
		t.Errorf("body = %q, want it to contain pay_1", body)
	}
}

func TestDoErrorDecodesProblem(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/problem+json")
		w.WriteHeader(http.StatusBadRequest)
		io.WriteString(w, `{"title":"Validation failed","status":400,"detail":"amountMinor must be positive","errors":{"amountMinor":"must be greater than 0"}}`)
	}))
	defer srv.Close()

	c := newClient("k", srv.URL, "", false)
	var err error
	stderr := capture(t, &os.Stderr, func() {
		err = c.do(http.MethodPost, "/v1/payments", map[string]any{"amountMinor": -1}, nil)
	})
	if !errors.Is(err, errRequestFailed) {
		t.Fatalf("err = %v, want errRequestFailed", err)
	}
	for _, want := range []string{"HTTP 400", "Validation failed", "amountMinor must be positive", "amountMinor: must be greater than 0"} {
		if !strings.Contains(stderr, want) {
			t.Errorf("stderr missing %q; got:\n%s", want, stderr)
		}
	}
}

func TestDoSuccessPrintsTable(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		io.WriteString(w, `[{"id":"acct_1","code":"settlement","type":"LIABILITY"},{"id":"acct_2","code":"bank","type":"ASSET"}]`)
	}))
	defer srv.Close()

	c := newClient("k", srv.URL, "", false)
	var err error
	stdout := capture(t, &os.Stdout, func() {
		err = c.do(http.MethodGet, "/v1/ledger/accounts", nil, nil)
	})
	if err != nil {
		t.Fatalf("do: %v", err)
	}
	for _, want := range []string{"ID", "CODE", "TYPE", "settlement", "bank"} {
		if !strings.Contains(stdout, want) {
			t.Errorf("stdout missing %q; got:\n%s", want, stdout)
		}
	}
}

func TestDoSuccessJSONMode(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, `{"id":"pay_1","amountMinor":150000}`)
	}))
	defer srv.Close()

	c := newClient("k", srv.URL, "", true) // jsonOut = true
	stdout := capture(t, &os.Stdout, func() {
		if err := c.do(http.MethodGet, "/v1/payments/pay_1", nil, nil); err != nil {
			t.Fatalf("do: %v", err)
		}
	})
	if !strings.Contains(stdout, `"amountMinor": 150000`) {
		t.Errorf("stdout should be indented JSON; got:\n%s", stdout)
	}
}

func TestPrintProblemNonJSON(t *testing.T) {
	var buf bytes.Buffer
	printProblem(&buf, http.MethodGet, "/v1/x", 500, []byte("upstream exploded"))
	got := buf.String()
	if !strings.Contains(got, "HTTP 500") || !strings.Contains(got, "upstream exploded") {
		t.Errorf("unexpected output: %s", got)
	}
}

func TestObjectKeysPreservesOrder(t *testing.T) {
	raw := []byte(`{"id":"1","amountMinor":100,"currency":"INR","status":"CAPTURED","nested":{"a":1}}`)
	got := objectKeys(raw)
	want := []string{"id", "amountMinor", "currency", "status", "nested"}
	if len(got) != len(want) {
		t.Fatalf("keys = %v, want %v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Errorf("key[%d] = %q, want %q", i, got[i], want[i])
		}
	}
}

func TestPrintTableNestedObject(t *testing.T) {
	// A card view with an embedded object + array (like the real /v1/cards/{id}).
	raw := []byte(`{"card":{"id":"card_1","maskedPan":"****1234","balanceMinor":5000},"transactions":[{"type":"LOAD","amountMinor":5000}]}`)
	var buf bytes.Buffer
	printTable(&buf, raw)
	got := buf.String()
	for _, want := range []string{"card:", "MASKEDPAN", "****1234", "transactions (1):", "TYPE", "LOAD"} {
		if !strings.Contains(got, want) {
			t.Errorf("table missing %q; got:\n%s", want, got)
		}
	}
}

func TestPrintTableEmptyArray(t *testing.T) {
	var buf bytes.Buffer
	printTable(&buf, []byte(`[]`))
	if !strings.Contains(buf.String(), "(no rows)") {
		t.Errorf("empty array should print (no rows); got: %s", buf.String())
	}
}

func TestJSONField(t *testing.T) {
	body := []byte(`{"id":"m_1","apiKey":"qp_test_xyz","card":{"id":"c_9"},"amountMinor":150000}`)
	cases := map[string]string{
		"id":          "m_1",
		"apiKey":      "qp_test_xyz",
		"card.id":     "c_9",
		"amountMinor": "150000", // stays an integer, not 1.5e+05
		"missing":     "",
		"card.nope":   "",
	}
	for path, want := range cases {
		if got := jsonField(body, path); got != want {
			t.Errorf("jsonField(%q) = %q, want %q", path, got, want)
		}
	}
}

func TestClientRequiresCredentials(t *testing.T) {
	t.Setenv("QEETPAY_API_KEY", "")
	t.Setenv("QEETPAY_MERCHANT_ID", "")
	t.Setenv("QEETPAY_BASE_URL", "")

	// Flag pointers hold their defaults ("" / false) immediately after newFlagSet.
	_, cf := newFlagSet("test")
	if _, err := cf.client(); err == nil {
		t.Error("expected an error when neither api key nor merchant is set")
	}

	// With a merchant id (dev/sandbox), a client should build.
	*cf.merchant = "merch_1"
	c, err := cf.client()
	if err != nil {
		t.Fatalf("client with merchant: %v", err)
	}
	if c.baseURL != defaultBaseURL {
		t.Errorf("baseURL = %q, want default %q", c.baseURL, defaultBaseURL)
	}
}
