package main

import (
	"fmt"
	"net/http"
)

// runOffline dispatches the `offline` command group (UPI Lite wallets, POS
// devices/transactions, Bharat QR and UPI 123Pay intents).
func runOffline(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("offline: expected a subcommand (wallet-create, wallet-list, wallet-get, wallet-topup, wallet-spend, pos-capture, pos-list, device-register, device-list, qr-generate, qr-list, intent-create, intent-list, intent-confirm)")
	}
	switch args[0] {
	case "wallet-create":
		return offlineWalletCreate(args[1:])
	case "wallet-list":
		return offlineWalletList(args[1:])
	case "wallet-get":
		return offlineWalletGet(args[1:])
	case "wallet-topup":
		return offlineWalletAmount(args[1:], "topup")
	case "wallet-spend":
		return offlineWalletAmount(args[1:], "spend")
	case "pos-capture":
		return offlinePosCapture(args[1:])
	case "pos-list":
		return offlinePosList(args[1:])
	case "device-register":
		return offlineDeviceRegister(args[1:])
	case "device-list":
		return offlineDeviceList(args[1:])
	case "qr-generate":
		return offlineQrGenerate(args[1:])
	case "qr-list":
		return offlineQrList(args[1:])
	case "intent-create":
		return offlineIntentCreate(args[1:])
	case "intent-list":
		return offlineIntentList(args[1:])
	case "intent-confirm":
		return offlineIntentConfirm(args[1:])
	default:
		return fmt.Errorf("offline: unknown subcommand %q", args[0])
	}
}

// offlineWalletCreate implements `offline wallet-create` →
// POST /v1/offline/upi-lite/wallets.
func offlineWalletCreate(args []string) error {
	fs, cf := newFlagSet("offline wallet-create")
	customer := fs.String("customer", "", "customer reference (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *customer == "" {
		return fmt.Errorf("offline wallet-create: --customer is required")
	}
	body := map[string]any{
		"customerRef": *customer,
		"currency":    *currency,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/offline/upi-lite/wallets", body, nil)
}

// offlineWalletList implements `offline wallet-list` →
// GET /v1/offline/upi-lite/wallets.
func offlineWalletList(args []string) error {
	fs, cf := newFlagSet("offline wallet-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/offline/upi-lite/wallets", nil, nil)
}

// offlineWalletGet implements `offline wallet-get` →
// GET /v1/offline/upi-lite/wallets/{walletId}.
func offlineWalletGet(args []string) error {
	fs, cf := newFlagSet("offline wallet-get")
	id := fs.String("id", "", "wallet id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("offline wallet-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/offline/upi-lite/wallets/"+*id, nil, nil)
}

// offlineWalletAmount implements topup/spend →
// POST /v1/offline/upi-lite/wallets/{walletId}/{action}.
func offlineWalletAmount(args []string, action string) error {
	fs, cf := newFlagSet("offline wallet-" + action)
	id := fs.String("id", "", "wallet id (required)")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *amount <= 0 {
		return fmt.Errorf("offline wallet-%s: --id and --amount (>0) are required", action)
	}
	body := map[string]any{"amountMinor": *amount}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/offline/upi-lite/wallets/"+*id+"/"+action, body, nil)
}

// offlinePosCapture implements `offline pos-capture` →
// POST /v1/offline/pos/transactions.
func offlinePosCapture(args []string) error {
	fs, cf := newFlagSet("offline pos-capture")
	device := fs.String("device", "", "POS device id (required)")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	method := fs.String("method", "", "capture method")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *device == "" || *amount <= 0 {
		return fmt.Errorf("offline pos-capture: --device and --amount (>0) are required")
	}
	body := map[string]any{
		"deviceId":    *device,
		"amountMinor": *amount,
		"currency":    *currency,
	}
	if *method != "" {
		body["method"] = *method
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/offline/pos/transactions", body, nil)
}

// offlinePosList implements `offline pos-list` → GET /v1/offline/pos/transactions.
func offlinePosList(args []string) error {
	fs, cf := newFlagSet("offline pos-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/offline/pos/transactions", nil, nil)
}

// offlineDeviceRegister implements `offline device-register` →
// POST /v1/offline/pos/devices.
func offlineDeviceRegister(args []string) error {
	fs, cf := newFlagSet("offline device-register")
	label := fs.String("label", "", "device label (required)")
	serial := fs.String("serial", "", "device serial number (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *label == "" || *serial == "" {
		return fmt.Errorf("offline device-register: --label and --serial are required")
	}
	body := map[string]any{
		"label":    *label,
		"serialNo": *serial,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/offline/pos/devices", body, nil)
}

// offlineDeviceList implements `offline device-list` → GET /v1/offline/pos/devices.
func offlineDeviceList(args []string) error {
	fs, cf := newFlagSet("offline device-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/offline/pos/devices", nil, nil)
}

// offlineQrGenerate implements `offline qr-generate` → POST /v1/offline/bharat-qr.
func offlineQrGenerate(args []string) error {
	fs, cf := newFlagSet("offline qr-generate")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (0 = static/dynamic-open QR)")
	currency := fs.String("currency", "INR", "ISO currency code")
	merchantName := fs.String("merchant-name", "", "merchant display name")
	reference := fs.String("reference", "", "merchant reference")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	body := map[string]any{"currency": *currency}
	if *amount > 0 {
		body["amountMinor"] = *amount
	}
	if *merchantName != "" {
		body["merchantName"] = *merchantName
	}
	if *reference != "" {
		body["reference"] = *reference
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/offline/bharat-qr", body, nil)
}

// offlineQrList implements `offline qr-list` → GET /v1/offline/bharat-qr.
func offlineQrList(args []string) error {
	fs, cf := newFlagSet("offline qr-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/offline/bharat-qr", nil, nil)
}

// offlineIntentCreate implements `offline intent-create` →
// POST /v1/offline/123pay/intents.
func offlineIntentCreate(args []string) error {
	fs, cf := newFlagSet("offline intent-create")
	mobile := fs.String("mobile", "", "payer mobile number (required)")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *mobile == "" || *amount <= 0 {
		return fmt.Errorf("offline intent-create: --mobile and --amount (>0) are required")
	}
	body := map[string]any{
		"payerMobile": *mobile,
		"amountMinor": *amount,
		"currency":    *currency,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/offline/123pay/intents", body, nil)
}

// offlineIntentList implements `offline intent-list` →
// GET /v1/offline/123pay/intents.
func offlineIntentList(args []string) error {
	fs, cf := newFlagSet("offline intent-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/offline/123pay/intents", nil, nil)
}

// offlineIntentConfirm implements `offline intent-confirm` →
// POST /v1/offline/123pay/intents/{intentId}/confirm.
func offlineIntentConfirm(args []string) error {
	fs, cf := newFlagSet("offline intent-confirm")
	id := fs.String("id", "", "intent id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("offline intent-confirm: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/offline/123pay/intents/"+*id+"/confirm", nil, nil)
}
