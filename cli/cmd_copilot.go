package main

import (
	"fmt"
	"net/http"
)

// runCopilot dispatches the `copilot` command group (natural-language finance
// assistant over treasury / reconciliation / general surfaces).
func runCopilot(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("copilot: expected a subcommand (ask, treasury-ask, reconciliation-ask, conversations, conversation)")
	}
	switch args[0] {
	case "ask":
		return copilotAsk(args[1:], "/v1/copilot/query", "ask")
	case "treasury-ask":
		return copilotAsk(args[1:], "/v1/copilot/treasury/ask", "treasury-ask")
	case "reconciliation-ask":
		return copilotAsk(args[1:], "/v1/copilot/reconciliation/ask", "reconciliation-ask")
	case "conversations":
		return copilotConversations(args[1:])
	case "conversation":
		return copilotConversation(args[1:])
	default:
		return fmt.Errorf("copilot: unknown subcommand %q", args[0])
	}
}

// copilotAsk implements the three ask surfaces (query/treasury/reconciliation),
// all POSTing an AskRequest and returning a CopilotAnswer.
func copilotAsk(args []string, path, sub string) error {
	fs, cf := newFlagSet("copilot " + sub)
	question := fs.String("question", "", "natural-language question (required)")
	conversation := fs.String("conversation", "", "existing conversation id to continue")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *question == "" {
		return fmt.Errorf("copilot %s: --question is required", sub)
	}
	body := map[string]any{"question": *question}
	if *conversation != "" {
		body["conversationId"] = *conversation
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, path, body, nil)
}

// copilotConversations implements `copilot conversations` →
// GET /v1/copilot/conversations.
func copilotConversations(args []string) error {
	fs, cf := newFlagSet("copilot conversations")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/copilot/conversations", nil, nil)
}

// copilotConversation implements `copilot conversation` →
// GET /v1/copilot/conversations/{conversationId}.
func copilotConversation(args []string) error {
	fs, cf := newFlagSet("copilot conversation")
	id := fs.String("id", "", "conversation id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("copilot conversation: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/copilot/conversations/"+*id, nil, nil)
}
