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
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  TimeSince,
  cn,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
  ArrowDownLeftIcon,
  ArrowUpRightIcon,
  GlobeIcon,
  PlusIcon,
  ReceiptIcon,
  SendIcon,
} from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { DetailRow, FormError, MoneyField, TextField } from "@/features/finance/shared";
import { FormSheet } from "@/features/gst/ui";
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
  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Foreign-currency cross-border flows — inbound export receipts (LUT / FEMA purpose code + FIRA) and outbound import remittances (SWIFT + LRS tracking + 2.5% TCS)." />
      <Tabs defaultValue="inbound">
        <TabsList>
          <TabsTrigger value="inbound">
            <ArrowDownLeftIcon /> Inbound (export)
          </TabsTrigger>
          <TabsTrigger value="outbound">
            <ArrowUpRightIcon /> Outbound (import)
          </TabsTrigger>
        </TabsList>
        <TabsContent value="inbound" className="mt-4">
          <InboundTab />
        </TabsContent>
        <TabsContent value="outbound" className="mt-4">
          <OutboundTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function InboundTab() {
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
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Button onClick={() => setCreateOpen(true)}>
          <PlusIcon /> Create export invoice
        </Button>
      </div>

      <Card className="overflow-hidden py-0">
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

// ── Outbound (import) ─────────────────────────────────────────────────────────

type OutboundStatus = "INITIATED" | "REMITTED" | "FAILED";

type OutRemittance = {
  id: string;
  beneficiaryName: string;
  beneficiarySwift: string;
  beneficiaryCountry: string;
  purposeCode: string;
  currency: string;
  foreignAmountMinor: number;
  fxRate: number | string;
  principalInrMinor: number;
  tcsMinor: number;
  inrDebitedMinor: number;
  financialYear: string;
  lrsCumulativeAfterMinor: number;
  status: OutboundStatus;
  ledgerEntryId: string;
  remittanceReference: string | null;
  createdAt: string;
};

type OutQuote = {
  currency: string;
  foreignAmountMinor: number;
  fxRate: number | string;
  principalInrMinor: number;
  financialYear: string;
  lrsCumulativeBeforeMinor: number;
  lrsThresholdMinor: number;
  tcsMinor: number;
  tcsBps: number;
  inrDebitedMinor: number;
};

type OutEvent = { id: string; type: string; amountMinor: number; note: string | null; createdAt: string };
type OutDetail = { remittance: OutRemittance; events: OutEvent[] };

function outStatusBadge(status: OutboundStatus) {
  if (status === "REMITTED") return <Badge variant="success">Remitted</Badge>;
  if (status === "FAILED") return <Badge variant="destructive">Failed</Badge>;
  return <Badge variant="warning">Initiated</Badge>;
}

function OutboundTab() {
  const [createOpen, setCreateOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);

  const listQ = useQuery({
    queryKey: ["outbound-remittances"],
    queryFn: () => api<OutRemittance[]>("/v1/crossborder/outbound"),
    staleTime: 15_000,
  });

  const rows = listQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (r) => [r.beneficiaryName, r.beneficiarySwift, r.beneficiaryCountry, r.purposeCode, r.currency],
    filterFields: { status: (r) => r.status, currency: (r) => r.currency },
    sortFields: { debited: (r) => r.inrDebitedMinor, createdAt: (r) => r.createdAt },
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Button onClick={() => setCreateOpen(true)}>
          <SendIcon /> New remittance
        </Button>
      </div>

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search beneficiary, SWIFT, purpose…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: [
                { label: "Initiated", value: "INITIATED" },
                { label: "Remitted", value: "REMITTED" },
                { label: "Failed", value: "FAILED" },
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
              ? exportToCsv("outbound-remittances", lv.view, [
                  { header: "Beneficiary", value: (r) => r.beneficiaryName },
                  { header: "SWIFT", value: (r) => r.beneficiarySwift },
                  { header: "Country", value: (r) => r.beneficiaryCountry },
                  { header: "Currency", value: (r) => r.currency },
                  { header: "Foreign amount (minor)", value: (r) => r.foreignAmountMinor },
                  { header: "INR debited (minor)", value: (r) => r.inrDebitedMinor },
                  { header: "TCS (minor)", value: (r) => r.tcsMinor },
                  { header: "Status", value: (r) => r.status },
                ])
              : exportToJson("outbound-remittances", lv.view)
          }
        />
        <CardContent className="p-0">
          <DataState
            isLoading={listQ.isLoading}
            isError={listQ.isError}
            error={listQ.error}
            isEmpty={lv.view.length === 0}
            emptyIcon={GlobeIcon}
            emptyTitle="No outbound remittances"
            emptyDescription="Pay a foreign vendor via SWIFT; LRS usage and TCS are tracked per financial year."
            skeletonRows={5}
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Beneficiary</TableHead>
                  <TableHead className="text-right">Foreign amount</TableHead>
                  <TableHead className="text-right">INR debited</TableHead>
                  <TableHead className="text-right">TCS</TableHead>
                  <TableHead>Status</TableHead>
                  <SortHeader columnKey="createdAt" sort={lv.sort} onToggle={lv.toggleSort}>
                    Created
                  </SortHeader>
                  <TableHead className="text-right">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell>
                      <div className="font-medium">{r.beneficiaryName}</div>
                      <div className="font-mono text-xs text-muted-foreground">
                        {r.beneficiarySwift} · {r.beneficiaryCountry.toUpperCase()}
                      </div>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{formatInr(r.foreignAmountMinor, r.currency)}</TableCell>
                    <TableCell className="text-right font-medium tabular-nums">{formatInr(r.inrDebitedMinor)}</TableCell>
                    <TableCell className="text-right tabular-nums text-muted-foreground">{formatInr(r.tcsMinor)}</TableCell>
                    <TableCell>{outStatusBadge(r.status)}</TableCell>
                    <TableCell className="text-muted-foreground">
                      <TimeSince value={r.createdAt} />
                    </TableCell>
                    <TableCell className="text-right">
                      <Button variant="outline" size="sm" onClick={() => setDetailId(r.id)}>
                        Manage
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </CardContent>
      </Card>

      <CreateRemittanceSheet open={createOpen} onOpenChange={setCreateOpen} />
      <RemittanceDetailSheet remittanceId={detailId} onClose={() => setDetailId(null)} />
    </div>
  );
}

function CreateRemittanceSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const [beneficiaryName, setBeneficiaryName] = useState("");
  const [beneficiarySwift, setBeneficiarySwift] = useState("");
  const [beneficiaryAccount, setBeneficiaryAccount] = useState("");
  const [beneficiaryCountry, setBeneficiaryCountry] = useState("");
  const [currency, setCurrency] = useState("USD");
  const [amount, setAmount] = useState("");
  const [purposeCode, setPurposeCode] = useState("");
  const [quote, setQuote] = useState<OutQuote | null>(null);

  const minor = rupeesToMinor(amount);

  const quoteM = useMutation({
    mutationFn: () =>
      api<OutQuote>("/v1/crossborder/outbound/quote", {
        method: "POST",
        body: { currency, foreignAmountMinor: minor },
      }),
    onSuccess: setQuote,
  });

  const createM = useMutation({
    mutationFn: () =>
      api<OutRemittance>("/v1/crossborder/outbound", {
        method: "POST",
        body: {
          beneficiaryName: beneficiaryName.trim(),
          beneficiarySwift: beneficiarySwift.trim(),
          beneficiaryAccount: beneficiaryAccount.trim(),
          beneficiaryCountry: beneficiaryCountry.trim(),
          currency,
          foreignAmountMinor: minor,
          purposeCode: purposeCode.trim(),
        },
      }),
    meta: { successMessage: "Remittance initiated" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["outbound-remittances"] });
      onOpenChange(false);
      setBeneficiaryName("");
      setBeneficiarySwift("");
      setBeneficiaryAccount("");
      setBeneficiaryCountry("");
      setAmount("");
      setPurposeCode("");
      setQuote(null);
    },
  });

  const valid =
    beneficiaryName.trim() !== "" &&
    beneficiarySwift.trim() !== "" &&
    beneficiaryAccount.trim() !== "" &&
    beneficiaryCountry.trim() !== "" &&
    purposeCode.trim() !== "" &&
    minor !== null &&
    minor > 0;

  return (
    <FormSheet
      open={open}
      onOpenChange={onOpenChange}
      title="New outbound remittance"
      description="Pay a foreign vendor via SWIFT. FX conversion, LRS financial-year usage, and 2.5% TCS above the LRS threshold are computed on quote."
      submitLabel="Initiate remittance"
      submitting={createM.isPending}
      disabled={!valid}
      onSubmit={() => createM.mutate()}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="ob-name" label="Beneficiary name" value={beneficiaryName} onChange={setBeneficiaryName} placeholder="Acme Cloud Inc" required />
        <TextField id="ob-country" label="Country" value={beneficiaryCountry} onChange={setBeneficiaryCountry} placeholder="US" required />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="ob-swift" label="SWIFT / BIC" value={beneficiarySwift} onChange={setBeneficiarySwift} placeholder="CHASUS33" required />
        <TextField id="ob-account" label="Account" value={beneficiaryAccount} onChange={setBeneficiaryAccount} placeholder="000123456789" required />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label className="mb-1.5 block text-sm font-medium">Currency</label>
          <Select value={currency} onValueChange={(v) => setCurrency(v as string)}>
            <SelectTrigger className="w-full">
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
        </div>
        <MoneyField id="ob-amount" label={`Amount (${currency})`} value={amount} onChange={setAmount} required />
      </div>
      <TextField id="ob-purpose" label="FEMA purpose code" value={purposeCode} onChange={setPurposeCode} placeholder="S0301" required />

      <div className="flex items-center gap-2">
        <Button type="button" variant="outline" size="sm" disabled={minor === null || minor <= 0 || quoteM.isPending} onClick={() => quoteM.mutate()}>
          {quoteM.isPending ? "Quoting…" : "Preview quote"}
        </Button>
        {quote && (
          <span className="text-xs text-muted-foreground">
            FX {fmtRate(quote.fxRate)} · FY {quote.financialYear}
          </span>
        )}
      </div>

      {quote && (
        <div className="rounded-lg border p-3">
          <DetailRow label="Principal (INR)" value={formatInr(quote.principalInrMinor)} />
          <DetailRow label={`TCS (${(quote.tcsBps / 100).toFixed(2)}%)`} value={formatInr(quote.tcsMinor)} />
          <DetailRow label="INR debited" value={<span className="font-semibold">{formatInr(quote.inrDebitedMinor)}</span>} />
          <DetailRow label="LRS used (FY)" value={`${formatInr(quote.lrsCumulativeBeforeMinor)} / ${formatInr(quote.lrsThresholdMinor)}`} />
        </div>
      )}

      <FormError error={createM.error ?? quoteM.error} />
    </FormSheet>
  );
}

