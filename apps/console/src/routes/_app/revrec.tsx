import {
  Badge,
  Button,
  Card,
  DataState,
  Field,
  FieldDescription,
  FieldError,
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
  TimeSince,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { CalendarClockIcon, PlusIcon, ScaleIcon } from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/features/money/status";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/revrec")({ component: RevRecPage });

type RecognitionMethod = "STRAIGHT_LINE" | "IMMEDIATE";

type ScheduleSummary = {
  id: string;
  sourceType: string;
  sourceRef: string | null;
  currency: string;
  totalMinor: number;
  recognizedMinor: number;
  method: string;
  status: string;
  periodStart: string | null;
  periodEnd: string | null;
  createdAt: string;
};

const STATUSES = ["SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED"];
const METHODS = ["STRAIGHT_LINE", "IMMEDIATE"];
const RECOGNIZABLE = new Set<string>(["SCHEDULED", "IN_PROGRESS"]);

function RevRecPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const schedulesQ = useQuery({
    queryKey: ["revrec-schedules"],
    queryFn: () => api<ScheduleSummary[]>("/v1/revrec/schedules"),
  });

  const rows = schedulesQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (s) => [s.sourceType, s.sourceRef, s.status, s.method],
    filterFields: { status: (s) => s.status, method: (s) => s.method },
    sortFields: {
      total: (s) => s.totalMinor,
      recognized: (s) => s.recognizedMinor,
      createdAt: (s) => s.createdAt,
      status: (s) => s.status,
    },
  });

  // Create form state
  const [sourceType, setSourceType] = useState("subscription");
  const [sourceRef, setSourceRef] = useState("");
  const [total, setTotal] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [method, setMethod] = useState<RecognitionMethod>("STRAIGHT_LINE");
  const [start, setStart] = useState(() => new Date().toISOString().slice(0, 10));
  const [periods, setPeriods] = useState("12");
  const [totalError, setTotalError] = useState<string | null>(null);

  const invalidate = () => qc.invalidateQueries({ queryKey: ["revrec-schedules"] });

  const createM = useMutation({
    mutationFn: (body: unknown) =>
      api<unknown>("/v1/revrec/schedules", { method: "POST", body }),
    meta: { successMessage: "Recognition schedule created" },
    onSuccess: () => {
      invalidate();
      setOpen(false);
      setSourceRef("");
      setTotal("");
      setTotalError(null);
    },
  });

  const recognizeM = useMutation({
    mutationFn: (id: string) =>
      api<unknown>(`/v1/revrec/schedules/${id}/recognize`, { method: "POST" }),
    meta: { successMessage: "Recognised due periods" },
    onSuccess: invalidate,
  });

  function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(total);
    if (minor === null || minor <= 0) {
      setTotalError("Enter a total amount greater than zero.");
      return;
    }
    setTotalError(null);
    const periodsNum = method === "IMMEDIATE" ? 1 : Math.max(1, Number(periods) || 1);
    createM.mutate({
      sourceType: sourceType.trim() || "subscription",
      sourceRef: sourceRef.trim() || undefined,
      totalMinor: minor,
      currency: currency.trim().toUpperCase() || "INR",
      method,
      start,
      periods: periodsNum,
    });
  }

  const csvColumns = [
    { header: "Schedule ID", value: (s: ScheduleSummary) => s.id },
    { header: "Source Type", value: (s: ScheduleSummary) => s.sourceType },
    { header: "Source Ref", value: (s: ScheduleSummary) => s.sourceRef },
    { header: "Method", value: (s: ScheduleSummary) => s.method },
    { header: "Status", value: (s: ScheduleSummary) => s.status },
    { header: "Total (minor)", value: (s: ScheduleSummary) => s.totalMinor },
    { header: "Recognized (minor)", value: (s: ScheduleSummary) => s.recognizedMinor },
    { header: "Period Start", value: (s: ScheduleSummary) => s.periodStart },
    { header: "Period End", value: (s: ScheduleSummary) => s.periodEnd },
    { header: "Created At", value: (s: ScheduleSummary) => s.createdAt },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="IndAS 115 revenue recognition. A schedule defers revenue up front, then recognises it ratably per period — recognise due slices to post them to the ledger."
        actions={
          <Button variant="outline" render={<Link to={"/ledger" as never} />}>
            <ScaleIcon /> Ledger
          </Button>
        }
      />

      <Card className="gap-0 overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search schedules…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: STATUSES.map((s) => ({ label: s, value: s })),
              onChange: (v) => lv.setFilter("status", v),
            },
            {
              id: "method",
              label: "Method",
              value: lv.filters.method ?? "",
              options: METHODS.map((m) => ({ label: m, value: m })),
              onChange: (v) => lv.setFilter("method", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("revrec-schedules", lv.view, csvColumns)
              : exportToJson("revrec-schedules", lv.view)
          }
          exportDisabled={lv.view.length === 0}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
        >
          <Button size="sm" onClick={() => setOpen(true)}>
            <PlusIcon /> Create schedule
          </Button>
        </ListToolbar>

        <DataState
          isLoading={schedulesQ.isLoading}
          isError={schedulesQ.isError}
          error={schedulesQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={CalendarClockIcon}
          emptyTitle="No recognition schedules"
          emptyDescription="Create a schedule to defer revenue and recognise it over time."
          className="p-6"
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Source</TableHead>
                  <TableHead>Method</TableHead>
                  <SortHeader columnKey="recognized" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Recognised / Total
                  </SortHeader>
                  <SortHeader columnKey="status" sort={lv.sort} onToggle={lv.toggleSort}>
                    Status
                  </SortHeader>
                  <TableHead>Period</TableHead>
                  <SortHeader columnKey="createdAt" sort={lv.sort} onToggle={lv.toggleSort}>
                    Created
                  </SortHeader>
                  <TableHead className="text-end">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((s) => {
                  const pct =
                    s.totalMinor > 0 ? Math.round((s.recognizedMinor / s.totalMinor) * 100) : 0;
                  return (
                    <TableRow key={s.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                      <TableCell>
                        <div className="font-medium">{s.sourceType}</div>
                        {s.sourceRef && (
                          <div className="font-mono text-xs text-muted-foreground">{s.sourceRef}</div>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">{s.method}</Badge>
                      </TableCell>
                      <TableCell className="text-end tabular-nums">
                        <div>
                          {formatInr(s.recognizedMinor, s.currency)}
                          <span className="text-muted-foreground">
                            {" "}
                            / {formatInr(s.totalMinor, s.currency)}
                          </span>
                        </div>
                        <div className="mt-1 ml-auto h-1.5 w-28 overflow-hidden rounded-full bg-muted">
                          <div className="h-full rounded-full bg-primary" style={{ width: `${pct}%` }} />
                        </div>
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={s.status} />
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground tabular-nums">
                        {s.periodStart ?? "—"}
                        {s.periodEnd ? ` → ${s.periodEnd}` : ""}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        <TimeSince value={s.createdAt} />
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end">
                          {RECOGNIZABLE.has(s.status) && (
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={recognizeM.isPending}
                              onClick={() => recognizeM.mutate(s.id)}
                            >
                              Recognise due
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        </DataState>
      </Card>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="w-full sm:max-w-md">
          <form onSubmit={submitCreate} className="flex h-full flex-col">
            <SheetHeader>
              <SheetTitle>Create recognition schedule</SheetTitle>
              <SheetDescription>
                Defers the full amount now, then recognises it — evenly over N periods (straight-line)
                or all at once (immediate).
              </SheetDescription>
            </SheetHeader>

            <FieldGroup className="flex-1 overflow-y-auto p-4">
              <Field>
                <FieldLabel htmlFor="sourceType">Source type</FieldLabel>
                <Input
                  id="sourceType"
                  value={sourceType}
                  onChange={(e) => setSourceType(e.target.value)}
                  placeholder="subscription, invoice, contract…"
                />
              </Field>

              <Field>
                <FieldLabel htmlFor="sourceRef">Source reference</FieldLabel>
                <Input
                  id="sourceRef"
                  value={sourceRef}
                  onChange={(e) => setSourceRef(e.target.value)}
                  placeholder="Optional — e.g. the subscription or invoice id"
                />
              </Field>

              <Field>
                <FieldLabel htmlFor="total">Total amount</FieldLabel>
                <Input
                  id="total"
                  type="number"
                  min="0"
                  step="0.01"
                  inputMode="decimal"
                  value={total}
                  onChange={(e) => setTotal(e.target.value)}
                  placeholder="0.00"
                />
                <FieldDescription>Entered in rupees; sent to the API as paise.</FieldDescription>
                <FieldError errors={totalError ? [{ message: totalError }] : undefined} />
              </Field>

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
                <FieldLabel>Method</FieldLabel>
                <Select value={method} onValueChange={(v) => setMethod(v as RecognitionMethod)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="STRAIGHT_LINE">Straight-line (over N periods)</SelectItem>
                    <SelectItem value="IMMEDIATE">Immediate (point-in-time)</SelectItem>
                  </SelectContent>
                </Select>
              </Field>

              <Field>
                <FieldLabel htmlFor="start">Start date</FieldLabel>
                <Input
                  id="start"
                  type="date"
                  value={start}
                  onChange={(e) => setStart(e.target.value)}
                />
              </Field>

              <Field>
                <FieldLabel htmlFor="periods">Periods (months)</FieldLabel>
                <Input
                  id="periods"
                  type="number"
                  min="1"
                  step="1"
                  value={periods}
                  onChange={(e) => setPeriods(e.target.value)}
                  disabled={method === "IMMEDIATE"}
                />
                <FieldDescription>
                  {method === "IMMEDIATE"
                    ? "Immediate recognition uses a single period."
                    : "Number of equal monthly periods to spread the amount across."}
                </FieldDescription>
              </Field>
            </FieldGroup>

            <SheetFooter>
              <Button type="submit" disabled={createM.isPending}>
                {createM.isPending ? "Creating…" : "Create schedule"}
              </Button>
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>
    </div>
  );
}
