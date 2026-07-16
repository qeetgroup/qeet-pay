package main

import (
	"fmt"
	"net/http"
)

// runWebhooks dispatches the `webhooks` command group.
func runWebhooks(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("webhooks: expected a subcommand (register, list, disable, deliveries)")
	}
	switch args[0] {
	case "register":
		return webhooksRegister(args[1:])
	case "list":
		return webhooksList(args[1:])
	case "disable":
		return webhooksDisable(args[1:])
	case "deliveries":
		return webhooksDeliveries(args[1:])
	default:
		return fmt.Errorf("webhooks: unknown subcommand %q (want: register, list, disable, deliveries)", args[0])
	}
}

// webhooksRegister implements `webhooks register` → POST /v1/webhooks/endpoints.
func webhooksRegister(args []string) error {
	fs, cf := newFlagSet("webhooks register")
	url := fs.String("url", "", "endpoint URL (required)")
	events := fs.String("events", "", "comma-separated event types to subscribe to (empty = all)")
	secret := fs.String("secret", "", "HMAC signing secret (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *url == "" || *secret == "" {
		return fmt.Errorf("webhooks register: --url and --secret are required")
	}
	body := map[string]any{
		"url":           *url,
		"signingSecret": *secret,
	}
	if *events != "" {
		body["events"] = *events
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/webhooks/endpoints", body, nil)
}

// webhooksList implements `webhooks list` → GET /v1/webhooks/endpoints.
func webhooksList(args []string) error {
	fs, cf := newFlagSet("webhooks list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/webhooks/endpoints", nil, nil)
}

// webhooksDisable implements `webhooks disable` → DELETE /v1/webhooks/endpoints/{id}.
func webhooksDisable(args []string) error {
	fs, cf := newFlagSet("webhooks disable")
	id := fs.String("id", "", "endpoint id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("webhooks disable: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodDelete, "/v1/webhooks/endpoints/"+*id, nil, nil)
}

// webhooksDeliveries implements `webhooks deliveries` → GET /v1/webhooks/endpoints/{id}/deliveries.
func webhooksDeliveries(args []string) error {
	fs, cf := newFlagSet("webhooks deliveries")
	id := fs.String("id", "", "endpoint id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("webhooks deliveries: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/webhooks/endpoints/"+*id+"/deliveries", nil, nil)
}
