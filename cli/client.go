package main

import (
	"bytes"
	"crypto/rand"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"sort"
	"strings"
	"text/tabwriter"
	"time"
)

// defaultBaseURL is used when neither --base-url nor QEETPAY_BASE_URL is set.
const defaultBaseURL = "http://localhost:4201"

// apiKeyHeader is the API-key authentication header Qeet Pay expects
// (X-Api-Key: qp_live_… / qp_test_…).
const apiKeyHeader = "X-Api-Key"

// merchantHeader selects the active merchant in the permissive dev/test profiles,
// which boot without a live Qeet ID (TAD §6.1).
const merchantHeader = "X-Merchant-Id"

// errRequestFailed is returned by Client.do for a non-2xx response. The RFC-7807
// problem body has already been printed to stderr, so main only needs to exit
// non-zero without printing anything further.
var errRequestFailed = errors.New("request failed")

// Client is a tiny JSON HTTP client for the Qeet Pay REST API.
type Client struct {
	baseURL  string
	apiKey   string
	merchant string
	jsonOut  bool
	http     *http.Client
}

// newClient builds a Client with a sane request timeout.
func newClient(apiKey, baseURL, merchant string, jsonOut bool) *Client {
	return &Client{
		baseURL:  strings.TrimRight(baseURL, "/"),
		apiKey:   apiKey,
		merchant: merchant,
		jsonOut:  jsonOut,
		http:     &http.Client{Timeout: 30 * time.Second},
	}
}

// request sends an HTTP request with the auth headers, marshalling body to JSON
// when non-nil, and returns the response status and raw body. Network/transport
// failures return an error; a non-2xx status does not (the caller decides).
func (c *Client) request(method, path string, body any, headers map[string]string) (int, []byte, error) {
	var reader io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return 0, nil, fmt.Errorf("encode request body: %w", err)
		}
		reader = bytes.NewReader(b)
	}

	req, err := http.NewRequest(method, c.baseURL+path, reader)
	if err != nil {
		return 0, nil, fmt.Errorf("build request: %w", err)
	}
	if c.apiKey != "" {
		req.Header.Set(apiKeyHeader, c.apiKey)
	}
	if c.merchant != "" {
		req.Header.Set(merchantHeader, c.merchant)
	}
	req.Header.Set("Accept", "application/json")
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}

	resp, err := c.http.Do(req)
	if err != nil {
		return 0, nil, fmt.Errorf("send request to %s: %w", c.baseURL+path, err)
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return resp.StatusCode, nil, fmt.Errorf("read response: %w", err)
	}
	return resp.StatusCode, data, nil
}

// do performs a request and renders the result: a friendly table by default, or
// pretty JSON with --json. A non-2xx response prints the decoded RFC-7807 problem
// to stderr and returns errRequestFailed.
func (c *Client) do(method, path string, body any, headers map[string]string) error {
	status, data, err := c.request(method, path, body, headers)
	if err != nil {
		return err
	}

	if status < 200 || status >= 300 {
		printProblem(os.Stderr, method, path, status, data)
		return errRequestFailed
	}

	if c.jsonOut {
		printJSONTo(os.Stdout, data)
		return nil
	}
	printTable(os.Stdout, data)
	return nil
}

// printProblem decodes an RFC-7807 application/problem+json body and prints a
// concise, human-readable error. Non-JSON bodies are printed verbatim.
func printProblem(w io.Writer, method, path string, status int, data []byte) {
	fmt.Fprintf(w, "error: %s %s -> HTTP %d\n", method, path, status)

	trimmed := bytes.TrimSpace(data)
	if len(trimmed) == 0 {
		return
	}

	var problem struct {
		Type     string            `json:"type"`
		Title    string            `json:"title"`
		Status   int               `json:"status"`
		Detail   string            `json:"detail"`
		Instance string            `json:"instance"`
		Errors   map[string]string `json:"errors"`
	}
	if err := json.Unmarshal(trimmed, &problem); err != nil {
		// Not JSON — echo the raw body.
		w.Write(trimmed) //nolint:errcheck
		fmt.Fprintln(w)
		return
	}

	if problem.Title != "" {
		fmt.Fprintf(w, "  %s\n", problem.Title)
	}
	if problem.Detail != "" {
		fmt.Fprintf(w, "  %s\n", problem.Detail)
	}
	if len(problem.Errors) > 0 {
		fields := make([]string, 0, len(problem.Errors))
		for f := range problem.Errors {
			fields = append(fields, f)
		}
		sort.Strings(fields)
		for _, f := range fields {
			fmt.Fprintf(w, "  - %s: %s\n", f, problem.Errors[f])
		}
	}
}

