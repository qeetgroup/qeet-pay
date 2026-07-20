package main

import (
	"encoding/json"
	"fmt"
	"net/http"
)

// runMessaging dispatches the `messaging` command group (templates + rendered
// dispatch to qeet-notify, and WhatsApp-native pay collections/inbound).
func runMessaging(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("messaging: expected a subcommand (templates, template-upsert, dispatch, dispatches, dispatch-get, dispatch-delivered, dispatch-failed, whatsapp-pay-create, whatsapp-pay-list, whatsapp-pay-confirm, whatsapp-inbound, whatsapp-inbound-list)")
	}
	switch args[0] {
	case "templates":
		return messagingTemplates(args[1:])
	case "template-upsert":
		return messagingTemplateUpsert(args[1:])
	case "dispatch":
		return messagingDispatch(args[1:])
	case "dispatches":
		return messagingDispatches(args[1:])
	case "dispatch-get":
		return messagingDispatchGet(args[1:])
	case "dispatch-delivered":
		return messagingDispatchDelivered(args[1:])
	case "dispatch-failed":
		return messagingDispatchFailed(args[1:])
	case "whatsapp-pay-create":
		return messagingWhatsappPayCreate(args[1:])
	case "whatsapp-pay-list":
		return messagingWhatsappPayList(args[1:])
	case "whatsapp-pay-confirm":
		return messagingWhatsappPayConfirm(args[1:])
	case "whatsapp-inbound":
		return messagingWhatsappInbound(args[1:])
	case "whatsapp-inbound-list":
		return messagingWhatsappInboundList(args[1:])
	default:
		return fmt.Errorf("messaging: unknown subcommand %q", args[0])
	}
}

// messagingTemplates implements `messaging templates` → GET /v1/messaging/templates.
func messagingTemplates(args []string) error {
	fs, cf := newFlagSet("messaging templates")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/messaging/templates", nil, nil)
}

// messagingTemplateUpsert implements `messaging template-upsert` →
// PUT /v1/messaging/templates.
func messagingTemplateUpsert(args []string) error {
	fs, cf := newFlagSet("messaging template-upsert")
	key := fs.String("key", "", "template key (required)")
	channel := fs.String("channel", "", "channel: WHATSAPP|SMS|EMAIL (required)")
	body := fs.String("body", "", "template body, with {{variable}} placeholders (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *key == "" || *channel == "" || *body == "" {
		return fmt.Errorf("messaging template-upsert: --key, --channel and --body are required")
	}
	payload := map[string]any{
		"templateKey": *key,
		"channel":     *channel,
		"body":        *body,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPut, "/v1/messaging/templates", payload, nil)
}

