package main

import (
	"fmt"
	"net/http"
	"net/url"
	"strconv"
)

// runTreasury dispatches the `treasury` command group (auto-sweep rules,
// sweep runs and idle-cash recommendations).
func runTreasury(args []string) error {
	if len(args) == 0 {
		return fmt.Errorf("treasury: expected a subcommand (rule-create, rule-list, rule-get, rule-pause, rule-resume, sweep-run, sweeps, recommendations)")
	}
	switch args[0] {
	case "rule-create":
		return treasuryRuleCreate(args[1:])
	case "rule-list":
		return treasuryRuleList(args[1:])
	case "rule-get":
		return treasuryRuleGet(args[1:])
	case "rule-pause":
		return treasuryRuleAction(args[1:], "pause")
	case "rule-resume":
		return treasuryRuleAction(args[1:], "resume")
	case "sweep-run":
		return treasurySweepRun(args[1:])
	case "sweeps":
		return treasurySweeps(args[1:])
	case "recommendations":
		return treasuryRecommendations(args[1:])
	default:
		return fmt.Errorf("treasury: unknown subcommand %q", args[0])
	}
}

// treasuryRuleCreate implements `treasury rule-create` → POST /v1/treasury/rules.
func treasuryRuleCreate(args []string) error {
	fs, cf := newFlagSet("treasury rule-create")
	name := fs.String("name", "", "rule name (required)")
	source := fs.String("source-account", "", "source account code (required)")
	target := fs.String("target-account", "", "target account code (required)")
	trigger := fs.String("trigger", "", "trigger: THRESHOLD|SCHEDULE (required)")
	threshold := fs.Int64("threshold", 0, "threshold balance in paise (THRESHOLD trigger)")
	schedule := fs.String("schedule", "", "cron schedule (SCHEDULE trigger)")
	keep := fs.Int64("keep", 0, "minimum balance to keep in the source account, in paise")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *name == "" || *source == "" || *target == "" || *trigger == "" {
		return fmt.Errorf("treasury rule-create: --name, --source-account, --target-account and --trigger are required")
	}
	body := map[string]any{
		"name":              *name,
		"sourceAccountCode": *source,
		"targetAccountCode": *target,
		"trigger":           *trigger,
	}
	if *threshold > 0 {
		body["thresholdMinor"] = *threshold
	}
	if *schedule != "" {
		body["schedule"] = *schedule
	}
	if *keep > 0 {
		body["keepMinor"] = *keep
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/treasury/rules", body, nil)
}

// treasuryRuleList implements `treasury rule-list` → GET /v1/treasury/rules.
func treasuryRuleList(args []string) error {
	fs, cf := newFlagSet("treasury rule-list")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/treasury/rules", nil, nil)
}

// treasuryRuleGet implements `treasury rule-get` → GET /v1/treasury/rules/{ruleId}.
func treasuryRuleGet(args []string) error {
	fs, cf := newFlagSet("treasury rule-get")
	id := fs.String("id", "", "rule id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("treasury rule-get: --id is required")
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/treasury/rules/"+*id, nil, nil)
}

// treasuryRuleAction implements pause/resume →
// POST /v1/treasury/rules/{ruleId}/{action}.
func treasuryRuleAction(args []string, action string) error {
	fs, cf := newFlagSet("treasury rule-" + action)
	id := fs.String("id", "", "rule id (required)")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	if *id == "" {
		return fmt.Errorf("treasury rule-%s: --id is required", action)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/treasury/rules/"+*id+"/"+action, nil, nil)
}

// treasurySweepRun implements `treasury sweep-run` → POST /v1/treasury/sweeps/run.
func treasurySweepRun(args []string) error {
	fs, cf := newFlagSet("treasury sweep-run")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodPost, "/v1/treasury/sweeps/run", nil, nil)
}

// treasurySweeps implements `treasury sweeps` → GET /v1/treasury/sweeps.
func treasurySweeps(args []string) error {
	fs, cf := newFlagSet("treasury sweeps")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, "/v1/treasury/sweeps", nil, nil)
}

// treasuryRecommendations implements `treasury recommendations` →
// GET /v1/treasury/recommendations.
func treasuryRecommendations(args []string) error {
	fs, cf := newFlagSet("treasury recommendations")
	horizonDays := fs.Int("horizon-days", 30, "projection horizon in days")
	windowDays := fs.Int("window-days", 30, "trailing window used for the trend")
	if err := fs.Parse(args); err != nil {
		return flagErr(err)
	}
	q := url.Values{}
	q.Set("horizonDays", strconv.Itoa(*horizonDays))
	q.Set("windowDays", strconv.Itoa(*windowDays))
	client, err := cf.client()
	if err != nil {
		return err
	}
	return client.do(http.MethodGet, withQuery("/v1/treasury/recommendations", q), nil, nil)
}