function RemittanceDetailSheet({ remittanceId, onClose }: { remittanceId: string | null; onClose: () => void }) {
  const qc = useQueryClient();
  const [reference, setReference] = useState("");

  const detailQ = useQuery({
    queryKey: ["outbound-remittance", remittanceId],
    queryFn: () => api<OutDetail>(`/v1/crossborder/outbound/${remittanceId}`),
    enabled: remittanceId !== null,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["outbound-remittance", remittanceId] });
    qc.invalidateQueries({ queryKey: ["outbound-remittances"] });
  };

  const remitM = useMutation({
    mutationFn: () =>
      api<OutRemittance>(`/v1/crossborder/outbound/${remittanceId}/mark-remitted`, {
        method: "POST",
        body: { remittanceReference: reference.trim() },
      }),
    meta: { successMessage: "Marked remitted" },
    onSuccess: () => {
      invalidate();
      setReference("");
    },
  });
  const failM = useMutation({
    mutationFn: () =>
      api<OutRemittance>(`/v1/crossborder/outbound/${remittanceId}/mark-failed`, {
        method: "POST",
        body: { reason: "Marked failed from console" },
      }),
    meta: { successMessage: "Marked failed" },
    onSuccess: invalidate,
  });

  const r = detailQ.data?.remittance;
  const events = detailQ.data?.events ?? [];

  return (
    <Sheet open={remittanceId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>{r ? r.beneficiaryName : "Remittance"}</SheetTitle>
          <SheetDescription>
            {r ? `${r.beneficiarySwift} · ${formatInr(r.foreignAmountMinor, r.currency)}` : "Loading…"}
          </SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto px-4">
          <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error} skeletonRows={4}>
            {r && (
              <>
                <div>
                  <DetailRow label="Status" value={outStatusBadge(r.status)} />
                  <DetailRow label="FX rate" value={fmtRate(r.fxRate)} />
                  <DetailRow label="Principal (INR)" value={formatInr(r.principalInrMinor)} />
                  <DetailRow label="TCS" value={formatInr(r.tcsMinor)} />
                  <DetailRow label="INR debited" value={formatInr(r.inrDebitedMinor)} />
                  <DetailRow label="LRS used (FY)" value={`${formatInr(r.lrsCumulativeAfterMinor)} · ${r.financialYear}`} />
                  {r.remittanceReference && <DetailRow label="Reference" value={r.remittanceReference} />}
                </div>

                <div>
                  <p className="mb-2 text-sm font-medium">Events</p>
                  {events.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No events recorded.</p>
                  ) : (
                    <ul className="space-y-2">
                      {events.map((e) => (
                        <li key={e.id} className="flex items-center justify-between rounded-lg border p-2.5 text-sm">
                          <span>
                            <Badge variant="outline">{e.type}</Badge>
                            {e.note && <span className="ml-2 text-xs text-muted-foreground">{e.note}</span>}
                          </span>
                          <span className="text-xs text-muted-foreground">
                            <TimeSince value={e.createdAt} />
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>

                {r.status === "INITIATED" && (
                  <div className="space-y-3 rounded-lg border p-3">
                    <p className="text-sm font-medium">Settle remittance</p>
                    <TextField id="ob-ref" label="Remittance reference" value={reference} onChange={setReference} placeholder="SWIFT MT103 ref" />
                    <FormError error={remitM.error ?? failM.error} />
                    <div className="flex gap-2">
                      <Button size="sm" disabled={reference.trim() === "" || remitM.isPending} onClick={() => remitM.mutate()}>
                        Mark remitted
                      </Button>
                      <Button size="sm" variant="outline" disabled={failM.isPending} onClick={() => failM.mutate()}>
                        Mark failed
                      </Button>
                    </div>
                  </div>
                )}
              </>
            )}
          </DataState>
        </div>
        <SheetFooter>
          <Separator className="mb-2" />
          <SheetClose render={<Button variant="outline">Close</Button>} />
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
