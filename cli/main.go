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
	case "mandates":
		err = runMandates(rest)
	case "virtual-accounts":
		err = runVirtualAccounts(rest)
	case "reconciliation":
		err = runReconciliation(rest)
	case "revrec":
		err = runRevRec(rest)
	case "marketplace":
		err = runMarketplace(rest)
	case "insurance":
		err = runInsurance(rest)
	case "bnpl":
		err = runBNPL(rest)
	case "tds":
		err = runTDS(rest)
	case "crossborder":
		err = runCrossborder(rest)
	case "treasury":
		err = runTreasury(rest)
	case "payroll":
		err = runPayroll(rest)
	case "offline":
		err = runOffline(rest)
	case "ondc":
		err = runONDC(rest)
	case "messaging":
		err = runMessaging(rest)
	case "kyc":
		err = runKYC(rest)
	case "aml":
		err = runAML(rest)
	case "agentic":
		err = runAgentic(rest)
	case "accounting":
		err = runAccounting(rest)
	case "copilot":
		err = runCopilot(rest)
	case "fraud":
		err = runFraud(rest)
	case "compliance":
		err = runCompliance(rest)
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
  mandates   create | list | get | activate | pause | revoke | debit | debits
  virtual-accounts  mint | list | get | credit | close
  reconciliation    ingest | settlements | settlement-get
  revrec     schedule-create | schedule-list | schedule-get | recognize
  marketplace  split-create | split-list | split-get | split-cancel |
             seller-register | seller-list | seller-suspend | seller-activate
  insurance  policy-issue | policy-list | policy-get | policy-cancel |
             claim-file | claim-approve | claim-reject
  bnpl       create | list | get | pay
  tds        deduction-record | deduction-list | deduction-get | certificate |
             summary | return-prepare | return-list | return-get | return-file | return-export
  crossborder  outbound-create | outbound-list | outbound-get | outbound-quote |
             outbound-mark-remitted | outbound-mark-failed |
             export-create | export-list | export-get | export-remittance
  treasury   rule-create | rule-list | rule-get | rule-pause | rule-resume |
             sweep-run | sweeps | recommendations
  payroll    batch-create | batch-list | batch-get | batch-approve | batch-reject |
             lines | slip
  offline    wallet-create | wallet-list | wallet-get | wallet-topup | wallet-spend |
             pos-capture | pos-list | device-register | device-list |
             qr-generate | qr-list | intent-create | intent-list | intent-confirm
  ondc       order-create | order-list | order-get | order-fulfill | order-settle | order-cancel
  messaging  templates | template-upsert | dispatch | dispatches | dispatch-get |
             dispatch-delivered | dispatch-failed | whatsapp-pay-create |
             whatsapp-pay-list | whatsapp-pay-confirm | whatsapp-inbound | whatsapp-inbound-list
  kyc        customer-create | customer-list | customer-get | customer-pan | customer-consent |
             aadhaar-initiate | aadhaar-verify | vcip-schedule | vcip-list | vcip-get |
             vcip-start | vcip-complete | vcip-fail | ubo-add | ubo-list | ubo-get | ubo-remove
  aml        screen | mule-scan | monitor | alerts | cases | case-create |
             case-close | str-list | str-create
  agentic    mandate-issue | mandate-list | mandate-get | mandate-authorize |
             mandate-revoke | mcp-manifest
  accounting connections | connect | export-create | export-list | export-get | export-download
  copilot    ask | treasury-ask | reconciliation-ask | conversations | conversation
  fraud      decisions | decision-get
  compliance health

Examples:
  qp sandbox seed --slug acme-demo --name "Acme Demo"
  qp payments create --amount 150000 --method UPI --merchant <id>
  qp payments capture --id <payment-id>
  qp links create --title "Invoice #42" --amount 250000
  qp gst return-prepare --type GSTR1 --period 2026-06
  qp analytics arr
  qp mandates create --customer cust-1 --type UPI_AUTOPAY --frequency MONTHLY --start-date 2026-07-01
  qp virtual-accounts mint --customer cust-1
  qp treasury rule-create --name "Idle sweep" --source-account settlement --target-account bank --trigger THRESHOLD --threshold 5000000
  qp copilot ask --question "What is my current settlement balance?"

Run 'qp <command> <subcommand> -h' for a subcommand's flags.
`)
}
