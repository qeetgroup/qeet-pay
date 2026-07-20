package main

import (
	"fmt"
	"net/http"
)

// runFraud dispatches the `fraud` command group (fraud-scoring decisions log).
func runFraud(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("fraud: expected a subcommand (decisions, decision-get)")
	}
	switch args[0] {
	case "decisions":
		return fraudDecisions(args[1:])
	case "decision-get":
		return fraudDecisionGet(args[1:])
	default:
		return fmt.Errorf("fraud: unknown subcommand %q (want: decisions, decision-get)", args[0])
	}
}

// fraudDecisions implements `fraud decisions` → GET /v1/fraud/decisions.
func fraudDecisions(args []string) error {
	fs, cf := newFlagSet("fraud decisions")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/fraud/decisions", nil, nil)
}

// fraudDecisionGet implements `fraud decision-get` → GET /v1/fraud/decisions/{id}.
func fraudDecisionGet(args []string) error {
	fs, cf := newFlagSet("fraud decision-get")
	id := fs.String("id", "", "decision id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("fraud decision-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/fraud/decisions/"+*id, nil, nil)
}
