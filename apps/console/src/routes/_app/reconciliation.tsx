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
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { FileCheck2Icon, PlusIcon, ScaleIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { StatusBadge, statusVariant } from "@/features/money/status";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/reconciliation")({ component: ReconciliationPage });

type DiscrepancyView = {
  type: string;
  paymentId: string | null;
  providerPaymentId: string | null;
  expectedMinor: number | null;
  reportedMinor: number | null;
  detail: string | null;
};

type ReconciliationView = {
  id: string;
  status: string;
  matchedCount: number;
  discrepancyCount: number;
  discrepancies: DiscrepancyView[];
};

type SettlementView = {
  id: string;
  provider: string;
  providerSettlementId: string;
  currency: string;
  grossAmountMinor: number;
  feeAmountMinor: number;
  taxAmountMinor: number;
  netAmountMinor: number;
  itemCount: number;
  status: string;
  ledgerEntryId: string | null;
  reconciliation: ReconciliationView | null;
};

const STATUSES = ["RECEIVED", "RECONCILED", "DISCREPANCY"];

type ItemDraft = {
  paymentId: string;
  providerPaymentId: string;
  gross: string;
  fee: string;
  tax: string;
};
const emptyItem = (): ItemDraft => ({ paymentId: "", providerPaymentId: "", gross: "", fee: "", tax: "" });

function ReconciliationPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<SettlementView | null>(null);

  const settlementsQ = useQuery({
    queryKey: ["settlements"],
    queryFn: () => api<SettlementView[]>("/v1/settlements"),
  });

  const rows = settlementsQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (s) => [s.id, s.provider, s.providerSettlementId, s.status],
    filterFields: { status: (s) => s.status },
    sortFields: {
      gross: (s) => s.grossAmountMinor,
      net: (s) => s.netAmountMinor,
      items: (s) => s.itemCount,
      status: (s) => s.status,
    },
  });

  // Ingest form state
  const [provider, setProvider] = useState("razorpay");
  const [providerSettlementId, setProviderSettlementId] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [settledAt, setSettledAt] = useState("");
  const [reportedNet, setReportedNet] = useState("");
  const [items, setItems] = useState<ItemDraft[]>([emptyItem()]);
  const [formError, setFormError] = useState<string | null>(null);

  const invalidate = () => qc.invalidateQueries({ queryKey: ["settlements"] });

  const ingestM = useMutation({
    mutationFn: (body: unknown) => api<SettlementView>("/v1/settlements", { method: "POST", body }),
    meta: { successMessage: "Settlement ingested & reconciled" },
    onSuccess: () => {
      invalidate();
      setOpen(false);
      setProviderSettlementId("");
      setSettledAt("");
      setReportedNet("");
      setItems([emptyItem()]);
      setFormError(null);
    },
  });

  function updateItem(i: number, patch: Partial<ItemDraft>) {
    setItems((prev) => prev.map((it, idx) => (idx === i ? { ...it, ...patch } : it)));
  }

  function submitIngest(e: React.FormEvent) {
    e.preventDefault();
    if (!provider.trim() || !providerSettlementId.trim()) {
      setFormError("Provider and provider settlement ID are required.");
      return;
    }
    const built = [];
    for (const it of items) {
      const gross = rupeesToMinor(it.gross);
      if (gross === null || gross <= 0) {
        setFormError("Every line needs a gross amount greater than zero.");
        return;
      }
      built.push({
        paymentId: it.paymentId.trim() || undefined,
        providerPaymentId: it.providerPaymentId.trim() || undefined,
        grossMinor: gross,
        feeMinor: rupeesToMinor(it.fee) ?? 0,
        taxMinor: rupeesToMinor(it.tax) ?? 0,
      });
    }
    if (built.length === 0) {
      setFormError("Add at least one settlement line.");
      return;
    }
    setFormError(null);
    const reportedNetMinor = reportedNet.trim() ? rupeesToMinor(reportedNet) : undefined;
    ingestM.mutate({
      provider: provider.trim(),
      providerSettlementId: providerSettlementId.trim(),
      currency: currency.trim().toUpperCase() || "INR",
      settledAt: settledAt ? new Date(settledAt).toISOString() : undefined,
      reportedNetMinor,
      items: built,
    });
  }

  const csvColumns = [
    { header: "Settlement ID", value: (s: SettlementView) => s.id },
    { header: "Provider", value: (s: SettlementView) => s.provider },
    { header: "Provider Settlement ID", value: (s: SettlementView) => s.providerSettlementId },
    { header: "Currency", value: (s: SettlementView) => s.currency },
    { header: "Gross (minor)", value: (s: SettlementView) => s.grossAmountMinor },
    { header: "Fee (minor)", value: (s: SettlementView) => s.feeAmountMinor },
    { header: "Tax (minor)", value: (s: SettlementView) => s.taxAmountMinor },
    { header: "Net (minor)", value: (s: SettlementView) => s.netAmountMinor },
    { header: "Items", value: (s: SettlementView) => s.itemCount },
    { header: "Status", value: (s: SettlementView) => s.status },
    { header: "Discrepancies", value: (s: SettlementView) => s.reconciliation?.discrepancyCount ?? 0 },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Ingest provider settlement reports — each posts the money movement to the ledger and reconciles every line against captured payments, flagging any discrepancies."
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
          searchPlaceholder="Search settlements…"
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
              ? exportToCsv("settlements", lv.view, csvColumns)
              : exportToJson("settlements", lv.view)
          }
          exportDisabled={lv.view.length === 0}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
        >
          <Button size="sm" onClick={() => setOpen(true)}>
            <PlusIcon /> Ingest settlement
          </Button>
        </ListToolbar>

        <DataState
          isLoading={settlementsQ.isLoading}
          isError={settlementsQ.isError}
          error={settlementsQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={FileCheck2Icon}
          emptyTitle="No settlements"
          emptyDescription="Ingest a provider settlement report to reconcile it against captured payments."
          className="p-6"
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Settlement</TableHead>
                  <TableHead>Provider</TableHead>
                  <SortHeader columnKey="gross" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Gross
                  </SortHeader>
                  <TableHead className="text-end">Fee</TableHead>
                  <SortHeader columnKey="net" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Net
                  </SortHeader>
                  <SortHeader columnKey="items" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Items
                  </SortHeader>
                  <SortHeader columnKey="status" sort={lv.sort} onToggle={lv.toggleSort}>
                    Status
                  </SortHeader>
                  <TableHead>Reconciliation</TableHead>
                  <TableHead className="text-end">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((s) => (
                  <TableRow key={s.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                    <TableCell className="font-mono text-xs">{s.providerSettlementId}</TableCell>
                    <TableCell>{s.provider}</TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(s.grossAmountMinor, s.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums text-muted-foreground">
                      {formatInr(s.feeAmountMinor, s.currency)}
                    </TableCell>
                    <TableCell className="text-end font-medium tabular-nums">
                      {formatInr(s.netAmountMinor, s.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">{s.itemCount}</TableCell>
                    <TableCell>
                      <StatusBadge status={s.status} />
                    </TableCell>
                    <TableCell>
                      {s.reconciliation ? (
                        <div className="flex items-center gap-2">
                          <StatusBadge status={s.reconciliation.status} />
                          {s.reconciliation.discrepancyCount > 0 && (
                            <span className="text-xs text-muted-foreground">
                              {s.reconciliation.discrepancyCount} flagged
                            </span>
                          )}
                        </div>
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end">
                        <Button size="sm" variant="outline" onClick={() => setSelected(s)}>
                          View
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </DataState>
      </Card>

      {/* Ingest settlement */}
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="w-full sm:max-w-xl">
          <form onSubmit={submitIngest} className="flex h-full flex-col">
            <SheetHeader>
              <SheetTitle>Ingest settlement report</SheetTitle>
              <SheetDescription>
                Records a provider settlement, posts the money movement to the ledger, and reconciles
                each line against captured payments.
              </SheetDescription>
            </SheetHeader>

            <FieldGroup className="flex-1 overflow-y-auto p-4">
              <div className="grid gap-2 sm:grid-cols-2">
                <Field>
                  <FieldLabel htmlFor="provider">Provider</FieldLabel>
                  <Input
                    id="provider"
                    value={provider}
                    onChange={(e) => setProvider(e.target.value)}
                    placeholder="razorpay"
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="psid">Provider settlement ID</FieldLabel>
                  <Input
                    id="psid"
                    value={providerSettlementId}
                    onChange={(e) => setProviderSettlementId(e.target.value)}
                    placeholder="setl_..."
                  />
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
                  <FieldLabel htmlFor="settledAt">Settled at</FieldLabel>
                  <Input
                    id="settledAt"
                    type="datetime-local"
                    value={settledAt}
                    onChange={(e) => setSettledAt(e.target.value)}
                  />
                </Field>
              </div>

              <Field>
                <FieldLabel htmlFor="reportedNet">Reported net (control total)</FieldLabel>
                <Input
                  id="reportedNet"
                  type="number"
                  min="0"
                  step="0.01"
                  inputMode="decimal"
                  value={reportedNet}
                  onChange={(e) => setReportedNet(e.target.value)}
                  placeholder="Optional — flags a BATCH_TOTAL_MISMATCH if it disagrees"
                />
                <FieldDescription>In rupees; optional. Sent to the API as paise.</FieldDescription>
              </Field>

              <Separator />

              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">Settlement lines ({items.length})</span>
              </div>

              {items.map((it, i) => (
                <div key={i} className="rounded-lg border p-3">
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-xs font-medium text-muted-foreground">Line {i + 1}</span>
                    <Button
                      type="button"
                      size="icon-sm"
                      variant="ghost"
                      aria-label="Remove line"
                      disabled={items.length === 1}
                      onClick={() => setItems((prev) => prev.filter((_, idx) => idx !== i))}
                    >
                      <Trash2Icon />
                    </Button>
                  </div>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <Field className="sm:col-span-2">
                      <FieldLabel htmlFor={`pid-${i}`}>Payment ID (UUID)</FieldLabel>
                      <Input
                        id={`pid-${i}`}
                        value={it.paymentId}
                        onChange={(e) => updateItem(i, { paymentId: e.target.value })}
                        placeholder="Optional — our captured payment id"
                      />
                    </Field>
                    <Field className="sm:col-span-2">
                      <FieldLabel htmlFor={`ppid-${i}`}>Provider payment ID</FieldLabel>
                      <Input
                        id={`ppid-${i}`}
                        value={it.providerPaymentId}
                        onChange={(e) => updateItem(i, { providerPaymentId: e.target.value })}
                        placeholder="pay_..."
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
                        value={it.gross}
                        onChange={(e) => updateItem(i, { gross: e.target.value })}
                        placeholder="0.00"
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor={`fee-${i}`}>Fee</FieldLabel>
                      <Input
                        id={`fee-${i}`}
                        type="number"
                        min="0"
                        step="0.01"
                        inputMode="decimal"
                        value={it.fee}
                        onChange={(e) => updateItem(i, { fee: e.target.value })}
                        placeholder="0.00"
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor={`tax-${i}`}>Tax</FieldLabel>
                      <Input
                        id={`tax-${i}`}
                        type="number"
                        min="0"
                        step="0.01"
                        inputMode="decimal"
                        value={it.tax}
                        onChange={(e) => updateItem(i, { tax: e.target.value })}
                        placeholder="0.00"
                      />
                    </Field>
                  </div>
                </div>
              ))}

              <Button
                type="button"
                variant="outline"
                onClick={() => setItems((prev) => [...prev, emptyItem()])}
              >
                <PlusIcon /> Add line
              </Button>

              {formError && <FieldDescription className="text-destructive">{formError}</FieldDescription>}
            </FieldGroup>

            <SheetFooter>
              <Button type="submit" disabled={ingestM.isPending}>
                {ingestM.isPending ? "Ingesting…" : "Ingest settlement"}
              </Button>
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>

      {/* Discrepancy viewer */}
      <Sheet open={selected !== null} onOpenChange={(o) => !o && setSelected(null)}>
        <SheetContent side="right" className="w-full sm:max-w-xl">
          <SheetHeader>
            <SheetTitle>Settlement {selected?.providerSettlementId}</SheetTitle>
            <SheetDescription>
              {selected?.provider} · {selected?.itemCount} items · {selected?.status}
            </SheetDescription>
          </SheetHeader>

          {selected && (
            <div className="flex-1 space-y-4 overflow-y-auto p-4">
              <div className="grid grid-cols-2 gap-2 text-sm">
                <div className="flex justify-between rounded-md bg-muted/50 px-3 py-2">
                  <span className="text-muted-foreground">Gross</span>
                  <span className="font-medium tabular-nums">
                    {formatInr(selected.grossAmountMinor, selected.currency)}
                  </span>
                </div>
                <div className="flex justify-between rounded-md bg-muted/50 px-3 py-2">
                  <span className="text-muted-foreground">Fee</span>
                  <span className="font-medium tabular-nums">
                    {formatInr(selected.feeAmountMinor, selected.currency)}
                  </span>
                </div>
                <div className="flex justify-between rounded-md bg-muted/50 px-3 py-2">
                  <span className="text-muted-foreground">Tax</span>
                  <span className="font-medium tabular-nums">
                    {formatInr(selected.taxAmountMinor, selected.currency)}
                  </span>
                </div>
                <div className="flex justify-between rounded-md bg-muted/50 px-3 py-2">
                  <span className="text-muted-foreground">Net</span>
                  <span className="font-semibold tabular-nums">
                    {formatInr(selected.netAmountMinor, selected.currency)}
                  </span>
                </div>
              </div>

              {selected.ledgerEntryId && (
                <p className="text-xs text-muted-foreground">
                  Ledger entry: <span className="font-mono">{selected.ledgerEntryId}</span>
                </p>
              )}

              <Separator />

              {selected.reconciliation ? (
                <div className="space-y-3">
                  <div className="flex items-center gap-2">
                    <StatusBadge status={selected.reconciliation.status} />
                    <span className="text-sm text-muted-foreground">
                      {selected.reconciliation.matchedCount} matched ·{" "}
                      {selected.reconciliation.discrepancyCount} discrepancies
                    </span>
                  </div>

                  {selected.reconciliation.discrepancies.length === 0 ? (
                    <p className="text-sm text-muted-foreground">
                      No discrepancies — every line matched a captured payment.
                    </p>
                  ) : (
                    <div className="space-y-2">
                      {selected.reconciliation.discrepancies.map((d, i) => (
                        <div key={i} className="rounded-lg border p-3">
                          <Badge variant={statusVariant(d.type)}>{d.type}</Badge>
                          {d.detail && <p className="mt-2 text-sm">{d.detail}</p>}
                          <div className="mt-2 flex flex-wrap gap-x-6 gap-y-1 text-xs text-muted-foreground">
                            {d.providerPaymentId && (
                              <span>
                                Provider: <span className="font-mono">{d.providerPaymentId}</span>
                              </span>
                            )}
                            {d.paymentId && (
                              <span>
                                Payment: <span className="font-mono">{d.paymentId}</span>
                              </span>
                            )}
                            {d.expectedMinor != null && (
                              <span>Expected: {formatInr(d.expectedMinor, selected.currency)}</span>
                            )}
                            {d.reportedMinor != null && (
                              <span>Reported: {formatInr(d.reportedMinor, selected.currency)}</span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No reconciliation run recorded.</p>
              )}
            </div>
          )}

          <SheetFooter>
            <SheetClose render={<Button type="button" variant="outline">Close</Button>} />
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </div>
  );
}
