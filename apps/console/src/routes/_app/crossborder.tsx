import {
  Badge,
  Button,
  Card,
  CardContent,
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
  TimeSince,
  cn,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { GlobeIcon, PlusIcon, ReceiptIcon } from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/crossborder")({
  component: CrossBorderPage,
});

const FOREIGN_CURRENCIES = ["USD", "EUR", "GBP"] as const;

type Invoice = {
  id: string;
  invoiceNumber: string;
  buyerCountry: string;
  currency: string;
  foreignAmountMinor: number;
  purposeCode: string;
  lut: boolean;
  status: "ISSUED" | "REMITTED";
  createdAt: string;
};

type Remittance = {
  id: string;
  foreignAmountMinor: number;
  foreignCurrency: string;
  fxRate: number | string;
  inrAmountMinor: number;
  firaReference: string;
  purposeCode: string;
  ledgerEntryId: string;
  remittedAt: string;
};

type InvoiceView = { invoice: Invoice; remittances: Remittance[] };

function statusBadge(status: Invoice["status"]) {
  return status === "REMITTED" ? (
    <Badge variant="success">Remitted</Badge>
  ) : (
    <Badge variant="warning">Issued</Badge>
  );
}

function fmtRate(r: number | string): string {
  const n = typeof r === "number" ? r : Number(r);
  return Number.isFinite(n) ? n.toFixed(4) : String(r);
}

function errMsg(e: unknown): string {
  return e instanceof ApiError ? e.message : e instanceof Error ? e.message : "Something went wrong.";
}

