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
import { BanknoteIcon, PlusIcon, Trash2Icon, UsersIcon } from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/features/money/status";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/payout-batches")({ component: PayoutBatchesPage });

type PayoutRail = "UPI" | "IMPS" | "NEFT" | "RTGS";

type PayoutBatchSummary = {
  id: string;
  currency: string;
  status: string;
  totalCount: number;
  totalAmountMinor: number;
  paidCount: number;
  failedCount: number;
  description: string | null;
};

const RAILS: PayoutRail[] = ["UPI", "IMPS", "NEFT", "RTGS"];
const STATUSES = [
  "PENDING_APPROVAL",
  "COMPLETED",
  "PARTIALLY_COMPLETED",
  "FAILED",
  "REJECTED",
];

type LineDraft = { amount: string; rail: PayoutRail; destination: string; description: string };
const emptyLine = (): LineDraft => ({ amount: "", rail: "UPI", destination: "", description: "" });

function PayoutBatchesPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const batchesQ = useQuery({
    queryKey: ["payout-batches"],
    queryFn: () => api<PayoutBatchSummary[]>("/v1/payout-batches"),
  });

  const rows = batchesQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (b) => [b.id, b.description, b.status],
    filterFields: { status: (b) => b.status },
    sortFields: {
      total: (b) => b.totalAmountMinor,
      items: (b) => b.totalCount,
      status: (b) => b.status,
    },
  });

  // Create form state
  const [currency, setCurrency] = useState("INR");
  const [description, setDescription] = useState("");
  const [lines, setLines] = useState<LineDraft[]>([emptyLine()]);
  const [formError, setFormError] = useState<string | null>(null);

  const invalidate = () => qc.invalidateQueries({ queryKey: ["payout-batches"] });

  const createM = useMutation({
    mutationFn: (body: {
      currency: string;
      description?: string;
      payouts: { amountMinor: number; rail: PayoutRail; destination: string; description?: string }[];
    }) => api<PayoutBatchSummary>("/v1/payout-batches", { method: "POST", body }),
    meta: { successMessage: "Batch staged for approval" },
    onSuccess: () => {
      invalidate();
      setOpen(false);
      setDescription("");
      setLines([emptyLine()]);
      setFormError(null);
    },
  });

  const approveM = useMutation({
    mutationFn: (id: string) =>
      api<unknown>(`/v1/payout-batches/${id}/approve`, { method: "POST" }),
    meta: { successMessage: "Batch approved & disbursed" },
    onSuccess: invalidate,
  });

  const rejectM = useMutation({
    mutationFn: (id: string) =>
      api<unknown>(`/v1/payout-batches/${id}/reject`, { method: "POST" }),
    meta: { successMessage: "Batch rejected" },
    onSuccess: invalidate,
  });

  const draftTotalMinor = lines.reduce((s, l) => s + Math.max(0, rupeesToMinor(l.amount) ?? 0), 0);
  const busy = approveM.isPending || rejectM.isPending;

  function updateLine(i: number, patch: Partial<LineDraft>) {
    setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  }

  function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    const payouts = [];
    for (const l of lines) {
      const minor = rupeesToMinor(l.amount);
      if (minor === null || minor <= 0) {
        setFormError("Every line needs an amount greater than zero.");
        return;
      }
      if (!l.destination.trim()) {
        setFormError("Every line needs a beneficiary destination.");
        return;
      }
      payouts.push({
        amountMinor: minor,
        rail: l.rail,
        destination: l.destination.trim(),
        description: l.description.trim() || undefined,
      });
    }
    if (payouts.length === 0) {
      setFormError("Add at least one payout line.");
      return;
    }
    setFormError(null);
    createM.mutate({
      currency: currency.trim().toUpperCase() || "INR",
      description: description.trim() || undefined,
      payouts,
    });
  }

  const csvColumns = [
    { header: "Batch ID", value: (b: PayoutBatchSummary) => b.id },
    { header: "Status", value: (b: PayoutBatchSummary) => b.status },
    { header: "Currency", value: (b: PayoutBatchSummary) => b.currency },
    { header: "Items", value: (b: PayoutBatchSummary) => b.totalCount },
    { header: "Total (minor)", value: (b: PayoutBatchSummary) => b.totalAmountMinor },
    { header: "Paid", value: (b: PayoutBatchSummary) => b.paidCount },
    { header: "Failed", value: (b: PayoutBatchSummary) => b.failedCount },
    { header: "Description", value: (b: PayoutBatchSummary) => b.description },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Bulk disbursements with maker-checker: create a batch of payouts (all staged), then approve it as a unit to disburse every line at once."
        actions={
          <Button variant="outline" render={<Link to={"/payouts" as never} />}>
            <BanknoteIcon /> Single payout
          </Button>
        }
      />

      <Card className="gap-0 overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search batches…"
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
              ? exportToCsv("payout-batches", lv.view, csvColumns)
              : exportToJson("payout-batches", lv.view)
          }
          exportDisabled={lv.view.length === 0}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
        >
          <Button size="sm" onClick={() => setOpen(true)}>
            <PlusIcon /> Create batch
          </Button>
        </ListToolbar>

        <DataState
          isLoading={batchesQ.isLoading}
          isError={batchesQ.isError}
          error={batchesQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={UsersIcon}
          emptyTitle="No payout batches"
          emptyDescription="Create a batch to disburse many payouts at once."
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
                  <SortHeader columnKey="items" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Items
                  </SortHeader>
                  <SortHeader columnKey="total" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Total
                  </SortHeader>
                  <TableHead className="text-end">Paid / Failed</TableHead>
                  <TableHead>Description</TableHead>
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
                    <TableCell className="text-end tabular-nums">{b.totalCount}</TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(b.totalAmountMinor, b.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums text-muted-foreground">
                      {b.paidCount} / {b.failedCount}
                    </TableCell>
                    <TableCell className="max-w-48 truncate text-sm text-muted-foreground">
                      {b.description ?? "—"}
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
        <SheetContent side="right" className="w-full sm:max-w-xl">
          <form onSubmit={submitCreate} className="flex h-full flex-col">
            <SheetHeader>
              <SheetTitle>Create payout batch</SheetTitle>
              <SheetDescription>
                All lines are staged as PENDING_APPROVAL. Approve the batch to disburse every payout at
                once (maker-checker).
              </SheetDescription>
            </SheetHeader>

            <FieldGroup className="flex-1 overflow-y-auto p-4">
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
                <FieldLabel htmlFor="batch-desc">Description</FieldLabel>
                <Textarea
                  id="batch-desc"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Optional note for the batch (e.g. Vendor run — Jul)"
                  rows={2}
                />
              </Field>

              <Separator />

              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">Payout lines ({lines.length})</span>
                <span className="text-sm tabular-nums text-muted-foreground">
                  Total {formatInr(draftTotalMinor, currency.trim().toUpperCase() || "INR")}
                </span>
              </div>

              {lines.map((l, i) => (
                <div key={i} className="rounded-lg border p-3">
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-xs font-medium text-muted-foreground">Line {i + 1}</span>
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
                      <FieldLabel htmlFor={`amt-${i}`}>Amount</FieldLabel>
                      <Input
                        id={`amt-${i}`}
                        type="number"
                        min="0"
                        step="0.01"
                        inputMode="decimal"
                        value={l.amount}
                        onChange={(e) => updateLine(i, { amount: e.target.value })}
                        placeholder="0.00"
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
                    <Field className="sm:col-span-2">
                      <FieldLabel htmlFor={`dest-${i}`}>Beneficiary</FieldLabel>
                      <Input
                        id={`dest-${i}`}
                        value={l.destination}
                        onChange={(e) => updateLine(i, { destination: e.target.value })}
                        placeholder="name@upi  or  account@IFSC"
                      />
                    </Field>
                    <Field className="sm:col-span-2">
                      <FieldLabel htmlFor={`ldesc-${i}`}>Description</FieldLabel>
                      <Input
                        id={`ldesc-${i}`}
                        value={l.description}
                        onChange={(e) => updateLine(i, { description: e.target.value })}
                        placeholder="Optional"
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
                <PlusIcon /> Add line
              </Button>

              {formError && <FieldDescription className="text-destructive">{formError}</FieldDescription>}
            </FieldGroup>

            <SheetFooter>
              <Button type="submit" disabled={createM.isPending}>
                {createM.isPending ? "Creating…" : "Create batch"}
              </Button>
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>
    </div>
  );
}