// printJSONTo writes data to w, indented if it is valid JSON, raw otherwise.
// An empty body (common on 201/204 responses) prints a small confirmation.
func printJSONTo(w io.Writer, data []byte) {
	trimmed := bytes.TrimSpace(data)
	if len(trimmed) == 0 {
		fmt.Fprintln(w, "(empty response)")
		return
	}
	var buf bytes.Buffer
	if err := json.Indent(&buf, trimmed, "", "  "); err != nil {
		w.Write(trimmed) //nolint:errcheck
		fmt.Fprintln(w)
		return
	}
	buf.WriteTo(w) //nolint:errcheck
	fmt.Fprintln(w)
}

// commonFlags holds the auth/target/output flags shared by every subcommand.
type commonFlags struct {
	apiKey   *string
	baseURL  *string
	merchant *string
	jsonOut  *bool
}

// newFlagSet returns a ContinueOnError FlagSet pre-wired with the common
// --api-key / --base-url / --merchant / --json flags.
func newFlagSet(name string) (*flag.FlagSet, *commonFlags) {
	fs := flag.NewFlagSet(name, flag.ContinueOnError)
	fs.SetOutput(os.Stderr)
	cf := &commonFlags{
		apiKey:   fs.String("api-key", "", "API key (defaults to $QEETPAY_API_KEY)"),
		baseURL:  fs.String("base-url", "", "API base URL (defaults to $QEETPAY_BASE_URL or "+defaultBaseURL+")"),
		merchant: fs.String("merchant", "", "merchant id for the dev/test X-Merchant-Id header (defaults to $QEETPAY_MERCHANT_ID)"),
		jsonOut:  fs.Bool("json", false, "print raw JSON instead of a table"),
	}
	return fs, cf
}

// resolvedBaseURL returns the base URL from flag, then environment, then default.
func (cf *commonFlags) resolvedBaseURL() string {
	baseURL := *cf.baseURL
	if baseURL == "" {
		baseURL = os.Getenv("QEETPAY_BASE_URL")
	}
	if baseURL == "" {
		baseURL = defaultBaseURL
	}
	return baseURL
}

// client resolves the API key, base URL and merchant from flags then environment,
// and returns a ready Client. It errors if neither an API key nor a merchant id
// is available (one is required to identify the tenant).
func (cf *commonFlags) client() (*Client, error) {
	apiKey := *cf.apiKey
	if apiKey == "" {
		apiKey = os.Getenv("QEETPAY_API_KEY")
	}

	merchant := *cf.merchant
	if merchant == "" {
		merchant = os.Getenv("QEETPAY_MERCHANT_ID")
	}

	if apiKey == "" && merchant == "" {
		return nil, errors.New("no credentials: set $QEETPAY_API_KEY (or --api-key), or pass --merchant for dev/sandbox")
	}

	return newClient(apiKey, cf.resolvedBaseURL(), merchant, *cf.jsonOut), nil
}

// flagErr normalises flag parsing errors: -h/--help is not a real error (flag
// already printed usage), everything else is returned as-is.
func flagErr(err error) error {
	if errors.Is(err, flag.ErrHelp) {
		return nil
	}
	return err
}

// withQuery appends an encoded query string to path when values are present.
func withQuery(path string, q url.Values) string {
	if len(q) == 0 {
		return path
	}
	return path + "?" + q.Encode()
}

