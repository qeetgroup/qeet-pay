import {
  Badge,
  Button,
  Card,
  DataState,
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Textarea,
  TimeSince,
  cn,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
  FileTextIcon,
  FolderOpenIcon,
  ScanSearchIcon,
  ShieldAlertIcon,
  SirenIcon,
  UserSearchIcon,
} from "lucide-react";
import { useState } from "react";

import { ListToolbar } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import { KpiRow, KpiTile } from "@/components/stat-tile";
import { ShortId } from "@/features/finance/shared";
import { ApiError, api } from "@/lib/api";
import { useListView } from "@/lib/list-view";
import { rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/aml")({ component: AmlPage });

const SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;
const ALERT_STATUSES = ["OPEN", "DISMISSED", "ESCALATED"] as const;
const PARTY_TYPES = ["INDIVIDUAL", "BUSINESS", "BENEFICIARY"] as const;

type Alert = {
  id: string;
  subjectRef: string;
  transactionRef: string | null;
  ruleCode: string;
  category: string;
  riskScore: number;
  severity: (typeof SEVERITIES)[number];
  status: (typeof ALERT_STATUSES)[number];
  caseId: string | null;
  createdAt: string;
};

type Screening = { id: string; result: "CLEAR" | "HIT"; matchCount: number; riskScore: number };
type MonitorResult = { alertCount: number; alerts: Alert[] };
type AmlCase = {
  id: string;
  subject: string;
  description: string | null;
  status: string;
  disposition: string | null;
  riskScore: number;
  alertCount: number;
  openedAt: string;
  closedAt: string | null;
};
type StrReport = {
  id: string;
  caseId: string | null;
  subject: string;
  grounds: string;
  status: "DRAFT" | "FILED";
  fiuReferenceId: string | null;
  createdAt: string;
  filedAt: string | null;
};

function severityVariant(s: string): "success" | "destructive" | "warning" | "muted" {
  switch (s) {
    case "CRITICAL":
    case "HIGH":
      return "destructive";
    case "MEDIUM":
      return "warning";
    default:
      return "muted";
  }
}

function errMsg(e: unknown): string {
  return e instanceof ApiError ? e.message : e instanceof Error ? e.message : "Something went wrong.";
}

function AmlPage() {
  const [screenOpen, setScreenOpen] = useState(false);
  const [monitorOpen, setMonitorOpen] = useState(false);
  const [strOpen, setStrOpen] = useState(false);

  const alertsQ = useQuery({
    queryKey: ["aml-alerts"],
    queryFn: () => api<Alert[]>("/v1/aml/alerts"),
    staleTime: 15_000,
  });
  const casesQ = useQuery({
    queryKey: ["aml-cases"],
    queryFn: () => api<AmlCase[]>("/v1/aml/cases"),
    staleTime: 15_000,
  });
  const strQ = useQuery({
    queryKey: ["aml-str-reports"],
    queryFn: () => api<StrReport[]>("/v1/aml/str-reports"),
    staleTime: 15_000,
  });

  const alerts = alertsQ.data ?? [];
  const openCount = alerts.filter((a) => a.status === "OPEN").length;
  const criticalCount = alerts.filter((a) => a.severity === "CRITICAL" || a.severity === "HIGH").length;
  const openCases = (casesQ.data ?? []).filter((c) => c.status === "OPEN").length;
  const filedCount = (strQ.data ?? []).filter((r) => r.status === "FILED").length;

  const lv = useListView(alerts, {
    searchFields: (a) => [a.subjectRef, a.ruleCode, a.category, a.transactionRef ?? ""],
    filterFields: { severity: (a) => a.severity, status: (a) => a.status },
    sortFields: { score: (a) => a.riskScore, created: (a) => a.createdAt },
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Screen parties against sanctions/PEP lists, monitor transactions for suspicious patterns, and file STRs with FIU-IND."
        actions={
          <>
            <Button variant="outline" onClick={() => setScreenOpen(true)}>
              <UserSearchIcon /> Screen party
            </Button>
            <Button variant="outline" onClick={() => setMonitorOpen(true)}>
              <ScanSearchIcon /> Monitor
            </Button>
            <Button onClick={() => setStrOpen(true)}>
              <FileTextIcon /> File STR
            </Button>
          </>
        }
      />

      <KpiRow>
        <KpiTile label="Open alerts" value={openCount.toLocaleString("en-IN")} icon={SirenIcon} tone={openCount > 0 ? "warning" : "neutral"} loading={alertsQ.isLoading} />
        <KpiTile label="High / critical" value={criticalCount.toLocaleString("en-IN")} icon={ShieldAlertIcon} tone={criticalCount > 0 ? "danger" : "neutral"} loading={alertsQ.isLoading} />
        <KpiTile label="Open cases" value={openCases.toLocaleString("en-IN")} icon={FolderOpenIcon} tone={openCases > 0 ? "info" : "neutral"} loading={casesQ.isLoading} />
        <KpiTile label="STRs filed" value={filedCount.toLocaleString("en-IN")} icon={FileTextIcon} tone="success" loading={strQ.isLoading} />
      </KpiRow>

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search subject, rule, category…"
          filters={[
            {
              id: "severity",
              label: "Severity",
              value: lv.filters.severity ?? "",
              options: SEVERITIES.map((s) => ({ label: s, value: s })),
              onChange: (v) => lv.setFilter("severity", v),
            },
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: ALERT_STATUSES.map((s) => ({ label: s, value: s })),
              onChange: (v) => lv.setFilter("status", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
        />

        <DataState
          isLoading={alertsQ.isLoading}
          isError={alertsQ.isError}
          error={alertsQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={ShieldAlertIcon}
          emptyTitle="No AML alerts"
          emptyDescription="Screen a party or monitor a transaction to surface suspicious activity."
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Subject</TableHead>
                <TableHead>Rule</TableHead>
                <TableHead>Category</TableHead>
                <TableHead className="text-end">Score</TableHead>
                <TableHead>Severity</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Raised</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((a) => (
                <TableRow key={a.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                  <TableCell className="font-medium">{a.subjectRef}</TableCell>
                  <TableCell className="font-mono text-xs">{a.ruleCode}</TableCell>
                  <TableCell className="text-muted-foreground">{a.category}</TableCell>
                  <TableCell className="text-end tabular-nums">{a.riskScore}</TableCell>
                  <TableCell>
                    <Badge variant={severityVariant(a.severity)}>{a.severity}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{a.status}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    <TimeSince value={a.createdAt} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <SectionCard title="Investigation cases" description="Cases opened from clustered alerts." icon={FolderOpenIcon} flush>
          <DataState
            isLoading={casesQ.isLoading}
            isError={casesQ.isError}
            error={casesQ.error}
            isEmpty={(casesQ.data ?? []).length === 0}
            emptyIcon={FolderOpenIcon}
            emptyTitle="No cases"
            emptyDescription="Cases group related alerts for investigation."
            className="p-6"
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Subject</TableHead>
                  <TableHead className="text-end">Alerts</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Opened</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(casesQ.data ?? []).map((c) => (
                  <TableRow key={c.id}>
                    <TableCell className="font-medium">{c.subject}</TableCell>
                    <TableCell className="text-end tabular-nums">{c.alertCount}</TableCell>
                    <TableCell>
                      <Badge variant={c.status === "OPEN" ? "warning" : "muted"}>{c.status}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      <TimeSince value={c.openedAt} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </SectionCard>

        <SectionCard title="STR reports" description="Suspicious Transaction Reports for FIU-IND." icon={FileTextIcon} flush>
          <DataState
            isLoading={strQ.isLoading}
            isError={strQ.isError}
            error={strQ.error}
            isEmpty={(strQ.data ?? []).length === 0}
            emptyIcon={FileTextIcon}
            emptyTitle="No STRs"
            emptyDescription="File an STR when suspicious activity warrants it."
            className="p-6"
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Subject</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Reference</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(strQ.data ?? []).map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="font-medium">{r.subject}</TableCell>
                    <TableCell>
                      <Badge variant={r.status === "FILED" ? "success" : "warning"}>{r.status}</Badge>
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {r.fiuReferenceId ?? <ShortId id={r.id} />}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      <TimeSince value={r.createdAt} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </SectionCard>
      </div>

      <ScreenSheet open={screenOpen} onOpenChange={setScreenOpen} />
      <MonitorSheet open={monitorOpen} onOpenChange={setMonitorOpen} />
      <StrSheet open={strOpen} onOpenChange={setStrOpen} />
    </div>
  );
}

function ScreenSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const [partyType, setPartyType] = useState<(typeof PARTY_TYPES)[number]>("INDIVIDUAL");
  const [partyName, setPartyName] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [result, setResult] = useState<Screening | null>(null);

  const screenM = useMutation({
    mutationFn: () =>
      api<Screening>("/v1/aml/screen", {
        method: "POST",
        body: { partyType, partyName: partyName.trim(), identifier: identifier.trim() || undefined },
      }),
    onSuccess: (data) => {
      setResult(data);
      qc.invalidateQueries({ queryKey: ["aml-alerts"] });
    },
  });

  return (
    <Sheet
      open={open}
      onOpenChange={(o) => {
        onOpenChange(o);
        if (!o) {
          setResult(null);
          screenM.reset();
        }
      }}
    >
      <SheetContent side="right" className="sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Screen a party</SheetTitle>
          <SheetDescription>Check a name / PAN against OFAC, UN and PEP watchlists.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-4 overflow-y-auto px-4">
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="s-type">Party type</FieldLabel>
              <Select value={partyType} onValueChange={(v) => setPartyType(v as (typeof PARTY_TYPES)[number])}>
                <SelectTrigger id="s-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PARTY_TYPES.map((t) => (
                    <SelectItem key={t} value={t}>
                      {t}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel htmlFor="s-name">Party name</FieldLabel>
              <Input id="s-name" value={partyName} onChange={(e) => setPartyName(e.target.value)} placeholder="Acme Trading Pvt Ltd" autoFocus />
            </Field>
            <Field>
              <FieldLabel htmlFor="s-id">Identifier (PAN / account)</FieldLabel>
              <Input id="s-id" value={identifier} onChange={(e) => setIdentifier(e.target.value.toUpperCase())} placeholder="AAAAA0000A" />
              <FieldDescription>Optional. Also screened against blocked identifiers.</FieldDescription>
            </Field>
          </FieldGroup>

          {result && (
            <div
              className={cn(
                "rounded-md p-3 text-sm",
                result.result === "HIT" ? "bg-destructive/10 text-destructive" : "bg-success/10 text-success",
              )}
            >
              {result.result === "HIT"
                ? `HIT — ${result.matchCount} watchlist match(es), risk score ${result.riskScore}. An alert was raised.`
                : "CLEAR — no watchlist matches."}
            </div>
          )}
          {screenM.isError && <p className="text-sm text-destructive">{errMsg(screenM.error)}</p>}
        </div>
        <SheetFooter>
          <div className="flex justify-end gap-2">
            <SheetClose render={<Button type="button" variant="outline">Close</Button>} />
            <Button type="button" disabled={partyName.trim() === "" || screenM.isPending} onClick={() => screenM.mutate()}>
              {screenM.isPending ? "Screening…" : "Screen"}
            </Button>
          </div>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}

function MonitorSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const [transactionRef, setTransactionRef] = useState("");
  const [amount, setAmount] = useState("");
  const [mcc, setMcc] = useState("");
  const [countryCode, setCountryCode] = useState("");
  const [result, setResult] = useState<MonitorResult | null>(null);

  const minor = rupeesToMinor(amount);
  const canSubmit = transactionRef.trim() !== "" && minor !== null && minor > 0;

  const monitorM = useMutation({
    mutationFn: () =>
      api<MonitorResult>("/v1/aml/monitor", {
        method: "POST",
        body: {
          transactionRef: transactionRef.trim(),
          amountMinor: minor,
          currency: "INR",
          mcc: mcc.trim() ? Number(mcc) : undefined,
          countryCode: countryCode.trim() || undefined,
        },
      }),
    onSuccess: (data) => {
      setResult(data);
      qc.invalidateQueries({ queryKey: ["aml-alerts"] });
    },
  });

  return (
    <Sheet
      open={open}
      onOpenChange={(o) => {
        onOpenChange(o);
        if (!o) {
          setResult(null);
          monitorM.reset();
        }
      }}
    >
      <SheetContent side="right" className="sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Monitor a transaction</SheetTitle>
          <SheetDescription>Run the AML rules engine against a transaction to surface alerts.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-4 overflow-y-auto px-4">
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="m-ref">Transaction reference</FieldLabel>
              <Input id="m-ref" value={transactionRef} onChange={(e) => setTransactionRef(e.target.value)} placeholder="pay_9fZ3…" autoFocus />
            </Field>
            <Field>
              <FieldLabel htmlFor="m-amount">Amount (₹)</FieldLabel>
              <Input id="m-amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="950000.00" />
              <FieldDescription>Amounts just under ₹10,00,000 are flagged as structuring.</FieldDescription>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel htmlFor="m-mcc">MCC</FieldLabel>
                <Input id="m-mcc" inputMode="numeric" value={mcc} onChange={(e) => setMcc(e.target.value)} placeholder="7995" />
              </Field>
              <Field>
                <FieldLabel htmlFor="m-country">Country</FieldLabel>
                <Input id="m-country" value={countryCode} onChange={(e) => setCountryCode(e.target.value.toUpperCase())} placeholder="IN" />
              </Field>
            </div>
          </FieldGroup>

          {result && (
            <div className="rounded-md bg-muted/50 p-3 text-sm">
              {result.alertCount === 0
                ? "No rules fired — transaction looks clean."
                : `${result.alertCount} alert(s) raised: ${result.alerts.map((a) => a.ruleCode).join(", ")}`}
            </div>
          )}
          {monitorM.isError && <p className="text-sm text-destructive">{errMsg(monitorM.error)}</p>}
        </div>
        <SheetFooter>
          <div className="flex justify-end gap-2">
            <SheetClose render={<Button type="button" variant="outline">Close</Button>} />
            <Button type="button" disabled={!canSubmit || monitorM.isPending} onClick={() => monitorM.mutate()}>
              {monitorM.isPending ? "Evaluating…" : "Evaluate"}
            </Button>
          </div>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}

function StrSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const [subject, setSubject] = useState("");
  const [grounds, setGrounds] = useState("");

  const fileM = useMutation({
    mutationFn: () =>
      api<StrReport>("/v1/aml/str-reports", {
        method: "POST",
        body: { subject: subject.trim(), grounds: grounds.trim(), fileImmediately: true },
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["aml-str-reports"] });
      onOpenChange(false);
      setSubject("");
      setGrounds("");
    },
  });

  const canSubmit = subject.trim() !== "" && grounds.trim() !== "";

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="sm:max-w-md">
        <form
          className="flex h-full flex-col"
          onSubmit={(e) => {
            e.preventDefault();
            if (canSubmit) fileM.mutate();
          }}
        >
          <SheetHeader>
            <SheetTitle>File a Suspicious Transaction Report</SheetTitle>
            <SheetDescription>Generate an FIU-IND-style STR under PMLA and file it (sandbox assigns a reference).</SheetDescription>
          </SheetHeader>
          <div className="flex-1 space-y-4 overflow-y-auto px-4">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="str-subject">Subject</FieldLabel>
                <Input id="str-subject" value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="Suspicious layering by beneficiary ben_1" autoFocus />
              </Field>
              <Field>
                <FieldLabel htmlFor="str-grounds">Grounds of suspicion</FieldLabel>
                <Textarea id="str-grounds" value={grounds} onChange={(e) => setGrounds(e.target.value)} placeholder="Rapid pass-through consistent with mule behaviour…" rows={4} />
              </Field>
            </FieldGroup>
            {fileM.isError && <p className="text-sm text-destructive">{errMsg(fileM.error)}</p>}
          </div>
          <SheetFooter>
            <div className="flex justify-end gap-2">
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
              <Button type="submit" disabled={!canSubmit || fileM.isPending}>
                {fileM.isPending ? "Filing…" : "File STR"}
              </Button>
            </div>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}
