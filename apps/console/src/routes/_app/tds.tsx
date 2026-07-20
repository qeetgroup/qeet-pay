import {
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  DataState,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Separator,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  TimeSince,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { DownloadIcon, FileSpreadsheetIcon, LandmarkIcon, PlusIcon, ScrollTextIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { toast } from "sonner";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { FormSheet, KeyValue, LabeledField, SummaryStat } from "@/features/gst/ui";
import { ShortId } from "@/features/finance/shared";
import { StatusBadge } from "@/features/money/status";
import { API_BASE, ApiError, api, keyStore } from "@/lib/api";
import { downloadBlob } from "@/lib/export";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatBps, formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/tds")({ component: TdsPage });

type DeductionView = {
  id: string;
  kind: string;
  section: string;
  deducteeName: string;
  deducteePan: string | null;
  grossMinor: number;
  rateBps: number;
  taxMinor: number;
  transactionRef: string | null;
  deductedOn: string;
  quarter: string;
  certificateNo: string | null;
  createdAt: string;
};

type SummaryView = {
  quarter: string;
  count: number;
  totalGrossMinor: number;
  totalTaxMinor: number;
  taxBySection: Record<string, number>;
};

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Indian FY quarter for an ISO date, matching the backend TdsCalculator format ("2026-Q2"). */
function quarterOfIso(iso: string): string {
  const [y, m] = iso.split("-").map(Number);
  const fyStart = m >= 4 ? y : y - 1;
  const q = m >= 4 && m <= 6 ? 1 : m >= 7 && m <= 9 ? 2 : m >= 10 && m <= 12 ? 3 : 4;
  return `${fyStart}-Q${q}`;
}

function TdsPage() {
  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Record tax deducted / collected at source, issue deductee certificates, and prepare + file the quarterly statutory returns (Form 24Q/26Q/27EQ)." />
      <Tabs defaultValue="deductions">
        <TabsList>
          <TabsTrigger value="deductions">
            <LandmarkIcon /> Deductions
          </TabsTrigger>
          <TabsTrigger value="returns">
            <FileSpreadsheetIcon /> Returns
          </TabsTrigger>
        </TabsList>
        <TabsContent value="deductions" className="mt-4">
          <DeductionsTab />
        </TabsContent>
        <TabsContent value="returns" className="mt-4">
          <ReturnsTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function DeductionsTab() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const [kind, setKind] = useState("TDS");
  const [section, setSection] = useState("");
  const [deducteeName, setDeducteeName] = useState("");
  const [deducteePan, setDeducteePan] = useState("");
  const [gross, setGross] = useState("");
  const [rateBps, setRateBps] = useState("1000");
  const [transactionRef, setTransactionRef] = useState("");
  const [deductedOn, setDeductedOn] = useState(todayIso());

  const [summaryQuarter, setSummaryQuarter] = useState(quarterOfIso(todayIso()));

  const deductionsQ = useQuery({
    queryKey: ["tds-deductions"],
    queryFn: () => api<DeductionView[]>("/v1/tds/deductions"),
    staleTime: 15_000,
  });
  const summaryQ = useQuery({
    queryKey: ["tds-summary", summaryQuarter],
    queryFn: () => api<SummaryView>("/v1/tds/summary", { query: { quarter: summaryQuarter } }),
    enabled: summaryQuarter !== "",
    staleTime: 15_000,
  });

  const deductions = deductionsQ.data ?? [];

  const recordMut = useMutation({
    mutationFn: (body: unknown) => api<DeductionView>("/v1/tds/deductions", { method: "POST", body }),
    meta: { successMessage: "Deduction recorded" },
    onSuccess: () => {
      setOpen(false);
      resetForm();
      qc.invalidateQueries({ queryKey: ["tds-deductions"] });
      qc.invalidateQueries({ queryKey: ["tds-summary"] });
    },
  });

  const certMut = useMutation({
    mutationFn: (id: string) => api<DeductionView>(`/v1/tds/deductions/${id}/certificate`, { method: "POST" }),
    meta: { silent: true },
    onSuccess: (d) => {
      toast.success("Certificate issued", { description: d.certificateNo ?? undefined });
      qc.invalidateQueries({ queryKey: ["tds-deductions"] });
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Could not issue certificate"),
  });

  function resetForm() {
    setKind("TDS");
    setSection("");
    setDeducteeName("");
    setDeducteePan("");
    setGross("");
    setRateBps("1000");
    setTransactionRef("");
    setDeductedOn(todayIso());
  }

  function submit() {
    recordMut.mutate({
      kind,
      section: section.trim(),
      deducteeName: deducteeName.trim(),
      deducteePan: deducteePan.trim() || null,
      grossMinor: rupeesToMinor(gross) ?? 0,
      rateBps: Number(rateBps),
      transactionRef: transactionRef.trim() || null,
      deductedOn,
    });
  }

  const formValid =
    section.trim() !== "" &&
    deducteeName.trim() !== "" &&
    (rupeesToMinor(gross) ?? 0) > 0 &&
    Number.isFinite(Number(rateBps)) &&
    Number(rateBps) >= 0 &&
    Number(rateBps) <= 10_000;

  const quarterOptions = useMemo(() => {
    const set = new Set<string>([quarterOfIso(todayIso())]);
    for (const d of deductions) set.add(d.quarter);
    return Array.from(set).sort().reverse();
  }, [deductions]);

  const lv = useListView(deductions, {
    searchFields: (d) => [d.kind, d.section, d.deducteeName, d.deducteePan, d.transactionRef],
    filterFields: { kind: (d) => d.kind, quarter: (d) => d.quarter },
    sortFields: {
      deductee: (d) => d.deducteeName,
      tax: (d) => d.taxMinor,
      date: (d) => d.deductedOn,
    },
  });

  const csvColumns = [
    { header: "Kind", value: (d: DeductionView) => d.kind },
    { header: "Section", value: (d: DeductionView) => d.section },
    { header: "Deductee", value: (d: DeductionView) => d.deducteeName },
    { header: "PAN", value: (d: DeductionView) => d.deducteePan ?? "" },
    { header: "Gross", value: (d: DeductionView) => d.grossMinor / 100 },
    { header: "Rate (bps)", value: (d: DeductionView) => d.rateBps },
    { header: "Tax", value: (d: DeductionView) => d.taxMinor / 100 },
    { header: "Quarter", value: (d: DeductionView) => d.quarter },
    { header: "Certificate", value: (d: DeductionView) => d.certificateNo ?? "" },
  ];

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <CardTitle>Quarterly summary</CardTitle>
            <CardDescription>Per-section tax totals for the selected FY quarter.</CardDescription>
          </div>
          <Select value={summaryQuarter} onValueChange={(v) => v && setSummaryQuarter(v)}>
            <SelectTrigger size="sm" className="w-40">
              <SelectValue placeholder="Quarter" />
            </SelectTrigger>
            <SelectContent>
              {quarterOptions.map((q) => (
                <SelectItem key={q} value={q}>
                  {q}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-3">
            <SummaryStat label="Deductions" value={summaryQ.data?.count ?? 0} hint={summaryQuarter} />
            <SummaryStat label="Gross amount" value={formatInr(summaryQ.data?.totalGrossMinor ?? 0)} />
            <SummaryStat label="Tax at source" value={formatInr(summaryQ.data?.totalTaxMinor ?? 0)} />
          </div>
          <DataState
            isLoading={summaryQ.isLoading}
            isError={summaryQ.isError}
            error={summaryQ.error}
            isEmpty={Object.keys(summaryQ.data?.taxBySection ?? {}).length === 0}
            emptyIcon={ScrollTextIcon}
            emptyTitle="No deductions this quarter"
            emptyDescription="Record a deduction dated in this quarter to populate the section breakdown."
            skeletonRows={2}
          >
            <div className="space-y-1.5">
              {Object.entries(summaryQ.data?.taxBySection ?? {}).map(([sec, tax]) => (
                <KeyValue key={sec} label={`Section ${sec}`}>
                  {formatInr(tax)}
                </KeyValue>
              ))}
            </div>
          </DataState>
        </CardContent>
      </Card>

      <Card className="gap-0 py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search deductee, section…"
          filters={[
            {
              id: "kind",
              label: "Kind",
              value: lv.filters.kind ?? "",
              onChange: (v) => lv.setFilter("kind", v),
              options: [
                { label: "TDS", value: "TDS" },
                { label: "TCS", value: "TCS" },
              ],
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("tds-deductions", lv.view, csvColumns)
              : exportToJson("tds-deductions", lv.view)
          }
          exportDisabled={lv.view.length === 0}
        >
          <Button size="sm" onClick={() => setOpen(true)}>
            <PlusIcon /> Record deduction
          </Button>
        </ListToolbar>

        <DataState
          isLoading={deductionsQ.isLoading}
          isError={deductionsQ.isError}
          error={deductionsQ.error}
          isEmpty={deductions.length === 0}
          emptyIcon={LandmarkIcon}
          emptyTitle="No deductions recorded"
          emptyDescription="Record a TDS or TCS deduction to start tracking tax at source."
          skeletonRows={5}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Kind</TableHead>
                <TableHead>Section</TableHead>
                <SortHeader columnKey="deductee" sort={lv.sort} onToggle={lv.toggleSort}>
                  Deductee
                </SortHeader>
                <SortHeader columnKey="date" sort={lv.sort} onToggle={lv.toggleSort}>
                  Deducted on
                </SortHeader>
                <TableHead className="text-end">Gross</TableHead>
                <TableHead className="text-end">Rate</TableHead>
                <SortHeader columnKey="tax" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                  Tax
                </SortHeader>
                <TableHead>Quarter</TableHead>
                <TableHead className="text-end">Certificate</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((d) => (
                <TableRow key={d.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                  <TableCell>
                    <Badge variant={d.kind === "TDS" ? "secondary" : "outline"}>{d.kind}</Badge>
                  </TableCell>
                  <TableCell className="font-medium">{d.section}</TableCell>
                  <TableCell>
                    <div className="font-medium">{d.deducteeName}</div>
                    {d.deducteePan && (
                      <div className="font-mono text-xs text-muted-foreground">{d.deducteePan}</div>
                    )}
                  </TableCell>
                  <TableCell className="tabular-nums">{d.deductedOn}</TableCell>
                  <TableCell className="text-end tabular-nums">{formatInr(d.grossMinor)}</TableCell>
                  <TableCell className="text-end tabular-nums">{formatBps(d.rateBps)}</TableCell>
                  <TableCell className="text-end font-medium tabular-nums">{formatInr(d.taxMinor)}</TableCell>
                  <TableCell className="tabular-nums">{d.quarter}</TableCell>
                  <TableCell className="text-end">
                    {d.certificateNo ? (
                      <span className="font-mono text-xs text-muted-foreground">{d.certificateNo}</span>
                    ) : (
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={certMut.isPending}
                        onClick={() => certMut.mutate(d.id)}
                      >
                        Issue certificate
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </Card>

      <FormSheet
        open={open}
        onOpenChange={setOpen}
        title="Record deduction"
        description="Tax deducted (TDS) or collected (TCS) at source. Tax = gross × rate, rounded to the paise."
        submitLabel="Record deduction"
        submitting={recordMut.isPending}
        disabled={!formValid}
        onSubmit={submit}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <LabeledField label="Kind" htmlFor="kind">
            <Select value={kind} onValueChange={(v) => v && setKind(v)}>
              <SelectTrigger id="kind">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="TDS">TDS — deducted at source</SelectItem>
                <SelectItem value="TCS">TCS — collected at source</SelectItem>
              </SelectContent>
            </Select>
          </LabeledField>
          <LabeledField label="Section" htmlFor="section" description="e.g. 194J, 194C, 52.">
            <Input id="section" value={section} onChange={(e) => setSection(e.target.value.toUpperCase())} placeholder="194J" />
          </LabeledField>
          <LabeledField label="Deductee name" htmlFor="deducteeName">
            <Input id="deducteeName" value={deducteeName} onChange={(e) => setDeducteeName(e.target.value)} />
          </LabeledField>
          <LabeledField label="Deductee PAN" htmlFor="deducteePan" description="Optional.">
            <Input id="deducteePan" value={deducteePan} onChange={(e) => setDeducteePan(e.target.value.toUpperCase())} placeholder="ABCDE1234F" />
          </LabeledField>
          <LabeledField label="Gross amount (₹)" htmlFor="gross">
            <Input id="gross" inputMode="decimal" value={gross} onChange={(e) => setGross(e.target.value)} placeholder="0.00" />
          </LabeledField>
          <LabeledField
            label="Rate (bps)"
            htmlFor="rateBps"
            description={`= ${formatBps(Number(rateBps) || 0)} of gross`}
          >
            <Input id="rateBps" type="number" min={0} max={10000} value={rateBps} onChange={(e) => setRateBps(e.target.value)} />
          </LabeledField>
        </div>

        <Separator />

        <div className="grid gap-4 sm:grid-cols-2">
          <LabeledField label="Transaction ref" htmlFor="transactionRef" description="Optional.">
            <Input id="transactionRef" value={transactionRef} onChange={(e) => setTransactionRef(e.target.value)} />
          </LabeledField>
          <LabeledField label="Deducted on" htmlFor="deductedOn" description="Determines the FY quarter.">
            <Input id="deductedOn" type="date" value={deductedOn} onChange={(e) => setDeductedOn(e.target.value)} />
          </LabeledField>
        </div>
      </FormSheet>
    </div>
  );
}

// ── Returns (Form 24Q/26Q/27EQ) ───────────────────────────────────────────────

type ReturnSummary = {
  id: string;
  form: string;
  fy: string;
  quarter: string;
  status: string;
  deducteeCount: number;
  deductionCount: number;
  totalGrossMinor: number;
  totalTaxMinor: number;
  bsrCode: string | null;
  challanNo: string | null;
  challanDate: string | null;
  ackToken: string | null;
  preparedAt: string | null;
  filedAt: string | null;
  createdAt: string;
};

const RETURN_FORMS = [
  { value: "FORM_26Q", label: "26Q — TDS (non-salary)" },
  { value: "FORM_24Q", label: "24Q — TDS (salary)" },
  { value: "FORM_27EQ", label: "27EQ — TCS" },
];

async function downloadReturn(id: string, form: string, quarter: string) {
  const key = keyStore.get();
  const res = await fetch(`${API_BASE}/v1/tds/returns/${id}/export`, {
    headers: key ? { "X-Api-Key": key } : {},
  });
  const text = await res.text();
  downloadBlob(text, "text/plain;charset=utf-8", `TDS_${form}_${quarter}.txt`);
}

function ReturnsTab() {
  const qc = useQueryClient();
  const [form, setForm] = useState("FORM_26Q");
  const [quarter, setQuarter] = useState(quarterOfIso(todayIso()));

  const returnsQ = useQuery({
    queryKey: ["tds-returns"],
    queryFn: () => api<ReturnSummary[]>("/v1/tds/returns"),
    staleTime: 15_000,
  });

  const prepareM = useMutation({
    mutationFn: () => api<{ ret: ReturnSummary }>("/v1/tds/returns/prepare", { method: "POST", body: { form, quarter } }),
    meta: { successMessage: "Return prepared" },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["tds-returns"] }),
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Could not prepare return"),
  });

  const fileM = useMutation({
    mutationFn: (id: string) => api<ReturnSummary>(`/v1/tds/returns/${id}/file`, { method: "POST" }),
    meta: { successMessage: "Return filed" },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["tds-returns"] }),
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Could not file return"),
  });

  const rows = returnsQ.data ?? [];

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>Prepare a quarterly return</CardTitle>
          <CardDescription>Aggregates the quarter's deductions into a Form 24Q/26Q/27EQ, ready to export and file.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="flex flex-col gap-3 sm:flex-row sm:items-end"
            onSubmit={(e) => {
              e.preventDefault();
              if (quarter.trim()) prepareM.mutate();
            }}
          >
            <LabeledField label="Form" htmlFor="ret-form">
              <Select value={form} onValueChange={(v) => v && setForm(v)}>
                <SelectTrigger id="ret-form" className="w-full sm:w-64">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {RETURN_FORMS.map((f) => (
                    <SelectItem key={f.value} value={f.value}>
                      {f.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </LabeledField>
            <LabeledField label="Quarter" htmlFor="ret-quarter" description='e.g. "2026-Q2".'>
              <Input id="ret-quarter" value={quarter} onChange={(e) => setQuarter(e.target.value)} className="sm:w-40" />
            </LabeledField>
            <Button type="submit" disabled={prepareM.isPending}>
              {prepareM.isPending ? "Preparing…" : "Prepare return"}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card className="gap-0 py-0">
        <div className="border-b p-4">
          <p className="text-sm font-medium">Prepared returns</p>
        </div>
        <DataState
          isLoading={returnsQ.isLoading}
          isError={returnsQ.isError}
          error={returnsQ.error}
          isEmpty={rows.length === 0}
          emptyIcon={FileSpreadsheetIcon}
          emptyTitle="No returns yet"
          emptyDescription="Prepare a return for a quarter to export the NSDL FVU file and file it to the TIN gateway."
          skeletonRows={4}
          className="p-6"
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Form</TableHead>
                <TableHead>Period</TableHead>
                <TableHead className="text-end">Deductees</TableHead>
                <TableHead className="text-end">Tax</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Ack / challan</TableHead>
                <TableHead className="text-end">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="font-medium">{r.form}</TableCell>
                  <TableCell className="tabular-nums">
                    {r.fy} · {r.quarter}
                  </TableCell>
                  <TableCell className="text-end tabular-nums">{r.deducteeCount}</TableCell>
                  <TableCell className="text-end tabular-nums">{formatInr(r.totalTaxMinor)}</TableCell>
                  <TableCell>
                    <StatusBadge status={r.status} />
                  </TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">
                    {r.ackToken ?? (r.preparedAt ? "prepared" : <ShortId id={r.id} />)}
                  </TableCell>
                  <TableCell className="text-end">
                    <div className="flex items-center justify-end gap-2">
                      <Button variant="outline" size="sm" onClick={() => downloadReturn(r.id, r.form, r.quarter)}>
                        <DownloadIcon /> Export
                      </Button>
                      {r.status !== "FILED" && (
                        <Button size="sm" disabled={fileM.isPending} onClick={() => fileM.mutate(r.id)}>
                          File
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </Card>
    </div>
  );
}
