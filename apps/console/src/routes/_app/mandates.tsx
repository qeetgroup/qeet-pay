import {
  Badge,
  Button,
  Card,
  DataState,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Field,
  FieldError,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Separator,
  Sheet,
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
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
  BanIcon,
  FileTextIcon,
  PauseIcon,
  PlayIcon,
  PlusIcon,
  ReceiptIcon,
} from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";
import { type CsvColumn, exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/mandates")({ component: MandatesPage });

// ── Types & options (mirrors mandates/MandateController) ──────────────────────

type MandateView = {
  id: string;
  customerId: string | null;
  type: string;
  limitMinor: number;
  currency: string;
  frequency: string;
  startDate: string;
  endDate: string | null;
  status: string;
  providerMandateId: string | null;
};

type MandateDebitView = {
  id: string;
  mandateId: string;
  amountMinor: number;
  currency: string;
  status: string;
  ledgerEntryId: string | null;
};

type BadgeVariant =
  | "default"
  | "success"
  | "warning"
  | "outline"
  | "secondary"
  | "destructive"
  | "muted";

const TYPES = ["UPI_AUTOPAY", "NACH"] as const;
const FREQUENCIES = ["DAILY", "WEEKLY", "MONTHLY", "YEARLY", "AS_PRESENTED"] as const;
const STATUSES = ["CREATED", "ACTIVE", "PAUSED", "REVOKED"] as const;

const TYPE_LABEL: Record<string, string> = { UPI_AUTOPAY: "UPI AutoPay", NACH: "NACH" };
const FREQ_LABEL: Record<string, string> = {
  DAILY: "Daily",
  WEEKLY: "Weekly",
  MONTHLY: "Monthly",
  YEARLY: "Yearly",
  AS_PRESENTED: "As presented",
};

const STATUS_VARIANT: Record<string, BadgeVariant> = {
  CREATED: "secondary",
  ACTIVE: "success",
  PAUSED: "warning",
  REVOKED: "muted",
};

const EMPTY_FORM = {
  customerRef: "",
  type: "UPI_AUTOPAY",
  limit: "",
  currency: "INR",
  frequency: "MONTHLY",
  startDate: new Date().toISOString().slice(0, 10),
  endDate: "",
};

function MandatesPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [limitError, setLimitError] = useState<string | null>(null);
  const [detail, setDetail] = useState<MandateView | null>(null);
  const [debitAmount, setDebitAmount] = useState("");
  const [debitDesc, setDebitDesc] = useState("");

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const mandatesQ = useQuery({
    queryKey: ["mandates"],
    queryFn: () => api<MandateView[]>("/v1/mandates"),
    staleTime: 15_000,
  });

  const debitsQ = useQuery({
    queryKey: ["mandate-debits", detail?.id],
    queryFn: () => api<MandateDebitView[]>(`/v1/mandates/${detail!.id}/debits`),
    enabled: !!detail,
  });

  const createM = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      api<MandateView>("/v1/mandates", { method: "POST", body }),
    onSuccess: (m) => {
      toast.success(`Mandate for ${m.customerId ?? "customer"} created`);
      setOpen(false);
      setForm({ ...EMPTY_FORM });
      qc.invalidateQueries({ queryKey: ["mandates"] });
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Failed to create mandate"),
  });

  const actionM = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "activate" | "pause" | "revoke" }) =>
      api<MandateView>(`/v1/mandates/${id}/${action}`, { method: "POST" }),
    onSuccess: (m, vars) => {
      toast.success(`Mandate ${vars.action}d`);
      qc.invalidateQueries({ queryKey: ["mandates"] });
      if (detail?.id === m.id) setDetail(m);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Action failed"),
  });

  const debitM = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Record<string, unknown> }) =>
      api<MandateDebitView>(`/v1/mandates/${id}/debit`, { method: "POST", body }),
    onSuccess: (_d, vars) => {
      toast.success("Debit posted");
      setDebitAmount("");
      setDebitDesc("");
      qc.invalidateQueries({ queryKey: ["mandate-debits", vars.id] });
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Debit failed"),
  });

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const limitMinor = rupeesToMinor(form.limit);
    if (limitMinor === null || limitMinor <= 0) {
      setLimitError("Enter a valid limit greater than zero.");
      return;
    }
    setLimitError(null);
    createM.mutate({
      customerRef: form.customerRef.trim(),
      type: form.type,
      limitMinor,
      currency: form.currency.trim() || "INR",
      frequency: form.frequency,
      startDate: form.startDate,
      endDate: form.endDate || undefined,
    });
  }

  function submitDebit(e: React.FormEvent) {
    e.preventDefault();
    if (!detail) return;
    const amountMinor = rupeesToMinor(debitAmount);
    if (amountMinor === null || amountMinor <= 0) {
      toast.error("Enter a valid debit amount.");
      return;
    }
    debitM.mutate({
      id: detail.id,
      body: { amountMinor, description: debitDesc.trim() || undefined },
    });
  }

  const lv = useListView(mandatesQ.data ?? [], {
    searchFields: (m) => [m.id, m.customerId, m.type, m.frequency, m.providerMandateId],
    filterFields: { status: (m) => m.status, type: (m) => m.type, frequency: (m) => m.frequency },
    sortFields: {
      limitMinor: (m) => m.limitMinor,
      startDate: (m) => m.startDate,
      status: (m) => m.status,
    },
  });

  const columns: CsvColumn<MandateView>[] = [
    { header: "ID", value: (m) => m.id },
    { header: "Customer", value: (m) => m.customerId ?? "" },
    { header: "Type", value: (m) => m.type },
    { header: "Limit (minor)", value: (m) => m.limitMinor },
    { header: "Currency", value: (m) => m.currency },
    { header: "Frequency", value: (m) => m.frequency },
    { header: "Start", value: (m) => m.startDate },
    { header: "End", value: (m) => m.endDate ?? "" },
    { header: "Status", value: (m) => m.status },
  ];

  const pending = actionM.isPending;

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Recurring debit authorisations (UPI AutoPay / NACH) and their charges."
        actions={
          <Button onClick={() => setOpen(true)}>
            <PlusIcon /> Create mandate
          </Button>
        }
      />

      <Card className="gap-0 overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search mandates…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: STATUSES.map((s) => ({ label: s, value: s })),
              onChange: (v) => lv.setFilter("status", v),
            },
            {
              id: "type",
              label: "Type",
              value: lv.filters.type ?? "",
              options: TYPES.map((t) => ({ label: TYPE_LABEL[t], value: t })),
              onChange: (v) => lv.setFilter("type", v),
            },
            {
              id: "frequency",
              label: "Frequency",
              value: lv.filters.frequency ?? "",
              options: FREQUENCIES.map((f) => ({ label: FREQ_LABEL[f], value: f })),
              onChange: (v) => lv.setFilter("frequency", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("mandates", lv.view, columns)
              : exportToJson("mandates", lv.view)
          }
          exportDisabled={lv.view.length === 0}
        >
          <Button size="sm" onClick={() => setOpen(true)}>
            <PlusIcon /> New mandate
          </Button>
        </ListToolbar>

        <DataState
          isLoading={mandatesQ.isLoading}
          isError={mandatesQ.isError}
          error={mandatesQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={FileTextIcon}
          emptyTitle="No mandates"
          emptyDescription="Create a UPI AutoPay or NACH mandate to authorise recurring debits."
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Customer</TableHead>
                  <TableHead>Type</TableHead>
                  <SortHeader columnKey="limitMinor" sort={lv.sort} onToggle={lv.toggleSort}>
                    Limit
                  </SortHeader>
                  <TableHead>Frequency</TableHead>
                  <SortHeader columnKey="startDate" sort={lv.sort} onToggle={lv.toggleSort}>
                    Window
                  </SortHeader>
                  <SortHeader columnKey="status" sort={lv.sort} onToggle={lv.toggleSort}>
                    Status
                  </SortHeader>
                  <TableHead className="text-end">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((m) => (
                  <TableRow key={m.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                    <TableCell className="font-mono text-xs">{m.customerId ?? "—"}</TableCell>
                    <TableCell>{TYPE_LABEL[m.type] ?? m.type}</TableCell>
                    <TableCell className="tabular-nums">{formatInr(m.limitMinor, m.currency)}</TableCell>
                    <TableCell>{FREQ_LABEL[m.frequency] ?? m.frequency}</TableCell>
                    <TableCell className="text-xs text-muted-foreground tabular-nums">
                      {m.startDate}
                      {m.endDate ? ` → ${m.endDate}` : ""}
                    </TableCell>
                    <TableCell>
                      <Badge variant={STATUS_VARIANT[m.status] ?? "muted"}>{m.status}</Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap items-center justify-end gap-1.5">
                        {(m.status === "CREATED" || m.status === "PAUSED") && (
                          <Button
                            size="xs"
                            variant="outline"
                            disabled={pending}
                            onClick={() => actionM.mutate({ id: m.id, action: "activate" })}
                          >
                            <PlayIcon /> {m.status === "PAUSED" ? "Resume" : "Activate"}
                          </Button>
                        )}
                        {m.status === "ACTIVE" && (
                          <Button
                            size="xs"
                            variant="outline"
                            disabled={pending}
                            onClick={() => actionM.mutate({ id: m.id, action: "pause" })}
                          >
                            <PauseIcon /> Pause
                          </Button>
                        )}
                        <Button size="xs" variant="ghost" onClick={() => setDetail(m)}>
                          <ReceiptIcon /> Debits
                        </Button>
                        {m.status !== "REVOKED" && (
                          <Button
                            size="xs"
                            variant="destructive"
                            disabled={pending}
                            onClick={() => actionM.mutate({ id: m.id, action: "revoke" })}
                          >
                            <BanIcon /> Revoke
                          </Button>
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

      {/* Create mandate */}
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="w-full sm:max-w-md">
          <SheetHeader>
            <SheetTitle>Create mandate</SheetTitle>
            <SheetDescription>Authorise recurring debits for a customer.</SheetDescription>
          </SheetHeader>

          <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
            <div className="flex-1 space-y-4 overflow-y-auto px-4">
              <Field>
                <FieldLabel htmlFor="m-customer">Customer reference</FieldLabel>
                <Input
                  id="m-customer"
                  required
                  value={form.customerRef}
                  onChange={(e) => set("customerRef", e.target.value)}
                  placeholder="cust_abc123"
                />
              </Field>

              <div className="grid grid-cols-2 gap-3">
                <Field>
                  <FieldLabel>Type</FieldLabel>
                  <Select value={form.type} onValueChange={(v) => v && set("type", String(v))}>
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Type">
                        {(v) => TYPE_LABEL[String(v)] ?? "Type"}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      {TYPES.map((t) => (
                        <SelectItem key={t} value={t}>
                          {TYPE_LABEL[t]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </Field>
                <Field>
                  <FieldLabel>Frequency</FieldLabel>
                  <Select
                    value={form.frequency}
                    onValueChange={(v) => v && set("frequency", String(v))}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Frequency">
                        {(v) => FREQ_LABEL[String(v)] ?? "Frequency"}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      {FREQUENCIES.map((f) => (
                        <SelectItem key={f} value={f}>
                          {FREQ_LABEL[f]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </Field>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <Field>
                  <FieldLabel htmlFor="m-limit">Per-debit limit (₹)</FieldLabel>
                  <Input
                    id="m-limit"
                    required
                    inputMode="decimal"
                    value={form.limit}
                    onChange={(e) => set("limit", e.target.value)}
                    placeholder="5000.00"
                  />
                  {limitError && <FieldError errors={[{ message: limitError }]} />}
                </Field>
                <Field>
                  <FieldLabel htmlFor="m-currency">Currency</FieldLabel>
                  <Input
                    id="m-currency"
                    value={form.currency}
                    onChange={(e) => set("currency", e.target.value.toUpperCase())}
                    placeholder="INR"
                  />
                </Field>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <Field>
                  <FieldLabel htmlFor="m-start">Start date</FieldLabel>
                  <Input
                    id="m-start"
                    type="date"
                    required
                    value={form.startDate}
                    onChange={(e) => set("startDate", e.target.value)}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="m-end">End date (optional)</FieldLabel>
                  <Input
                    id="m-end"
                    type="date"
                    value={form.endDate}
                    onChange={(e) => set("endDate", e.target.value)}
                  />
                </Field>
              </div>
            </div>

            <SheetFooter className="flex-row justify-end gap-2 border-t">
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createM.isPending}>
                {createM.isPending ? "Creating…" : "Create mandate"}
              </Button>
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>

      {/* Debits detail */}
      <Dialog open={!!detail} onOpenChange={(o) => !o && setDetail(null)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Mandate debits</DialogTitle>
            <DialogDescription>
              {detail ? (
                <span className="font-mono text-xs">{detail.id}</span>
              ) : (
                "Charges posted against this mandate."
              )}
            </DialogDescription>
          </DialogHeader>

          <DataState
            isLoading={debitsQ.isLoading && !!detail}
            isError={debitsQ.isError}
            error={debitsQ.error}
            isEmpty={(debitsQ.data ?? []).length === 0}
            emptyIcon={ReceiptIcon}
            emptyTitle="No debits yet"
            emptyDescription="Charges against this mandate will appear here."
            skeletonRows={3}
          >
            <div className="max-h-64 overflow-y-auto rounded-lg border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Amount</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Ledger entry</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(debitsQ.data ?? []).map((d) => (
                    <TableRow key={d.id}>
                      <TableCell className="tabular-nums">
                        {formatInr(d.amountMinor, d.currency)}
                      </TableCell>
                      <TableCell>
                        <Badge variant="secondary">{d.status}</Badge>
                      </TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">
                        {d.ledgerEntryId ?? "—"}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </DataState>

          {detail?.status === "ACTIVE" && (
            <>
              <Separator />
              <form onSubmit={submitDebit} className="space-y-3">
                <p className="text-sm font-medium">Post a debit</p>
                <div className="grid grid-cols-2 gap-3">
                  <Field>
                    <FieldLabel htmlFor="d-amount">Amount (₹)</FieldLabel>
                    <Input
                      id="d-amount"
                      inputMode="decimal"
                      value={debitAmount}
                      onChange={(e) => setDebitAmount(e.target.value)}
                      placeholder="499.00"
                    />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="d-desc">Description</FieldLabel>
                    <Input
                      id="d-desc"
                      value={debitDesc}
                      onChange={(e) => setDebitDesc(e.target.value)}
                      placeholder="Monthly charge"
                    />
                  </Field>
                </div>
                <div className="flex justify-end">
                  <Button type="submit" size="sm" disabled={debitM.isPending}>
                    {debitM.isPending ? "Posting…" : "Post debit"}
                  </Button>
                </div>
              </form>
            </>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setDetail(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
