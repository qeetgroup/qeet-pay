package main

import (
	"encoding/json"
	"fmt"
	"net/http"
)

// runMarketplace dispatches the `marketplace` command group (split settlements
// with commission/GST/TCS/TDS attribution + seller registry).
func runMarketplace(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("marketplace: expected a subcommand (split-create, split-list, split-get, split-cancel, seller-register, seller-list, seller-suspend, seller-activate)")
	}
	switch args[0] {
	case "split-create":
		return marketplaceSplitCreate(args[1:])
	case "split-list":
		return marketplaceSplitList(args[1:])
	case "split-get":
		return marketplaceSplitGet(args[1:])
	case "split-cancel":
		return marketplaceSplitCancel(args[1:])
	case "seller-register":
		return marketplaceSellerRegister(args[1:])
	case "seller-list":
		return marketplaceSellerList(args[1:])
	case "seller-suspend":
		return marketplaceSellerAction(args[1:], "suspend")
	case "seller-activate":
		return marketplaceSellerAction(args[1:], "activate")
	default:
		return fmt.Errorf("marketplace: unknown subcommand %q", args[0])
	}
}

// marketplaceSplitCreate implements `marketplace split-create` →
// POST /v1/marketplace/splits.
func marketplaceSplitCreate(args []string) error {
	fs, cf := newFlagSet("marketplace split-create")
	paymentID := fs.String("payment", "", "captured payment id backing the split")
	sourceRef := fs.String("source-ref", "", "source reference")
	currency := fs.String("currency", "INR", "ISO currency code")
	lines := fs.String("lines", "", `split lines JSON array, e.g. `+
		`'[{"sellerRef":"seller-1","grossMinor":100000,"commissionBps":500,"commissionGstRate":18,"tcsBps":100,"tdsBps":100}]' (required)`)
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *lines == "" {
		return fmt.Errorf("marketplace split-create: --lines is required")
	}
	var parsedLines []map[string]any
	if err := json.Unmarshal([]byte(*lines), &parsedLines); err != nil {
		return fmt.Errorf("invalid --lines JSON: %w", err)
	}
	body := map[string]any{
		"currency": *currency,
		"lines":    parsedLines,
	}
	if *paymentID != "" {
		body["paymentId"] = *paymentID
	}
	if *sourceRef != "" {
		body["sourceRef"] = *sourceRef
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/marketplace/splits", body, nil)
}

// marketplaceSplitList implements `marketplace split-list` →
// GET /v1/marketplace/splits.
func marketplaceSplitList(args []string) error {
	fs, cf := newFlagSet("marketplace split-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/marketplace/splits", nil, nil)
}

// marketplaceSplitGet implements `marketplace split-get` →
// GET /v1/marketplace/splits/{splitId}.
func marketplaceSplitGet(args []string) error {
	fs, cf := newFlagSet("marketplace split-get")
	id := fs.String("id", "", "split id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("marketplace split-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/marketplace/splits/"+*id, nil, nil)
}

// marketplaceSplitCancel implements `marketplace split-cancel` →
// POST /v1/marketplace/splits/{splitId}/cancel.
func marketplaceSplitCancel(args []string) error {
	fs, cf := newFlagSet("marketplace split-cancel")
	id := fs.String("id", "", "split id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("marketplace split-cancel: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/marketplace/splits/"+*id+"/cancel", nil, nil)
}

// marketplaceSellerRegister implements `marketplace seller-register` →
// POST /v1/marketplace/sellers.
func marketplaceSellerRegister(args []string) error {
	fs, cf := newFlagSet("marketplace seller-register")
	sellerRef := fs.String("seller-ref", "", "seller reference (required)")
	name := fs.String("name", "", "seller legal name (required)")
	gstin := fs.String("gstin", "", "seller GSTIN")
	pan := fs.String("pan", "", "seller PAN")
	commissionBps := fs.Int("commission-bps", 0, "default commission in basis points")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *sellerRef == "" || *name == "" {
		return fmt.Errorf("marketplace seller-register: --seller-ref and --name are required")
	}
	body := map[string]any{
		"sellerRef": *sellerRef,
		"name":      *name,
	}
	if *gstin != "" {
		body["gstin"] = *gstin
	}
	if *pan != "" {
		body["pan"] = *pan
	}
	if *commissionBps > 0 {
		body["commissionBps"] = *commissionBps
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/marketplace/sellers", body, nil)
}

// marketplaceSellerList implements `marketplace seller-list` →
// GET /v1/marketplace/sellers.
func marketplaceSellerList(args []string) error {
	fs, cf := newFlagSet("marketplace seller-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/marketplace/sellers", nil, nil)
}

// marketplaceSellerAction implements suspend/activate →
// POST /v1/marketplace/sellers/{sellerId}/{action}.
func marketplaceSellerAction(args []string, action string) error {
	fs, cf := newFlagSet("marketplace seller-" + action)
	id := fs.String("id", "", "seller id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("marketplace seller-%s: --id is required", action)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/marketplace/sellers/"+*id+"/"+action, nil, nil)
}
