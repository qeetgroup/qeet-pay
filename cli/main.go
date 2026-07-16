// Command qp is a dependency-free (standard-library-only) command-line interface
// for the Qeet Pay REST API. Every endpoint lives under /v1; money is expressed
// in integer minor units (paise) as amountMinor; Jackson emits camelCase JSON.
//
// Authentication and target selection come from flags or the environment:
//
//	QEETPAY_API_KEY     / --api-key     API key sent as the X-Api-Key header (qp_live_… / qp_test_…)
//	QEETPAY_BASE_URL    / --base-url    API base URL (default http://localhost:4201)
//	QEETPAY_MERCHANT_ID / --merchant    dev/sandbox X-Merchant-Id header (permissive dev/test profile)
//	                      --json        print raw JSON instead of a friendly table
//
// Global flags are accepted by every subcommand and must follow it, e.g.
// `qp payments list --json`. Run `qp --help` for the full command list.
package main

import (
	"errors"
	"fmt"
	"os"
)

func main() {
	args := os.Args[1:]
	if len(args) == 0 {
		usage()
		os.Exit(2)
	}

	switch args[0] {
	case "-h", "--help", "help":
		usage()
		return
	}

	group, rest := args[0], args[1:]

	var err error
	switch group {
	case "payments":
		err = runPayments(rest)
	case "links":
		err = runLinks(rest)
	case "payouts":
		err = runPayouts(rest)
	case "billing":
		err = runBilling(rest)
	case "gst":
		err = runGST(rest)
	case "ledger":
		err = runLedger(rest)
	case "analytics":
		err = runAnalytics(rest)
	case "lending":
		err = runLending(rest)
	case "cards":
		err = runCards(rest)
	case "escrow":
		err = runEscrow(rest)
	case "kyb":
		err = runKYB(rest)
	case "webhooks":
		err = runWebhooks(rest)
	case "sandbox":
		err = runSandbox(rest)
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q\n\n", group)
		usage()
		os.Exit(2)
	}

	if err != nil {
		// errRequestFailed already printed the RFC-7807 problem to stderr.
		if !errors.Is(err, errRequestFailed) {
			fmt.Fprintln(os.Stderr, "error:", err)
		}
		os.Exit(1)
	}
}

// usage prints the top-level help listing every command.
func usage() {
	fmt.Fprint(os.Stderr, `qp — CLI for the Qeet Pay API

Usage:
  qp <command> <subcommand> [flags]

Global flags (accepted by every subcommand; place them after the subcommand):
  --api-key   string   API key            (default $QEETPAY_API_KEY)
  --base-url  string   API base URL       (default $QEETPAY_BASE_URL or http://localhost:4201)
  --merchant  string   dev X-Merchant-Id  (default $QEETPAY_MERCHANT_ID)
  --json               print raw JSON instead of a friendly table

Money is always in integer minor units (paise): --amount 150000 = ₹1,500.00

Commands:
  payments   create | get | capture | refund | refunds
  links      create | list | get | pay | cancel
  payouts    create | get | approve | reject
  billing    plan-create | subscription-create | subscription-get |
             subscription-pause | subscription-resume | subscription-cancel |
             invoice-get | invoice-pay
  gst        invoice-create | invoice-get | invoice-pay |
             irn-generate | irn-get | irn-cancel |
             return-prepare | return-list | return-get | return-file
  ledger     accounts | balance | post
  analytics  tpv | mrr | arr | success-rate | cash-flow
  lending    offer-request | offer-list | offer-accept |
             loan-list | loan-get | repay
  cards      issue | list | get | load | spend | freeze | unfreeze | close
  escrow     hold | list | get | release | refund
  kyb        pan | gstin | bank | status
  webhooks   register | list | disable | deliveries
  sandbox    seed

Examples:
  qp sandbox seed --slug acme-demo --name "Acme Demo"
  qp payments create --amount 150000 --method UPI --merchant <id>
  qp payments capture --id <payment-id>
  qp links create --title "Invoice #42" --amount 250000
  qp gst return-prepare --type GSTR1 --period 2026-06
  qp analytics arr

Run 'qp <command> <subcommand> -h' for a subcommand's flags.
`)
}