// messagingDispatch implements `messaging dispatch` → POST /v1/messaging/dispatch.
func messagingDispatch(args []string) error {
	fs, cf := newFlagSet("messaging dispatch")
	key := fs.String("key", "", "template key (required)")
	channel := fs.String("channel", "", "channel: WHATSAPP|SMS|EMAIL (required)")
	recipient := fs.String("recipient", "", "recipient address/number (required)")
	variables := fs.String("variables", "", `template variables JSON object, e.g. '{"name":"Asha","amount":"₹1,500"}'`)
	relatedRef := fs.String("related-ref", "", "related domain object reference")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *key == "" || *channel == "" || *recipient == "" {
		return fmt.Errorf("messaging dispatch: --key, --channel and --recipient are required")
	}
	payload := map[string]any{
		"templateKey": *key,
		"channel":     *channel,
		"recipient":   *recipient,
	}
	if *variables != "" {
		var parsed map[string]string
		if err := json.Unmarshal([]byte(*variables), &parsed); err != nil {
			return fmt.Errorf("invalid --variables JSON: %w", err)
		}
		payload["variables"] = parsed
	}
	if *relatedRef != "" {
		payload["relatedRef"] = *relatedRef
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/messaging/dispatch", payload, nil)
}

// messagingDispatches implements `messaging dispatches` →
// GET /v1/messaging/dispatches.
func messagingDispatches(args []string) error {
	fs, cf := newFlagSet("messaging dispatches")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/messaging/dispatches", nil, nil)
}

// messagingDispatchGet implements `messaging dispatch-get` →
// GET /v1/messaging/dispatches/{dispatchId}.
func messagingDispatchGet(args []string) error {
	fs, cf := newFlagSet("messaging dispatch-get")
	id := fs.String("id", "", "dispatch id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("messaging dispatch-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/messaging/dispatches/"+*id, nil, nil)
}

// messagingDispatchDelivered implements `messaging dispatch-delivered` →
// POST /v1/messaging/dispatches/{dispatchId}/delivered.
func messagingDispatchDelivered(args []string) error {
	fs, cf := newFlagSet("messaging dispatch-delivered")
	id := fs.String("id", "", "dispatch id (required)")
	providerRef := fs.String("provider-ref", "", "provider delivery reference (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *providerRef == "" {
		return fmt.Errorf("messaging dispatch-delivered: --id and --provider-ref are required")
	}
	payload := map[string]any{"providerRef": *providerRef}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/messaging/dispatches/"+*id+"/delivered", payload, nil)
}

// messagingDispatchFailed implements `messaging dispatch-failed` →
// POST /v1/messaging/dispatches/{dispatchId}/failed.
func messagingDispatchFailed(args []string) error {
	fs, cf := newFlagSet("messaging dispatch-failed")
	id := fs.String("id", "", "dispatch id (required)")
	reason := fs.String("reason", "", "failure reason (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *reason == "" {
		return fmt.Errorf("messaging dispatch-failed: --id and --reason are required")
	}
	payload := map[string]any{"reason": *reason}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/messaging/dispatches/"+*id+"/failed", payload, nil)
}

// messagingWhatsappPayCreate implements `messaging whatsapp-pay-create` →
// POST /v1/messaging/whatsapp/pay.
func messagingWhatsappPayCreate(args []string) error {
	fs, cf := newFlagSet("messaging whatsapp-pay-create")
	method := fs.String("method", "", "payment method: UPI|CARD|NET_BANKING|WALLET (required)")
	amount := fs.Int64("amount", 0, "amount in paise / minor units")
	simulateFailure := fs.Bool("simulate-failure", false, "ask the sandbox provider to fail")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *method == "" {
		return fmt.Errorf("messaging whatsapp-pay-create: --method is required")
	}
	payload := map[string]any{"method": *method}
	if *amount > 0 {
		payload["amountMinor"] = *amount
	}
	if *simulateFailure {
		payload["simulateFailure"] = true
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/messaging/whatsapp/pay", payload, nil)
}

// messagingWhatsappPayList implements `messaging whatsapp-pay-list` →
// GET /v1/messaging/whatsapp/pay.
func messagingWhatsappPayList(args []string) error {
	fs, cf := newFlagSet("messaging whatsapp-pay-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/messaging/whatsapp/pay", nil, nil)
}

// messagingWhatsappPayConfirm implements `messaging whatsapp-pay-confirm` →
// POST /v1/messaging/whatsapp/pay/{id}/confirm.
func messagingWhatsappPayConfirm(args []string) error {
	fs, cf := newFlagSet("messaging whatsapp-pay-confirm")
	id := fs.String("id", "", "pay collection id (required)")
	providerRef := fs.String("provider-ref", "", "provider reference")
	success := fs.Bool("success", true, "whether the collection succeeded")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("messaging whatsapp-pay-confirm: --id is required")
	}
	payload := map[string]any{"success": *success}
	if *providerRef != "" {
		payload["providerRef"] = *providerRef
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/messaging/whatsapp/pay/"+*id+"/confirm", payload, nil)
}

// messagingWhatsappInbound implements `messaging whatsapp-inbound` →
// POST /v1/messaging/whatsapp/inbound (simulate an inbound WhatsApp message).
func messagingWhatsappInbound(args []string) error {
	fs, cf := newFlagSet("messaging whatsapp-inbound")
	from := fs.String("from", "", "sender number (required)")
	messageID := fs.String("message-id", "", "provider message id (required)")
	text := fs.String("text", "", "message text")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *from == "" || *messageID == "" {
		return fmt.Errorf("messaging whatsapp-inbound: --from and --message-id are required")
	}
	payload := map[string]any{
		"from":      *from,
		"messageId": *messageID,
	}
	if *text != "" {
		payload["text"] = *text
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/messaging/whatsapp/inbound", payload, nil)
}

// messagingWhatsappInboundList implements `messaging whatsapp-inbound-list` →
// GET /v1/messaging/whatsapp/inbound.
func messagingWhatsappInboundList(args []string) error {
	fs, cf := newFlagSet("messaging whatsapp-inbound-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/messaging/whatsapp/inbound", nil, nil)
}