// newIdempotencyKey returns a random RFC 4122 v4 UUID for the Idempotency-Key
// header, so retried captures/refunds/approvals de-duplicate server-side.
func newIdempotencyKey() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return fmt.Sprintf("idem-%d", time.Now().UnixNano())
	}
	b[6] = (b[6] & 0x0f) | 0x40 // version 4
	b[8] = (b[8] & 0x3f) | 0x80 // variant 10
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

// idempotent returns a headers map carrying a fresh Idempotency-Key.
func idempotent() map[string]string {
	return map[string]string{"Idempotency-Key": newIdempotencyKey()}
}

// ── Table rendering ────────────────────────────────────────────────────────────
//
// Responses are either a JSON array of objects (list endpoints) or a single JSON
// object (which may embed nested objects/arrays, e.g. a card with its
// transactions). printTable renders the former as a columnar table and the latter
// as a key/value table, recursing into nested collections as labelled sub-tables.

// printTable renders data as a friendly table on w.
func printTable(w io.Writer, data []byte) {
	trimmed := bytes.TrimSpace(data)
	if len(trimmed) == 0 {
		fmt.Fprintln(w, "(empty response)")
		return
	}
	renderValue(w, "", trimmed)
}

// renderValue dispatches on the JSON kind of raw (object / array / scalar).
func renderValue(w io.Writer, label string, raw []byte) {
	switch jsonKind(raw) {
	case kindArray:
		renderArray(w, label, raw)
	case kindObject:
		renderObject(w, label, raw)
	default:
		if label != "" {
			fmt.Fprintf(w, "%s: %s\n", label, scalarString(raw))
		} else {
			fmt.Fprintln(w, scalarString(raw))
		}
	}
}

// renderArray prints an array. Arrays of objects become a columnar table; arrays
// of scalars become a simple list.
func renderArray(w io.Writer, label string, raw []byte) {
	var elems []json.RawMessage
	if err := json.Unmarshal(raw, &elems); err != nil {
		printJSONTo(w, raw)
		return
	}
	if label != "" {
		fmt.Fprintf(w, "%s (%d):\n", label, len(elems))
	}
	if len(elems) == 0 {
		fmt.Fprintln(w, "(no rows)")
		return
	}

	// Arrays of scalars: one value per line.
	if jsonKind(elems[0]) != kindObject {
		for _, e := range elems {
			fmt.Fprintf(w, "  %s\n", scalarString(e))
		}
		return
	}

	// Arrays of objects: build an ordered union of column names.
	var columns []string
	seen := map[string]bool{}
	for _, e := range elems {
		for _, k := range objectKeys(e) {
			if !seen[k] {
				seen[k] = true
				columns = append(columns, k)
			}
		}
	}

	tw := tabwriter.NewWriter(w, 0, 2, 2, ' ', 0)
	fmt.Fprintln(tw, strings.Join(upperAll(columns), "\t"))
	for _, e := range elems {
		fields := objectFields(e)
		cells := make([]string, len(columns))
		for i, c := range columns {
			cells[i] = cellString(fields[c])
		}
		fmt.Fprintln(tw, strings.Join(cells, "\t"))
	}
	tw.Flush() //nolint:errcheck
}

// renderObject prints an object as a FIELD/VALUE table, then recurses into any
// nested object or array fields as labelled sub-tables.
func renderObject(w io.Writer, label string, raw []byte) {
	if label != "" {
		fmt.Fprintf(w, "%s:\n", label)
	}

	keys := objectKeys(raw)
	fields := objectFields(raw)

	var nested []string
	tw := tabwriter.NewWriter(w, 0, 2, 2, ' ', 0)
	for _, k := range keys {
		v := fields[k]
		switch jsonKind(v) {
		case kindObject:
			nested = append(nested, k)
		case kindArray:
			nested = append(nested, k)
		default:
			fmt.Fprintf(tw, "%s\t%s\n", upper(k), scalarString(v))
		}
	}
	tw.Flush() //nolint:errcheck

	for _, k := range nested {
		fmt.Fprintln(w)
		renderValue(w, k, fields[k])
	}
}

