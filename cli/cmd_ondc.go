package main

import (
	"encoding/json"
	"fmt"
	"net/http"
)

// runONDC dispatches the `ondc` command group (ONDC network orders with
// commission/GST/TCS split, hold→fulfill→settle lifecycle).
func runONDC(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("ondc: expected a subcommand (order-create, order-list, order-get, order-fulfill, order-settle, order-cancel)")
	}
	switch args[0] {
	case "order-create":
		return ondcOrderCreate(args[1:])
	case "order-list":
		return ondcOrderList(args[1:])
	case "order-get":
		return ondcOrderGet(args[1:])
	case "order-fulfill":
		return ondcOrderAction(args[1:], "fulfill")
	case "order-settle":
		return ondcOrderAction(args[1:], "settle")
	case "order-cancel":
		return ondcOrderAction(args[1:], "cancel")
	default:
		return fmt.Errorf("ondc: unknown subcommand %q", args[0])
	}
}

// ondcOrderCreate implements `ondc order-create` → POST /v1/ondc/orders.
func ondcOrderCreate(args []string) error {
	fs, cf := newFlagSet("ondc order-create")
	networkOrderID := fs.String("network-order-id", "", "ONDC network order id (required)")
	buyerApp := fs.String("buyer-app", "", "buyer app id (required)")
	sellerApp := fs.String("seller-app", "", "seller app id (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	lines := fs.String("lines", "", `order lines JSON array, e.g. `+
		`'[{"partyRef":"seller-1","role":"SELLER","grossMinor":100000,"commissionBps":500,"commissionGstRate":18,"tcsBps":100}]' (required)`)
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *networkOrderID == "" || *buyerApp == "" || *sellerApp == "" || *lines == "" {
		return fmt.Errorf("ondc order-create: --network-order-id, --buyer-app, --seller-app and --lines are required")
	}
	var parsedLines []map[string]any
	if err := json.Unmarshal([]byte(*lines), &parsedLines); err != nil {
		return fmt.Errorf("invalid --lines JSON: %w", err)
	}
	body := map[string]any{
		"networkOrderId": *networkOrderID,
		"buyerApp":       *buyerApp,
		"sellerApp":      *sellerApp,
		"currency":       *currency,
		"lines":          parsedLines,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/ondc/orders", body, nil)
}

// ondcOrderList implements `ondc order-list` → GET /v1/ondc/orders.
func ondcOrderList(args []string) error {
	fs, cf := newFlagSet("ondc order-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/ondc/orders", nil, nil)
}

// ondcOrderGet implements `ondc order-get` → GET /v1/ondc/orders/{orderId}.
func ondcOrderGet(args []string) error {
	fs, cf := newFlagSet("ondc order-get")
	id := fs.String("id", "", "order id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("ondc order-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/ondc/orders/"+*id, nil, nil)
}

// ondcOrderAction implements fulfill/settle/cancel →
// POST /v1/ondc/orders/{orderId}/{action}.
func ondcOrderAction(args []string, action string) error {
	fs, cf := newFlagSet("ondc order-" + action)
	id := fs.String("id", "", "order id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("ondc order-%s: --id is required", action)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/ondc/orders/"+*id+"/"+action, nil, nil)
}
