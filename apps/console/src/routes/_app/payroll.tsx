import {
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
  Separator,
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
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { BanknoteIcon, ClockIcon, PlusIcon, Trash2Icon, UsersIcon } from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { KpiRow, KpiTile } from "@/components/stat-tile";
import { StatusBadge } from "@/features/money/status";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/payroll")({ component: PayrollPage });

type PayoutRail = "UPI" | "IMPS" | "NEFT" | "RTGS";

type PayrollBatchSummary = {
  id: string;
  currency: string;
  period: string | null;
  status: string;
  lineCount: number;
  totalGrossMinor: number;
  totalNetMinor: number;
  paidCount: number;
  failedCount: number;
  description: string | null;
};

const RAILS: PayoutRail[] = ["UPI", "IMPS", "NEFT", "RTGS"];
const STATUSES = [
  "PENDING_APPROVAL",
  "DISBURSED",
  "PARTIALLY_DISBURSED",
  "FAILED",
  "REJECTED",
];

type LineDraft = {
  employeeRef: string;
  employeeName: string;
  rail: PayoutRail;
  destination: string;
  accountNumber: string;
  ifsc: string;
  gross: string;
  pf: string;
  esi: string;
  pt: string;
  tds: string;
};

const emptyLine = (): LineDraft => ({
  employeeRef: "",
  employeeName: "",
  rail: "IMPS",
  destination: "",
  accountNumber: "",
  ifsc: "",
  gross: "",
  pf: "",
  esi: "",
  pt: "",
  tds: "",
});

const statutoryMinor = (l: LineDraft) =>
  (rupeesToMinor(l.pf) ?? 0) +
  (rupeesToMinor(l.esi) ?? 0) +
  (rupeesToMinor(l.pt) ?? 0) +
  (rupeesToMinor(l.tds) ?? 0);

const netMinor = (l: LineDraft) => Math.max(0, (rupeesToMinor(l.gross) ?? 0) - statutoryMinor(l));

function PayrollPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const batchesQ = useQuery({
    queryKey: ["payroll-batches"],
    queryFn: () => api<PayrollBatchSummary[]>("/v1/payroll/batches"),
  });

  const rows = batchesQ.data ?? [];
  const pendingRuns = rows.filter((b) => b.status === "PENDING_APPROVAL");
  const pendingNet = pendingRuns.reduce((s, b) => s + b.totalNetMinor, 0);
  const disbursedNet = rows
    .filter((b) => b.status === "DISBURSED" || b.status === "PARTIALLY_DISBURSED")
    .reduce((s, b) => s + b.totalNetMinor, 0);
  const employeeCount = rows.reduce((s, b) => s + b.lineCount, 0);
  const lv = useListView(rows, {
    searchFields: (b) => [b.id, b.description, b.period, b.status],
    filterFields: { status: (b) => b.status },
    sortFields: {
      net: (b) => b.totalNetMinor,
      gross: (b) => b.totalGrossMinor,
      employees: (b) => b.lineCount,
      status: (b) => b.status,
    },
  });

  // Create form state
  const [currency, setCurrency] = useState("INR");
  const [period, setPeriod] = useState("");
  const [description, setDescription] = useState("");
  const [lines, setLines] = useState<LineDraft[]>([emptyLine()]);
  const [formError, setFormError] = useState<string | null>(null);

  const invalidate = () => qc.invalidateQueries({ queryKey: ["payroll-batches"] });

  const createM = useMutation({
    mutationFn: (body: {
      currency: string;
      period?: string;
      description?: string;
      lines: {
        employeeRef: string;
        employeeName?: string;
        rail: PayoutRail;
        destination: string;
        accountNumber?: string;
        ifsc?: string;
        grossMinor: number;
        pfMinor: number;
        esiMinor: number;
        ptMinor: number;
        tdsMinor: number;
      }[];
    }) => api<PayrollBatchSummary>("/v1/payroll/batches", { method: "POST", body }),
    meta: { successMessage: "Payroll run staged for approval" },
    onSuccess: () => {
      invalidate();
      setOpen(false);
      setPeriod("");
      setDescription("");
      setLines([emptyLine()]);
      setFormError(null);
    },
  });

  const approveM = useMutation({
    mutationFn: (id: string) => api<unknown>(`/v1/payroll/batches/${id}/approve`, { method: "POST" }),
    meta: { successMessage: "Payroll approved & disbursed" },
    onSuccess: invalidate,
  });

  const rejectM = useMutation({
    mutationFn: (id: string) => api<unknown>(`/v1/payroll/batches/${id}/reject`, { method: "POST" }),
    meta: { successMessage: "Payroll run rejected" },
    onSuccess: invalidate,
  });

  const draftNetMinor = lines.reduce((s, l) => s + netMinor(l), 0);
  const busy = approveM.isPending || rejectM.isPending;

  function updateLine(i: number, patch: Partial<LineDraft>) {
    setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  }

  function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    const payload = [];
    for (const l of lines) {
      if (!l.employeeRef.trim()) {
        setFormError("Every line needs an employee reference.");
        return;
      }
      const grossMinor = rupeesToMinor(l.gross);
      if (grossMinor === null || grossMinor <= 0) {
        setFormError("Every line needs a gross pay greater than zero.");
        return;
      }
      if (!l.destination.trim()) {
        setFormError("Every line needs a beneficiary destination.");
        return;
      }
      const pfMinor = rupeesToMinor(l.pf) ?? 0;
      const esiMinor = rupeesToMinor(l.esi) ?? 0;
      const ptMinor = rupeesToMinor(l.pt) ?? 0;
      const tdsMinor = rupeesToMinor(l.tds) ?? 0;
      if (grossMinor - (pfMinor + esiMinor + ptMinor + tdsMinor) <= 0) {
        setFormError(`Net pay must be positive for ${l.employeeRef.trim()}.`);
        return;
      }
      payload.push({
        employeeRef: l.employeeRef.trim(),
        employeeName: l.employeeName.trim() || undefined,
        rail: l.rail,
        destination: l.destination.trim(),
        accountNumber: l.accountNumber.trim() || undefined,
        ifsc: l.ifsc.trim() || undefined,
        grossMinor,
        pfMinor,
        esiMinor,
        ptMinor,
        tdsMinor,
      });
    }
    if (payload.length === 0) {
      setFormError("Add at least one employee line.");
      return;
    }
    setFormError(null);
    createM.mutate({
      currency: currency.trim().toUpperCase() || "INR",
      period: period.trim() || undefined,
      description: description.trim() || undefined,
      lines: payload,
    });
  }

  const csvColumns = [
    { header: "Batch ID", value: (b: PayrollBatchSummary) => b.id },
    { header: "Status", value: (b: PayrollBatchSummary) => b.status },
    { header: "Period", value: (b: PayrollBatchSummary) => b.period },
    { header: "Currency", value: (b: PayrollBatchSummary) => b.currency },
    { header: "Employees", value: (b: PayrollBatchSummary) => b.lineCount },
    { header: "Gross (minor)", value: (b: PayrollBatchSummary) => b.totalGrossMinor },
    { header: "Net (minor)", value: (b: PayrollBatchSummary) => b.totalNetMinor },
    { header: "Paid", value: (b: PayrollBatchSummary) => b.paidCount },
    { header: "Failed", value: (b: PayrollBatchSummary) => b.failedCount },
    { header: "Description", value: (b: PayrollBatchSummary) => b.description },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Qeet People payroll disbursement: stage a payroll run (net pay = gross − PF/ESI/PT/TDS), then approve it as a unit to disburse every employee's net pay through the payouts engine — one salary slip + receipt per employee."
        actions={
          <Button variant="outline" render={<Link to={"/payout-batches" as never} />}>
            <UsersIcon /> Payout batches
          </Button>
        }
      />

      <KpiRow>
        <KpiTile label="Payroll runs" value={rows.length.toLocaleString("en-IN")} icon={UsersIcon} tone="info" hint={`${employeeCount.toLocaleString("en-IN")} employee lines`} loading={batchesQ.isLoading} />
        <KpiTile label="Pending approval" value={pendingRuns.length.toLocaleString("en-IN")} icon={ClockIcon} tone={pendingRuns.length > 0 ? "warning" : "neutral"} loading={batchesQ.isLoading} />
        <KpiTile label="Awaiting disbursal" value={formatInr(pendingNet)} icon={BanknoteIcon} tone={pendingNet > 0 ? "warning" : "neutral"} hint="Net pay pending approval" loading={batchesQ.isLoading} />
        <KpiTile label="Disbursed net" value={formatInr(disbursedNet)} icon={BanknoteIcon} tone="success" loading={batchesQ.isLoading} />
      </KpiRow>

      <Card className="gap-0 overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search payroll runs…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: STATUSES.map((s) => ({ label: s, value: s })),
              onChange: (v) => lv.setFilter("status", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("payroll-batches", lv.view, csvColumns)
              : exportToJson("payroll-batches", lv.view)
          }
          exportDisabled={lv.view.length === 0}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
        >
          <Button size="sm" onClick={() => setOpen(true)}>
            <PlusIcon /> New payroll run
          </Button>
        </ListToolbar>

        <DataState
          isLoading={batchesQ.isLoading}
          isError={batchesQ.isError}
          error={batchesQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={UsersIcon}
          emptyTitle="No payroll runs"
          emptyDescription="Stage a payroll run to disburse salaries with statutory deductions."
          className="p-6"
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Batch ID</TableHead>
                  <SortHeader columnKey="status" sort={lv.sort} onToggle={lv.toggleSort}>
                    Status
                  </SortHeader>
                  <TableHead>Period</TableHead>
                  <SortHeader
                    columnKey="employees"
                    sort={lv.sort}
                    onToggle={lv.toggleSort}
                    className="text-end"
                  >
                    Employees
                  </SortHeader>
                  <SortHeader columnKey="gross" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Gross
                  </SortHeader>
                  <SortHeader columnKey="net" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Net
                  </SortHeader>
                  <TableHead className="text-end">Paid / Failed</TableHead>
                  <TableHead className="text-end">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((b) => (
                  <TableRow key={b.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                    <TableCell className="font-mono text-xs">{b.id}</TableCell>
                    <TableCell>
                      <StatusBadge status={b.status} />
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">{b.period ?? "—"}</TableCell>
                    <TableCell className="text-end tabular-nums">{b.lineCount}</TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(b.totalGrossMinor, b.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(b.totalNetMinor, b.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums text-muted-foreground">
                      {b.paidCount} / {b.failedCount}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end gap-2">
                        {b.status === "PENDING_APPROVAL" && (
                          <>
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={busy}
                              onClick={() => approveM.mutate(b.id)}
                            >
                              Approve
                            </Button>
                            <Button
                              size="sm"
                              variant="ghost"
                              disabled={busy}
                              onClick={() => rejectM.mutate(b.id)}
                            >
                              Reject
                            </Button>
                          </>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </DataState>
      </Card>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="w-full sm:max-w-2xl">
          <form onSubmit={submitCreate} className="flex h-full flex-col">
            <SheetHeader>
              <SheetTitle>New payroll run</SheetTitle>
              <SheetDescription>
                All lines are staged as PENDING_APPROVAL. Net pay = gross − (PF + ESI + PT + TDS).
                Approve the run to disburse every net pay at once (maker-checker).
              </SheetDescription>
            </SheetHeader>

            <FieldGroup className="flex-1 overflow-y-auto p-4">
              <div className="grid gap-2 sm:grid-cols-2">
                <Field>
                  <FieldLabel htmlFor="currency">Currency</FieldLabel>
                  <Input
                    id="currency"
                    value={currency}
                    onChange={(e) => setCurrency(e.target.value)}
                    placeholder="INR"
                    maxLength={3}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="period">Period</FieldLabel>
                  <Input
                    id="period"
                    value={period}
                    onChange={(e) => setPeriod(e.target.value)}
                    placeholder="2026-07"
                  />
                </Field>
              </div>

              <Field>
                <FieldLabel htmlFor="run-desc">Description</FieldLabel>
                <Textarea
                  id="run-desc"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Optional note for the run (e.g. July salary cycle)"
                  rows={2}
                />
              </Field>

              <Separator />

              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">Employees ({lines.length})</span>
                <span className="text-sm tabular-nums text-muted-foreground">
                  Total net {formatInr(draftNetMinor, currency.trim().toUpperCase() || "INR")}
                </span>
              </div>

              {lines.map((l, i) => (
                <div key={i} className="rounded-lg border p-3">
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-xs font-medium text-muted-foreground">
                      Employee {i + 1} · net {formatInr(netMinor(l), currency.trim().toUpperCase() || "INR")}
                    </span>
                    <Button
                      type="button"
                      size="icon-sm"
                      variant="ghost"
                      aria-label="Remove line"
                      disabled={lines.length === 1}
                      onClick={() => setLines((prev) => prev.filter((_, idx) => idx !== i))}
                    >
                      <Trash2Icon />
                    </Button>
                  </div>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <Field>
                      <FieldLabel htmlFor={`ref-${i}`}>Employee ref</FieldLabel>
                      <Input
                        id={`ref-${i}`}
                        value={l.employeeRef}
                        onChange={(e) => updateLine(i, { employeeRef: e.target.value })}
                        placeholder="EMP-0001"
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor={`name-${i}`}>Name</FieldLabel>
                      <Input
                        id={`name-${i}`}
                        value={l.employeeName}
                        onChange={(e) => updateLine(i, { employeeName: e.target.value })}
                        placeholder="Optional"
                      />
                    </Field>
                    <Field>
                      <FieldLabel>Rail</FieldLabel>
                      <Select value={l.rail} onValueChange={(v) => updateLine(i, { rail: v as PayoutRail })}>
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {RAILS.map((r) => (
                            <SelectItem key={r} value={r}>
                              {r}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </Field>
                    <Field>
                      <FieldLabel htmlFor={`dest-${i}`}>Beneficiary</FieldLabel>
                      <Input
                        id={`dest-${i}`}
                        value={l.destination}
                        onChange={(e) => updateLine(i, { destination: e.target.value })}
                        placeholder="name@upi  or  account@IFSC"
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor={`acct-${i}`}>Account no. (penny-drop)</FieldLabel>
                      <Input
                        id={`acct-${i}`}
                        value={l.accountNumber}
                        onChange={(e) => updateLine(i, { accountNumber: e.target.value })}
                        placeholder="Optional"
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor={`ifsc-${i}`}>IFSC (penny-drop)</FieldLabel>
                      <Input
                        id={`ifsc-${i}`}
                        value={l.ifsc}
                        onChange={(e) => updateLine(i, { ifsc: e.target.value })}
                        placeholder="Optional"
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor={`gross-${i}`}>Gross</FieldLabel>
                      <Input
                        id={`gross-${i}`}
                        type="number"
                        min="0"
                        step="0.01"
                        inputMode="decimal"
                        value={l.gross}
                        onChange={(e) => updateLine(i, { gross: e.target.value })}
                        placeholder="0.00"
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor={`pf-${i}`}>PF</FieldLabel>
                      <Input
                        id={`pf-${i}`}
                        type="number"
                        min="0"
                        step="0.01"
                        inputMode="decimal"
                        value={l.pf}
                        onChange={(e) => updateLine(i, { pf: e.target.value })}
                        placeholder="0.00"
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor={`esi-${i}`}>ESI</FieldLabel>
                      <Input
                        id={`esi-${i}`}
                        type="number"
                        min="0"
                        step="0.01"
                        inputMode="decimal"
                        value={l.esi}
                        onChange={(e) => updateLine(i, { esi: e.target.value })}
                        placeholder="0.00"
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor={`pt-${i}`}>PT</FieldLabel>
                      <Input
                        id={`pt-${i}`}
                        type="number"
                        min="0"
                        step="0.01"
                        inputMode="decimal"
                        value={l.pt}
                        onChange={(e) => updateLine(i, { pt: e.target.value })}
                        placeholder="0.00"
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor={`tds-${i}`}>TDS</FieldLabel>
                      <Input
                        id={`tds-${i}`}
                        type="number"
                        min="0"
                        step="0.01"
                        inputMode="decimal"
                        value={l.tds}
                        onChange={(e) => updateLine(i, { tds: e.target.value })}
                        placeholder="0.00"
                      />
                    </Field>
                  </div>
                </div>
              ))}

              <Button
                type="button"
                variant="outline"
                onClick={() => setLines((prev) => [...prev, emptyLine()])}
              >
                <PlusIcon /> Add employee
              </Button>

              {formError && <FieldDescription className="text-destructive">{formError}</FieldDescription>}
            </FieldGroup>

            <SheetFooter>
              <Button type="submit" disabled={createM.isPending}>
                {createM.isPending ? "Staging…" : "Stage payroll run"}
              </Button>
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>
    </div>
  );
}
