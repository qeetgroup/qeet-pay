package main

import (
	"fmt"
	"net/http"
)

// runLending dispatches the `lending` command group (embedded working-capital advances).
func runLending(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("lending: expected a subcommand (offer-request, offer-list, offer-accept, loan-list, loan-get, repay)")
	}
	switch args[0] {
	case "offer-request":
		return lendingOfferRequest(args[1:])
	case "offer-list":
		return lendingOfferList(args[1:])
	case "offer-accept":
		return lendingOfferAccept(args[1:])
	case "loan-list":
		return lendingLoanList(args[1:])
	case "loan-get":
		return lendingLoanGet(args[1:])
	case "repay":
		return lendingRepay(args[1:])
	default:
		return fmt.Errorf("lending: unknown subcommand %q", args[0])
	}
}

// lendingOfferRequest implements `lending offer-request` → POST /v1/lending/offers.
func lendingOfferRequest(args []string) error {
	fs, cf := newFlagSet("lending offer-request")
	currency := fs.String("currency", "INR", "ISO currency code")
	avgMonthlyVolume := fs.Int64("avg-monthly-volume", 0, "average monthly settlement volume in paise (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *avgMonthlyVolume < 0 {
		return fmt.Errorf("lending offer-request: --avg-monthly-volume must be >= 0")
	}
	body := map[string]any{
		"currency":              *currency,
		"avgMonthlyVolumeMinor": *avgMonthlyVolume,
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/lending/offers", body, nil)
}

// lendingOfferList implements `lending offer-list` → GET /v1/lending/offers.
func lendingOfferList(args []string) error {
	fs, cf := newFlagSet("lending offer-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/lending/offers", nil, nil)
}

// lendingOfferAccept implements `lending offer-accept` → POST /v1/lending/offers/{offerId}/accept.
func lendingOfferAccept(args []string) error {
	fs, cf := newFlagSet("lending offer-accept")
	offer := fs.String("offer", "", "offer id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *offer == "" {
		return fmt.Errorf("lending offer-accept: --offer is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/lending/offers/"+*offer+"/accept", nil, nil)
}

// lendingLoanList implements `lending loan-list` → GET /v1/lending/loans.
func lendingLoanList(args []string) error {
	fs, cf := newFlagSet("lending loan-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/lending/loans", nil, nil)
}

// lendingLoanGet implements `lending loan-get` → GET /v1/lending/loans/{loanId}.
func lendingLoanGet(args []string) error {
	fs, cf := newFlagSet("lending loan-get")
	loan := fs.String("loan", "", "loan id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *loan == "" {
		return fmt.Errorf("lending loan-get: --loan is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/lending/loans/"+*loan, nil, nil)
}

// lendingRepay implements `lending repay` → POST /v1/lending/loans/{loanId}/repayments.
func lendingRepay(args []string) error {
	fs, cf := newFlagSet("lending repay")
	loan := fs.String("loan", "", "loan id (required)")
	settlementAmount := fs.Int64("settlement-amount", 0, "settlement amount to sweep from, in paise (required)")
	sourceRef := fs.String("source-ref", "", "settlement/source reference")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *loan == "" || *settlementAmount <= 0 {
		return fmt.Errorf("lending repay: --loan and --settlement-amount (>0) are required")
	}
	body := map[string]any{"settlementAmountMinor": *settlementAmount}
	if *sourceRef != "" {
		body["sourceRef"] = *sourceRef
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/lending/loans/"+*loan+"/repayments", body, nil)
}
