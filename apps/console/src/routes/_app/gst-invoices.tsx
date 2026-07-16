import {
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  DataState,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
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
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { EyeIcon, PlusIcon, ReceiptTextIcon, SearchIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { FormSheet, KeyValue, LabeledField, StatusBadge } from "@/features/gst/ui";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/gst-invoices")({ component: GstInvoicesPage });

type LineView = {
  description: string;
  hsnSac: string;
  taxableMinor: number;
  cgstMinor: number;
  sgstMinor: number;
  igstMinor: number;
  lineTotalMinor: number;
};

type InvoiceView = {
  id: string;
  invoiceNumber: string;
  supplyType: string;
  status: string;
  taxableMinor: number;
  cgstMinor: number;
  sgstMinor: number;
  igstMinor: number;
  totalGstMinor: number;
  totalMinor: number;
  ledgerEntryId: string | null;
  lines: LineView[];
};

type NoteView = {
  id: string;
  type: string;
  originalInvoiceId: string;
  reason: string;
  taxableMinor: number;
  totalMinor: number;
  status: string;
  ledgerEntryId: string | null;
  issuedAt: string;
};

type LineDraft = {
  description: string;
  hsnSac: string;
  quantity: string;
  unitPrice: string;
  gstRate: string;
};

const GST_RATES = ["0", "5", "12", "18", "28"];

function blankLine(): LineDraft {
  return { description: "", hsnSac: "", quantity: "1", unitPrice: "", gstRate: "18" };
}

function GstInvoicesPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState<InvoiceView | null>(null);
  const [lookupId, setLookupId] = useState("");

  const [supplierGstin, setSupplierGstin] = useState("");
  const [buyerGstin, setBuyerGstin] = useState("");
  const [placeOfSupply, setPlaceOfSupply] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [lines, setLines] = useState<LineDraft[]>([blankLine()]);

  const invoicesQ = useQuery({
    queryKey: ["gst-invoices"],
    queryFn: () => api<InvoiceView[]>("/v1/gst/invoices"),
    staleTime: 15_000,
  });

  const notesQ = useQuery({
    queryKey: ["gst-notes"],
    queryFn: () => api<NoteView[]>("/v1/gst/notes"),
    staleTime: 30_000,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["gst-invoices"] });

  const createMut = useMutation({
    mutationFn: (body: unknown) =>
      api<InvoiceView>("/v1/gst/invoices", { method: "POST", body }),
    meta: { successMessage: "GST invoice created" },
    onSuccess: () => {
      invalidate();
      setOpen(false);
      resetForm();
    },
  });

  const payMut = useMutation({
    mutationFn: (id: string) =>
      api<InvoiceView>(`/v1/gst/invoices/${id}/pay`, { method: "POST" }),
    meta: { successMessage: "Invoice paid — revenue + tax recognised" },
    onSuccess: invalidate,
  });

  // The list returns header-only views (empty lines); look up an invoice by id
  // to load its full line breakdown into the detail dialog.
  const lookupMut = useMutation({
    mutationFn: (id: string) => api<InvoiceView>(`/v1/gst/invoices/${id}`),
    meta: { successMessage: "Invoice loaded" },
    onSuccess: (inv) => {
      setDetail(inv);
      setLookupId("");
    },
  });

  function resetForm() {
    setSupplierGstin("");
    setBuyerGstin("");
    setPlaceOfSupply("");
    setCurrency("INR");
    setLines([blankLine()]);
  }

  function updateLine(idx: number, patch: Partial<LineDraft>) {
    setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, ...patch } : l)));
  }

  function submit() {
    const built = lines.map((l) => ({
      description: l.description.trim(),
      hsnSac: l.hsnSac.trim(),
      quantity: Number(l.quantity),
      unitPriceMinor: rupeesToMinor(l.unitPrice),
      gstRate: Number(l.gstRate),
    }));
    createMut.mutate({
      supplierGstin: supplierGstin.trim(),
      buyerGstin: buyerGstin.trim() || null,
      placeOfSupply: placeOfSupply.trim(),
      currency: currency.trim() || "INR",
      lines: built,
    });
  }

  const formValid =
    supplierGstin.trim().length >= 2 &&
    placeOfSupply.trim() !== "" &&
    lines.length > 0 &&
    lines.every(
      (l) =>
        l.description.trim() &&
        l.hsnSac.trim() &&
        Number(l.quantity) > 0 &&
        rupeesToMinor(l.unitPrice) !== null &&
        rupeesToMinor(l.unitPrice)! > 0,
    );

  const lv = useListView(invoicesQ.data ?? [], {
    searchFields: (i) => [i.invoiceNumber, i.status, i.supplyType],
    filterFields: { status: (i) => i.status },
    sortFields: {
      number: (i) => i.invoiceNumber,
      total: (i) => i.totalMinor,
      status: (i) => i.status,
    },
  });

  const csvColumns = [
    { header: "Invoice", value: (i: InvoiceView) => i.invoiceNumber },
    { header: "Supply", value: (i: InvoiceView) => i.supplyType },
    { header: "Status", value: (i: InvoiceView) => i.status },
    { header: "Taxable", value: (i: InvoiceView) => i.taxableMinor / 100 },
    { header: "CGST", value: (i: InvoiceView) => i.cgstMinor / 100 },
    { header: "SGST", value: (i: InvoiceView) => i.sgstMinor / 100 },
    { header: "IGST", value: (i: InvoiceView) => i.igstMinor / 100 },
    { header: "Total", value: (i: InvoiceView) => i.totalMinor / 100 },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Raise GST tax invoices with an automatic CGST/SGST/IGST breakup, then settle them to the ledger."
        actions={
          <Button onClick={() => setOpen(true)}>
            <PlusIcon /> Create GST invoice
          </Button>
        }
      />

      <Card className="gap-0 py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search by number, status…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              onChange: (v) => lv.setFilter("status", v),
              options: [
                { label: "Issued", value: "ISSUED" },
                { label: "Paid", value: "PAID" },
                { label: "Cancelled", value: "CANCELLED" },
              ],
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("gst-invoices", lv.view, csvColumns)
              : exportToJson("gst-invoices", lv.view)
          }
          exportDisabled={lv.view.length === 0}
        >
          <div className="relative">
            <SearchIcon className="pointer-events-none absolute inset-s-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={lookupId}
              onChange={(e) => setLookupId(e.target.value)}
              placeholder="Look up invoice by id"
              className="ps-9 sm:w-56"
              aria-label="Look up invoice by id"
            />
          </div>
          <Button
            variant="outline"
            disabled={!lookupId.trim() || lookupMut.isPending}
            onClick={() => lookupMut.mutate(lookupId.trim())}
          >
            Look up
          </Button>
        </ListToolbar>

        <DataState
          isLoading={invoicesQ.isLoading}
          isError={invoicesQ.isError}
          error={invoicesQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={ReceiptTextIcon}
          emptyTitle="No invoices yet"
          emptyDescription="Create a GST invoice with an automatic CGST/SGST/IGST breakup to get started."
        >
          <Table>
            <TableHeader>
              <TableRow>
                <SortHeader columnKey="number" sort={lv.sort} onToggle={lv.toggleSort}>
                  Invoice
                </SortHeader>
                <TableHead>Supply</TableHead>
                <SortHeader columnKey="status" sort={lv.sort} onToggle={lv.toggleSort}>
                  Status
                </SortHeader>
                <TableHead className="text-end">Taxable</TableHead>
                <TableHead className="text-end">CGST</TableHead>
                <TableHead className="text-end">SGST</TableHead>
                <TableHead className="text-end">IGST</TableHead>
                <SortHeader columnKey="total" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                  Total
                </SortHeader>
                <TableHead className="text-end">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((inv) => (
                <TableRow key={inv.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                  <TableCell className="font-medium">{inv.invoiceNumber}</TableCell>
                  <TableCell>
                    <span className="text-xs text-muted-foreground">
                      {inv.supplyType.replace(/_/g, "-").toLowerCase()}
                    </span>
                  </TableCell>
                  <TableCell>
                    <StatusBadge value={inv.status} />
                  </TableCell>
                  <TableCell className="text-end tabular-nums">{formatInr(inv.taxableMinor)}</TableCell>
                  <TableCell className="text-end tabular-nums">{formatInr(inv.cgstMinor)}</TableCell>
                  <TableCell className="text-end tabular-nums">{formatInr(inv.sgstMinor)}</TableCell>
                  <TableCell className="text-end tabular-nums">{formatInr(inv.igstMinor)}</TableCell>
                  <TableCell className="text-end font-medium tabular-nums">
                    {formatInr(inv.totalMinor)}
                  </TableCell>
                  <TableCell className="text-end">
                    <div className="flex justify-end gap-1">
                      <Button variant="ghost" size="icon-sm" aria-label="View lines" onClick={() => setDetail(inv)}>
                        <EyeIcon />
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={inv.status !== "ISSUED" || payMut.isPending}
                        onClick={() => payMut.mutate(inv.id)}
                      >
                        Pay
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Recent credit &amp; debit notes</CardTitle>
          <CardDescription>Adjustments issued against GST invoices.</CardDescription>
        </CardHeader>
        <CardContent>
          <DataState
            isLoading={notesQ.isLoading}
            isError={notesQ.isError}
            error={notesQ.error}
            isEmpty={(notesQ.data ?? []).length === 0}
            emptyIcon={ReceiptTextIcon}
            emptyTitle="No notes"
            emptyDescription="Credit and debit notes will appear here once issued."
            skeletonRows={3}
          >
            <div className="space-y-2">
              {(notesQ.data ?? []).map((n) => (
                <div key={n.id} className="flex items-center justify-between gap-3 rounded-md border p-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <Badge variant={n.type === "CREDIT" ? "warning" : "secondary"}>{n.type}</Badge>
                      <StatusBadge value={n.status} />
                    </div>
                    <p className="mt-1 truncate text-sm text-muted-foreground">{n.reason}</p>
                  </div>
                  <span className="shrink-0 font-medium tabular-nums">{formatInr(n.totalMinor)}</span>
                </div>
              ))}
            </div>
          </DataState>
        </CardContent>
      </Card>

      <FormSheet
        open={open}
        onOpenChange={setOpen}
        title="Create GST invoice"
        description="GST is computed per line; intra-state supply splits CGST + SGST, inter-state uses IGST."
        submitLabel="Create invoice"
        submitting={createMut.isPending}
        disabled={!formValid}
        onSubmit={submit}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <LabeledField label="Supplier GSTIN" htmlFor="supplierGstin" description="First 2 digits = state code.">
            <Input
              id="supplierGstin"
              value={supplierGstin}
              onChange={(e) => setSupplierGstin(e.target.value.toUpperCase())}
              placeholder="27AAAAA0000A1Z5"
            />
          </LabeledField>
          <LabeledField label="Buyer GSTIN" htmlFor="buyerGstin" description="Optional (B2C leaves blank).">
            <Input
              id="buyerGstin"
              value={buyerGstin}
              onChange={(e) => setBuyerGstin(e.target.value.toUpperCase())}
              placeholder="Optional"
            />
          </LabeledField>
          <LabeledField label="Place of supply" htmlFor="placeOfSupply" description="State code, e.g. 27 (MH).">
            <Input
              id="placeOfSupply"
              value={placeOfSupply}
              onChange={(e) => setPlaceOfSupply(e.target.value)}
              placeholder="27"
            />
          </LabeledField>
          <LabeledField label="Currency" htmlFor="currency">
            <Input id="currency" value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} />
          </LabeledField>
        </div>

        <Separator />

        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-sm font-medium">Line items</p>
            <Button type="button" variant="outline" size="sm" onClick={() => setLines((p) => [...p, blankLine()])}>
              <PlusIcon /> Add line
            </Button>
          </div>

          {lines.map((line, idx) => (
            <div key={idx} className="rounded-md border p-3">
              <div className="grid gap-3 sm:grid-cols-2">
                <LabeledField label="Description" htmlFor={`desc-${idx}`}>
                  <Input
                    id={`desc-${idx}`}
                    value={line.description}
                    onChange={(e) => updateLine(idx, { description: e.target.value })}
                    placeholder="Consulting services"
                  />
                </LabeledField>
                <LabeledField label="HSN / SAC" htmlFor={`hsn-${idx}`}>
                  <Input
                    id={`hsn-${idx}`}
                    value={line.hsnSac}
                    onChange={(e) => updateLine(idx, { hsnSac: e.target.value })}
                    placeholder="998311"
                  />
                </LabeledField>
              </div>
              <div className="mt-3 grid gap-3 sm:grid-cols-3">
                <LabeledField label="Quantity" htmlFor={`qty-${idx}`}>
                  <Input
                    id={`qty-${idx}`}
                    type="number"
                    min={1}
                    value={line.quantity}
                    onChange={(e) => updateLine(idx, { quantity: e.target.value })}
                  />
                </LabeledField>
                <LabeledField label="Unit price (₹)" htmlFor={`price-${idx}`}>
                  <Input
                    id={`price-${idx}`}
                    inputMode="decimal"
                    value={line.unitPrice}
                    onChange={(e) => updateLine(idx, { unitPrice: e.target.value })}
                    placeholder="0.00"
                  />
                </LabeledField>
                <LabeledField label="GST rate" htmlFor={`rate-${idx}`}>
                  <Select value={line.gstRate} onValueChange={(v) => v && updateLine(idx, { gstRate: v })}>
                    <SelectTrigger id={`rate-${idx}`}>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {GST_RATES.map((r) => (
                        <SelectItem key={r} value={r}>
                          {r}%
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </LabeledField>
              </div>
              {lines.length > 1 && (
                <div className="mt-2 flex justify-end">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => setLines((p) => p.filter((_, i) => i !== idx))}
                  >
                    <Trash2Icon /> Remove
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      </FormSheet>

      <Dialog open={detail !== null} onOpenChange={(o) => !o && setDetail(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{detail?.invoiceNumber}</DialogTitle>
            <DialogDescription>Line-item breakdown and GST totals.</DialogDescription>
          </DialogHeader>
          {detail && (
            <div className="space-y-4">
              {detail.lines.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Description</TableHead>
                      <TableHead>HSN/SAC</TableHead>
                      <TableHead className="text-end">Taxable</TableHead>
                      <TableHead className="text-end">Line total</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {detail.lines.map((l, i) => (
                      <TableRow key={i}>
                        <TableCell>{l.description}</TableCell>
                        <TableCell className="text-muted-foreground">{l.hsnSac}</TableCell>
                        <TableCell className="text-end tabular-nums">{formatInr(l.taxableMinor)}</TableCell>
                        <TableCell className="text-end tabular-nums">{formatInr(l.lineTotalMinor)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <p className="text-sm text-muted-foreground">
                  Line detail isn&apos;t cached for this invoice — look it up by id to load its lines.
                </p>
              )}
              <Separator />
              <div className="space-y-1.5">
                <KeyValue label="Taxable value">{formatInr(detail.taxableMinor)}</KeyValue>
                <KeyValue label="CGST">{formatInr(detail.cgstMinor)}</KeyValue>
                <KeyValue label="SGST">{formatInr(detail.sgstMinor)}</KeyValue>
                <KeyValue label="IGST">{formatInr(detail.igstMinor)}</KeyValue>
                <Separator />
                <KeyValue label="Invoice total" className="text-base">
                  {formatInr(detail.totalMinor)}
                </KeyValue>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