function CrossBorderPage() {
  const qc = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);

  const [invoiceNumber, setInvoiceNumber] = useState("");
  const [buyerCountry, setBuyerCountry] = useState("");
  const [currency, setCurrency] = useState<string>("USD");
  const [amount, setAmount] = useState("");
  const [purposeCode, setPurposeCode] = useState("");
  const [lut, setLut] = useState(true);

  const listQ = useQuery({
    queryKey: ["export-invoices"],
    queryFn: () => api<Invoice[]>("/v1/crossborder/export-invoices"),
    staleTime: 15_000,
  });

  const createM = useMutation({
    mutationFn: (body: {
      invoiceNumber: string;
      buyerCountry: string;
      currency: string;
      foreignAmountMinor: number;
      purposeCode: string;
      lut: boolean;
    }) => api<InvoiceView>("/v1/crossborder/export-invoices", { method: "POST", body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["export-invoices"] });
      setCreateOpen(false);
      setInvoiceNumber("");
      setBuyerCountry("");
      setAmount("");
      setPurposeCode("");
      setLut(true);
    },
  });

  const rows = listQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (r) => [r.invoiceNumber, r.buyerCountry, r.purposeCode, r.currency],
    filterFields: {
      status: (r) => r.status,
      currency: (r) => r.currency,
    },
    sortFields: {
      invoiceNumber: (r) => r.invoiceNumber,
      foreignAmountMinor: (r) => r.foreignAmountMinor,
      status: (r) => r.status,
      createdAt: (r) => r.createdAt,
    },
  });

  const cell = (extra?: string) => cn(lv.density === "compact" ? "py-1.5" : "py-3", extra);
  const minor = rupeesToMinor(amount);
  const canCreate =
    invoiceNumber.trim() !== "" &&
    buyerCountry.trim() !== "" &&
    purposeCode.trim() !== "" &&
    minor !== null &&
    minor > 0;

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Raise foreign-currency export invoices (LUT / FEMA purpose code) and record inward remittances FX-converted to INR with a FIRA reference."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon /> Create export invoice
          </Button>
        }
      />

      <Card className="py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search invoice, country, purpose code…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: [
                { label: "Issued", value: "ISSUED" },
                { label: "Remitted", value: "REMITTED" },
              ],
              onChange: (v) => lv.setFilter("status", v),
            },
            {
              id: "currency",
              label: "Currency",
              value: lv.filters.currency ?? "",
              options: FOREIGN_CURRENCIES.map((c) => ({ label: c, value: c })),
              onChange: (v) => lv.setFilter("currency", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          exportDisabled={lv.view.length === 0}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("export-invoices", lv.view, [
                  { header: "Invoice", value: (r) => r.invoiceNumber },
                  { header: "Buyer Country", value: (r) => r.buyerCountry },
                  { header: "Currency", value: (r) => r.currency },
                  { header: "Foreign Amount (minor)", value: (r) => r.foreignAmountMinor },
                  { header: "Purpose Code", value: (r) => r.purposeCode },
                  { header: "LUT", value: (r) => r.lut },
                  { header: "Status", value: (r) => r.status },
                  { header: "Created", value: (r) => r.createdAt },
                ])
              : exportToJson("export-invoices", lv.view)
          }
        />

        <CardContent className="p-0">
          <DataState
            isLoading={listQ.isLoading}
            isError={listQ.isError}
            error={listQ.error}
            isEmpty={lv.view.length === 0}
            emptyIcon={GlobeIcon}
            emptyTitle="No export invoices"
            emptyDescription="Create a foreign-currency export invoice to track inward remittances and FIRA."
            skeletonRows={6}
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <SortHeader columnKey="invoiceNumber" sort={lv.sort} onToggle={lv.toggleSort}>
                    Invoice
                  </SortHeader>
                  <TableHead>Buyer</TableHead>
                  <SortHeader columnKey="foreignAmountMinor" sort={lv.sort} onToggle={lv.toggleSort} className="text-right">
                    Amount
                  </SortHeader>
                  <TableHead>Purpose</TableHead>
                  <TableHead>LUT</TableHead>
                  <SortHeader columnKey="status" sort={lv.sort} onToggle={lv.toggleSort}>
                    Status
                  </SortHeader>
                  <SortHeader columnKey="createdAt" sort={lv.sort} onToggle={lv.toggleSort}>
                    Created
                  </SortHeader>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className={cell("font-medium")}>{r.invoiceNumber}</TableCell>
                    <TableCell className={cell("uppercase text-muted-foreground")}>{r.buyerCountry}</TableCell>
                    <TableCell className={cell("text-right font-medium tabular-nums")}>
                      {formatInr(r.foreignAmountMinor, r.currency)}
                    </TableCell>
                    <TableCell className={cell("tabular-nums text-muted-foreground")}>{r.purposeCode}</TableCell>
                    <TableCell className={cell()}>
                      {r.lut ? <Badge variant="outline">LUT</Badge> : <span className="text-muted-foreground">—</span>}
                    </TableCell>
                    <TableCell className={cell()}>{statusBadge(r.status)}</TableCell>
                    <TableCell className={cell("text-muted-foreground")}>
                      <TimeSince value={r.createdAt} />
                    </TableCell>
                    <TableCell className={cell("text-right")}>
                      <Button variant="outline" size="sm" onClick={() => setDetailId(r.id)}>
                        Remittances
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </CardContent>
      </Card>

      {/* Create export invoice sheet */}
      <Sheet open={createOpen} onOpenChange={setCreateOpen}>
        <SheetContent side="right" className="sm:max-w-md">
          <form
            className="flex h-full flex-col"
            onSubmit={(e) => {
              e.preventDefault();
              if (canCreate && minor !== null) {
                createM.mutate({
                  invoiceNumber: invoiceNumber.trim(),
                  buyerCountry: buyerCountry.trim(),
                  currency,
                  foreignAmountMinor: minor,
                  purposeCode: purposeCode.trim(),
                  lut,
                });
              }
            }}
          >
            <SheetHeader>
              <SheetTitle>Create export invoice</SheetTitle>
              <SheetDescription>
                Raise a foreign-currency invoice under LUT / a FEMA purpose code. It settles when the
                inward remittance is recorded.
              </SheetDescription>
            </SheetHeader>
            <div className="flex-1 overflow-y-auto px-4">
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="inv-number">Invoice number</FieldLabel>
                  <Input
                    id="inv-number"
                    value={invoiceNumber}
                    onChange={(e) => setInvoiceNumber(e.target.value)}
                    placeholder="EXP-2026-0007"
                    autoFocus
                    required
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="inv-country">Buyer country</FieldLabel>
                  <Input
                    id="inv-country"
                    value={buyerCountry}
                    onChange={(e) => setBuyerCountry(e.target.value)}
                    placeholder="US"
                    required
                  />
                </Field>
                <div className="grid grid-cols-2 gap-3">
                  <Field>
                    <FieldLabel htmlFor="inv-currency">Currency</FieldLabel>
                    <Select value={currency} onValueChange={(v) => setCurrency(v as string)}>
                      <SelectTrigger id="inv-currency">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {FOREIGN_CURRENCIES.map((c) => (
                          <SelectItem key={c} value={c}>
                            {c}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="inv-amount">Amount ({currency})</FieldLabel>
                    <Input
                      id="inv-amount"
                      inputMode="decimal"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      placeholder="10000.00"
                    />
                  </Field>
                </div>
                <Field>
                  <FieldLabel htmlFor="inv-purpose">FEMA purpose code</FieldLabel>
                  <Input
                    id="inv-purpose"
                    value={purposeCode}
                    onChange={(e) => setPurposeCode(e.target.value)}
                    placeholder="P0802"
                    required
                  />
                  <FieldDescription>RBI purpose-of-remittance code for this export.</FieldDescription>
                </Field>
                <Field>
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-input accent-primary"
                      checked={lut}
                      onChange={(e) => setLut(e.target.checked)}
                    />
                    Exported under LUT (no IGST)
                  </label>
                </Field>
              </FieldGroup>
              {createM.isError && <p className="mt-3 text-sm text-destructive">{errMsg(createM.error)}</p>}
            </div>
            <SheetFooter>
              <div className="flex justify-end gap-2">
                <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
                <Button type="submit" disabled={!canCreate || createM.isPending}>
                  {createM.isPending ? "Creating…" : "Create invoice"}
                </Button>
              </div>
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>

      {/* Detail + remittances sheet */}
      <Sheet open={detailId !== null} onOpenChange={(o) => !o && setDetailId(null)}>
        <SheetContent side="right" className="sm:max-w-lg">
          {detailId && <InvoiceDetail invoiceId={detailId} />}
        </SheetContent>
      </Sheet>
    </div>
  );
}

function InvoiceDetail({ invoiceId }: { invoiceId: string }) {
  const qc = useQueryClient();
  const [amount, setAmount] = useState("");
  const [fira, setFira] = useState("");

  const detailQ = useQuery({
    queryKey: ["export-invoice", invoiceId],
    queryFn: () => api<InvoiceView>(`/v1/crossborder/export-invoices/${invoiceId}`),
  });

  const remitM = useMutation({
    mutationFn: (body: { foreignAmountMinor: number; firaReference: string }) =>
      api<Remittance>(`/v1/crossborder/export-invoices/${invoiceId}/remittances`, {
        method: "POST",
        body,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["export-invoice", invoiceId] });
      qc.invalidateQueries({ queryKey: ["export-invoices"] });
      setAmount("");
      setFira("");
    },
  });

  const invoice = detailQ.data?.invoice;
  const remittances = detailQ.data?.remittances ?? [];
  const minor = rupeesToMinor(amount);
  const canRemit = minor !== null && minor > 0 && fira.trim() !== "" && invoice?.status === "ISSUED";

  return (
    <div className="flex h-full flex-col">
      <SheetHeader>
        <SheetTitle className="flex items-center gap-2">
          <ReceiptIcon className="size-4 text-muted-foreground" />
          {invoice?.invoiceNumber ?? "Export invoice"}
        </SheetTitle>
        <SheetDescription>
          {invoice
            ? `${invoice.buyerCountry.toUpperCase()} · ${formatInr(invoice.foreignAmountMinor, invoice.currency)} · ${invoice.purposeCode}`
            : "Loading invoice…"}
        </SheetDescription>
      </SheetHeader>

      <div className="flex-1 space-y-5 overflow-y-auto px-4">
        <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error} skeletonRows={4}>
          <div>{invoice && statusBadge(invoice.status)}</div>

          <div>
            <h3 className="mb-2 text-sm font-medium">Remittances ({remittances.length})</h3>
            {remittances.length === 0 ? (
              <p className="rounded-md bg-muted/50 p-3 text-sm text-muted-foreground">
                No inward remittance recorded yet.
              </p>
            ) : (
              <div className="space-y-3">
                {remittances.map((r) => (
                  <div key={r.id} className="rounded-lg border p-3 text-sm">
                    <div className="flex items-center justify-between">
                      <span className="font-medium tabular-nums">
                        {formatInr(r.foreignAmountMinor, r.foreignCurrency)}
                      </span>
                      <span className="text-muted-foreground">
                        <TimeSince value={r.remittedAt} />
                      </span>
                    </div>
                    <Separator className="my-2" />
                    <dl className="grid grid-cols-2 gap-1.5 text-xs">
                      <dt className="text-muted-foreground">INR credited</dt>
                      <dd className="text-right font-medium tabular-nums">{formatInr(r.inrAmountMinor)}</dd>
                      <dt className="text-muted-foreground">FX rate</dt>
                      <dd className="text-right tabular-nums">{fmtRate(r.fxRate)}</dd>
                      <dt className="text-muted-foreground">FIRA reference</dt>
                      <dd className="text-right tabular-nums">{r.firaReference}</dd>
                    </dl>
                  </div>
                ))}
              </div>
            )}
          </div>
        </DataState>
      </div>

      {invoice?.status === "ISSUED" && (
        <SheetFooter>
          <Separator className="mb-1" />
          <p className="text-sm font-medium">Record inward remittance</p>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              if (canRemit && minor !== null) {
                remitM.mutate({ foreignAmountMinor: minor, firaReference: fira.trim() });
              }
            }}
          >
            <Field>
              <FieldLabel htmlFor="rm-amount">Amount ({invoice.currency})</FieldLabel>
              <Input
                id="rm-amount"
                inputMode="decimal"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="10000.00"
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="rm-fira">FIRA reference</FieldLabel>
              <Input
                id="rm-fira"
                value={fira}
                onChange={(e) => setFira(e.target.value)}
                placeholder="FIRA-2026-0007"
              />
            </Field>
            {remitM.isError && <p className="text-sm text-destructive">{errMsg(remitM.error)}</p>}
            <Button type="submit" className="w-full" disabled={!canRemit || remitM.isPending}>
              {remitM.isPending ? "Recording…" : "Record remittance"}
            </Button>
          </form>
        </SheetFooter>
      )}
    </div>
  );
}