// ── JSON helpers ───────────────────────────────────────────────────────────────

type jsonKindT int

const (
	kindScalar jsonKindT = iota
	kindObject
	kindArray
)

// jsonKind reports whether raw is an object, array, or scalar by its first
// non-whitespace byte.
func jsonKind(raw []byte) jsonKindT {
	for _, b := range raw {
		switch b {
		case ' ', '\t', '\n', '\r':
			continue
		case '{':
			return kindObject
		case '[':
			return kindArray
		default:
			return kindScalar
		}
	}
	return kindScalar
}

// objectKeys returns the field names of a JSON object in document order.
func objectKeys(raw []byte) []string {
	dec := json.NewDecoder(bytes.NewReader(raw))
	t, err := dec.Token()
	if err != nil {
		return nil
	}
	if d, ok := t.(json.Delim); !ok || d != '{' {
		return nil
	}
	var keys []string
	for dec.More() {
		kt, err := dec.Token()
		if err != nil {
			return keys
		}
		key, _ := kt.(string)
		keys = append(keys, key)
		if err := skipValue(dec); err != nil {
			return keys
		}
	}
	return keys
}

// objectFields returns the raw value of each field of a JSON object.
func objectFields(raw []byte) map[string]json.RawMessage {
	m := map[string]json.RawMessage{}
	_ = json.Unmarshal(raw, &m)
	return m
}

// skipValue consumes exactly one JSON value from dec (used to walk object keys).
func skipValue(dec *json.Decoder) error {
	t, err := dec.Token()
	if err != nil {
		return err
	}
	d, ok := t.(json.Delim)
	if !ok {
		return nil // a scalar token
	}
	if d != '{' && d != '[' {
		return nil
	}
	depth := 1
	for depth > 0 {
		tt, err := dec.Token()
		if err != nil {
			return err
		}
		if dd, ok := tt.(json.Delim); ok {
			switch dd {
			case '{', '[':
				depth++
			case '}', ']':
				depth--
			}
		}
	}
	return nil
}

// scalarString formats a scalar JSON value (string/number/bool/null) for display.
func scalarString(raw []byte) string {
	dec := json.NewDecoder(bytes.NewReader(bytes.TrimSpace(raw)))
	dec.UseNumber()
	var v any
	if err := dec.Decode(&v); err != nil {
		return string(bytes.TrimSpace(raw))
	}
	switch t := v.(type) {
	case nil:
		return "-"
	case string:
		if t == "" {
			return "-"
		}
		return t
	case bool:
		if t {
			return "true"
		}
		return "false"
	case json.Number:
		return t.String()
	default:
		return string(bytes.TrimSpace(raw))
	}
}

// cellString formats any field value for a table cell; nested objects/arrays are
// shown compactly so a row stays on one line.
func cellString(raw json.RawMessage) string {
	if len(raw) == 0 {
		return "-"
	}
	switch jsonKind(raw) {
	case kindObject:
		return compactJSON(raw)
	case kindArray:
		var elems []json.RawMessage
		if err := json.Unmarshal(raw, &elems); err == nil {
			return fmt.Sprintf("[%d]", len(elems))
		}
		return compactJSON(raw)
	default:
		return scalarString(raw)
	}
}

// compactJSON returns raw with insignificant whitespace removed.
func compactJSON(raw []byte) string {
	var buf bytes.Buffer
	if err := json.Compact(&buf, raw); err != nil {
		return string(bytes.TrimSpace(raw))
	}
	return buf.String()
}

// upper upper-cases a camelCase field name into a table header (id -> ID).
func upper(s string) string {
	return strings.ToUpper(s)
}

func upperAll(ss []string) []string {
	out := make([]string, len(ss))
	for i, s := range ss {
		out[i] = upper(s)
	}
	return out
}
