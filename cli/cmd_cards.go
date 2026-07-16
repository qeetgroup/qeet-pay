package main

import (
	"fmt"
	"net/http"
)

// runCards dispatches the `cards` command group (virtual expense/wallet cards).
func runCards(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("cards: expected a subcommand (issue, list, get, load, spend, freeze, unfreeze, close)")
	}
	switch args[0] {
	case "issue":
		return cardsIssue(args[1:])
	case "list":
		return cardsList(args[1:])
	case "get":
		return cardsGet(args[1:])
	case "load":
		return cardsAmount(args[1:], "load")
	case "spend":
		return cardsSpend(args[1:])
	case "freeze":
		return cardsAction(args[1:], "freeze")
	case "unfreeze":
		return cardsAction(args[1:], "unfreeze")
	case "close":
		return cardsAction(args[1:], "close")
	default:
		return fmt.Errorf("cards: unknown subcommand %q", args[0])
	}
}

// cardsIssue implements `cards issue` → POST /v1/cards.
func cardsIssue(args []string) error {
	fs, cf := newFlagSet("cards issue")
	holder := fs.String("holder", "", "card holder reference (required)")
	cardType := fs.String("type", "", "card type: EXPENSE|WALLET (required)")
	currency := fs.String("currency", "INR", "ISO currency code")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *holder == "" || *cardType == "" {
		return fmt.Errorf("cards issue: --holder and --type are required")
	}
	body := map[string]any{
		"holderRef": *holder,
		"type":      *cardType,
		"currency":  *currency,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/cards", body, nil)
}

// cardsList implements `cards list` → GET /v1/cards.
func cardsList(args []string) error {
	fs, cf := newFlagSet("cards list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/cards", nil, nil)
}

// cardsGet implements `cards get` → GET /v1/cards/{cardId}.
func cardsGet(args []string) error {
	fs, cf := newFlagSet("cards get")
	id := fs.String("id", "", "card id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("cards get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/cards/"+*id, nil, nil)
}

// cardsAmount implements `cards load` → POST /v1/cards/{cardId}/load.
func cardsAmount(args []string, action string) error {
	fs, cf := newFlagSet("cards " + action)
	id := fs.String("id", "", "card id (required)")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *amount <= 0 {
		return fmt.Errorf("cards %s: --id and --amount (>0) are required", action)
	}
	body := map[string]any{"amountMinor": *amount}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/cards/"+*id+"/"+action, body, nil)
}

// cardsSpend implements `cards spend` → POST /v1/cards/{cardId}/spend.
func cardsSpend(args []string) error {
	fs, cf := newFlagSet("cards spend")
	id := fs.String("id", "", "card id (required)")
	amount := fs.Int64("amount", 0, "amount in paise / minor units (required)")
	description := fs.String("description", "", "spend description")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" || *amount <= 0 {
		return fmt.Errorf("cards spend: --id and --amount (>0) are required")
	}
	body := map[string]any{"amountMinor": *amount}
	if *description != "" {
		body["description"] = *description
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/cards/"+*id+"/spend", body, nil)
}

// cardsAction implements freeze/unfreeze/close → POST /v1/cards/{cardId}/{action}.
func cardsAction(args []string, action string) error {
	fs, cf := newFlagSet("cards " + action)
	id := fs.String("id", "", "card id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("cards %s: --id is required", action)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/cards/"+*id+"/"+action, nil, nil)
}
