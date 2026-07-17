import {
  Button,
  Card,
  Checkbox,
  DataState,
  FieldLabel,
  Input,
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
import { PlusIcon, RefreshCwIcon, ScaleIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { FormSheet, LabeledField, StatusBadge, SummaryStat } from "@/features/gst/ui";
import { ApiError, api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/itc")({ component: ItcPage });

type PurchaseView = {
  id: string;
  supplierGstin: string;
  supplierName: string;
  invoiceNumber: string;
  invoiceDate: string;
  taxableMinor: number;
  cgstMinor: number;
  sgstMinor: number;
  igstMinor: number;
  totalGstMinor: number;
  itcEligible: boolean;
  reconStatus: string;
  createdAt: string;
  reconciledAt: string | null;
};

type EligibleSummary = { eligibleInvoiceCount: number; eligibleItcMinor: number };
type ReconSummary = { matched: number; mismatched: number; missingIn2b: number };

type TwoBDraft = { supplierGstin: string; invoiceNumber: string; totalGst: string };

const today = () => new Date().toISOString().slice(0, 10);

function ItcPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reconOpen, setReconOpen] = useState(false);

  const [supplierGstin, setSupplierGstin] = useState("");
  const [supplierName, setSupplierName] = useState("");
  const [invoiceNumber, setInvoiceNumber] = useState("");
  const [invoiceDate, setInvoiceDate] = useState(today());
  const [taxable, setTaxable] = useState("");
  const [cgst, setCgst] = useState("");
  const [sgst, setSgst] = useState("");
  const [igst, setIgst] = useState("");
  const [itcEligible, setItcEligible] = useState(true);

  const [twoB, setTwoB] = useState<TwoBDraft[]>([]);

  const purchasesQ = useQuery({
    queryKey: ["itc-purchases"],
    queryFn: () => api<PurchaseView[]>("/v1/itc/purchases"),
    staleTime: 15_000,
  });
  const eligibleQ = useQuery({
    queryKey: ["itc-eligible"],
    queryFn: () => api<EligibleSummary>("/v1/itc/eligible-summary"),
    staleTime: 15_000,
  });

  const purchases = purchasesQ.data ?? [];

  const recordMut = useMutation({
    mutationFn: (body: unknown) => api<PurchaseView>("/v1/itc/purchases", { method: "POST", body }),
    meta: { successMessage: "Purchase recorded" },
    onSuccess: () => {
      setOpen(false);
      resetForm();
      qc.invalidateQueries({ queryKey: ["itc-purchases"] });
      qc.invalidateQueries({ queryKey: ["itc-eligible"] });
    },
  });

  const reconcileMut = useMutation({
    mutationFn: (lines: { supplierGstin: string; invoiceNumber: string; totalGstMinor: number }[]) =>
      api<ReconSummary>("/v1/itc/reconcile", { method: "POST", body: { lines } }),
    meta: { silent: true },
    onSuccess: (s) => {
      toast.success("Reconciled against GSTR-2B", {
        description: `${s.matched} matched · ${s.mismatched} mismatched · ${s.missingIn2b} missing`,
      });
      setReconOpen(false);
      qc.invalidateQueries({ queryKey: ["itc-purchases"] });
      qc.invalidateQueries({ queryKey: ["itc-eligible"] });
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Reconciliation failed"),
  });

  function resetForm() {
    setSupplierGstin("");
    setSupplierName("");
    setInvoiceNumber("");
    setInvoiceDate(today());
    setTaxable("");
    setCgst("");
    setSgst("");
    setIgst("");
    setItcEligible(true);
  }

  function submitPurchase() {
    recordMut.mutate({
      supplierGstin: supplierGstin.trim(),
      supplierName: supplierName.trim(),
      invoiceNumber: invoiceNumber.trim(),
      invoiceDate,
      taxableMinor: rupeesToMinor(taxable) ?? 0,
      cgstMinor: rupeesToMinor(cgst) ?? 0,
      sgstMinor: rupeesToMinor(sgst) ?? 0,
      igstMinor: rupeesToMinor(igst) ?? 0,
      itcEligible,
    });
  }

  function openReconcile() {
    // Seed the 2B lines from the recorded purchases so the operator can tweak a
    // total (→ MISMATCHED) or drop a line (→ MISSING_IN_2B) before posting.
    setTwoB(
      purchases.map((p) => ({
        supplierGstin: p.supplierGstin,
        invoiceNumber: p.invoiceNumber,
        totalGst: (p.totalGstMinor / 100).toFixed(2),
      })),
    );
    setReconOpen(true);
  }

  function submitReconcile() {
    reconcileMut.mutate(
      twoB.map((l) => ({
        supplierGstin: l.supplierGstin.trim(),
        invoiceNumber: l.invoiceNumber.trim(),
        totalGstMinor: rupeesToMinor(l.totalGst) ?? 0,
      })),
    );
  }

  const purchaseValid =
    supplierGstin.trim().length >= 2 && supplierName.trim() !== "" && invoiceNumber.trim() !== "";

  const lv = useListView(purchases, {
    searchFields: (p) => [p.supplierGstin, p.supplierName, p.invoiceNumber, p.reconStatus],
    filterFields: { reconStatus: (p) => p.reconStatus },
    sortFields: {
      supplier: (p) => p.supplierName,
      date: (p) => p.invoiceDate,
      gst: (p) => p.totalGstMinor,
    },
  });

  const csvColumns = [
    { header: "Supplier GSTIN", value: (p: PurchaseView) => p.supplierGstin },
    { header: "Supplier", value: (p: PurchaseView) => p.supplierName },
    { header: "Invoice", value: (p: PurchaseView) => p.invoiceNumber },
    { header: "Date", value: (p: PurchaseView) => p.invoiceDate },
    { header: "Taxable", value: (p: PurchaseView) => p.taxableMinor / 100 },
    { header: "Total GST", value: (p: PurchaseView) => p.totalGstMinor / 100 },
    { header: "ITC eligible", value: (p: PurchaseView) => p.itcEligible },
    { header: "Recon status", value: (p: PurchaseView) => p.reconStatus },
  ];

  const matched = purchases.filter((p) => p.reconStatus === "MATCHED").length;

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Track inward-supply purchase invoices, reconcile them against supplier-filed GSTR-2B, and report claimable ITC."
        actions={
          <div className="flex gap-2">
            <Button variant="outline" disabled={purchases.length === 0} onClick={openReconcile}>
              <RefreshCwIcon /> Reconcile against GSTR-2B
            </Button>
            <Button onClick={() => setOpen(true)}>
              <PlusIcon /> Record purchase
            </Button>
          </div>
        }
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <SummaryStat
          label="Eligible ITC"
          value={formatInr(eligibleQ.data?.eligibleItcMinor ?? 0)}
          hint={`${eligibleQ.data?.eligibleInvoiceCount ?? 0} matched & eligible invoices`}
        />
        <SummaryStat label="Purchases recorded" value={purchases.length} />
        <SummaryStat label="Matched in 2B" value={`${matched} / ${purchases.length}`} />
      </div>

      <Card className="gap-0 py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search supplier, invoice…"
          filters={[
            {
              id: "reconStatus",
              label: "Recon",
              value: lv.filters.reconStatus ?? "",
              onChange: (v) => lv.setFilter("reconStatus", v),
              options: [
                { label: "Unmatched", value: "UNMATCHED" },
                { label: "Matched", value: "MATCHED" },
                { label: "Mismatched", value: "MISMATCHED" },
                { label: "Missing in 2B", value: "MISSING_IN_2B" },
              ],
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("itc-purchases", lv.view, csvColumns)
              : exportToJson("itc-purchases", lv.view)
          }
          exportDisabled={lv.view.length === 0}
        />

        <DataState
          isLoading={purchasesQ.isLoading}
          isError={purchasesQ.isError}
          error={purchasesQ.error}
          isEmpty={purchases.length === 0}
          emptyIcon={ScaleIcon}
          emptyTitle="No purchases recorded"
          emptyDescription="Record an inward-supply purchase invoice to start tracking input tax credit."
          skeletonRows={5}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <SortHeader columnKey="supplier" sort={lv.sort} onToggle={lv.toggleSort}>
                  Supplier
                </SortHeader>
                <TableHead>Invoice</TableHead>
                <SortHeader columnKey="date" sort={lv.sort} onToggle={lv.toggleSort}>
                  Date
                </SortHeader>
                <TableHead className="text-end">Taxable</TableHead>
                <SortHeader columnKey="gst" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                  Total GST
                </SortHeader>
                <TableHead>ITC</TableHead>
                <TableHead>Recon</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((p) => (
                <TableRow key={p.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                  <TableCell>
                    <div className="font-medium">{p.supplierName}</div>
                    <div className="font-mono text-xs text-muted-foreground">{p.supplierGstin}</div>
                  </TableCell>
                  <TableCell className="font-medium">{p.invoiceNumber}</TableCell>
                  <TableCell className="tabular-nums">{p.invoiceDate}</TableCell>
                  <TableCell className="text-end tabular-nums">{formatInr(p.taxableMinor)}</TableCell>
                  <TableCell className="text-end font-medium tabular-nums">{formatInr(p.totalGstMinor)}</TableCell>
                  <TableCell>{p.itcEligible ? "Eligible" : "Ineligible"}</TableCell>
                  <TableCell>
                    <StatusBadge value={p.reconStatus} />
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
        title="Record purchase"
        description="Log an inward-supply invoice. It starts unmatched until reconciled against GSTR-2B."
        submitLabel="Record purchase"
        submitting={recordMut.isPending}
        disabled={!purchaseValid}
        onSubmit={submitPurchase}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <LabeledField label="Supplier GSTIN" htmlFor="supplierGstin">
            <Input
              id="supplierGstin"
              value={supplierGstin}
              onChange={(e) => setSupplierGstin(e.target.value.toUpperCase())}
              placeholder="27AAAAA0000A1Z5"
            />
          </LabeledField>
          <LabeledField label="Supplier name" htmlFor="supplierName">
            <Input id="supplierName" value={supplierName} onChange={(e) => setSupplierName(e.target.value)} />
          </LabeledField>
          <LabeledField label="Invoice number" htmlFor="invoiceNumber">
            <Input id="invoiceNumber" value={invoiceNumber} onChange={(e) => setInvoiceNumber(e.target.value)} />
          </LabeledField>
          <LabeledField label="Invoice date" htmlFor="invoiceDate">
            <Input id="invoiceDate" type="date" value={invoiceDate} onChange={(e) => setInvoiceDate(e.target.value)} />
          </LabeledField>
        </div>

        <Separator />

        <div className="grid gap-4 sm:grid-cols-2">
          <LabeledField label="Taxable value (₹)" htmlFor="taxable">
            <Input id="taxable" inputMode="decimal" value={taxable} onChange={(e) => setTaxable(e.target.value)} placeholder="0.00" />
          </LabeledField>
          <LabeledField label="CGST (₹)" htmlFor="cgst">
            <Input id="cgst" inputMode="decimal" value={cgst} onChange={(e) => setCgst(e.target.value)} placeholder="0.00" />
          </LabeledField>
          <LabeledField label="SGST (₹)" htmlFor="sgst">
            <Input id="sgst" inputMode="decimal" value={sgst} onChange={(e) => setSgst(e.target.value)} placeholder="0.00" />
          </LabeledField>
          <LabeledField label="IGST (₹)" htmlFor="igst">
            <Input id="igst" inputMode="decimal" value={igst} onChange={(e) => setIgst(e.target.value)} placeholder="0.00" />
          </LabeledField>
        </div>

        <div className="flex items-center gap-2">
          <Checkbox
            id="itcEligible"
            checked={itcEligible}
            onCheckedChange={(v) => setItcEligible(v === true)}
          />
          <FieldLabel htmlFor="itcEligible" className="font-normal">
            ITC eligible for this purchase
          </FieldLabel>
        </div>
      </FormSheet>

      <FormSheet
        open={reconOpen}
        onOpenChange={setReconOpen}
        title="Reconcile against GSTR-2B"
        description="These lines represent supplier-filed 2B data. Edit a total to simulate a mismatch, or remove a line to simulate a missing entry."
        submitLabel="Run reconciliation"
        submitting={reconcileMut.isPending}
        onSubmit={submitReconcile}
      >
        {twoB.length === 0 ? (
          <p className="text-sm text-muted-foreground">No 2B lines. Add one below.</p>
        ) : (
          twoB.map((line, idx) => (
            <div key={idx} className="rounded-md border p-3">
              <div className="grid gap-3 sm:grid-cols-2">
                <LabeledField label="Supplier GSTIN" htmlFor={`b-gstin-${idx}`}>
                  <Input
                    id={`b-gstin-${idx}`}
                    value={line.supplierGstin}
                    onChange={(e) =>
                      setTwoB((p) => p.map((l, i) => (i === idx ? { ...l, supplierGstin: e.target.value.toUpperCase() } : l)))
                    }
                  />
                </LabeledField>
                <LabeledField label="Invoice number" htmlFor={`b-inv-${idx}`}>
                  <Input
                    id={`b-inv-${idx}`}
                    value={line.invoiceNumber}
                    onChange={(e) =>
                      setTwoB((p) => p.map((l, i) => (i === idx ? { ...l, invoiceNumber: e.target.value } : l)))
                    }
                  />
                </LabeledField>
              </div>
              <div className="mt-3 flex items-end justify-between gap-3">
                <LabeledField label="Total GST in 2B (₹)" htmlFor={`b-gst-${idx}`}>
                  <Input
                    id={`b-gst-${idx}`}
                    inputMode="decimal"
                    value={line.totalGst}
                    onChange={(e) =>
                      setTwoB((p) => p.map((l, i) => (i === idx ? { ...l, totalGst: e.target.value } : l)))
                    }
                  />
                </LabeledField>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => setTwoB((p) => p.filter((_, i) => i !== idx))}
                >
                  <Trash2Icon /> Remove
                </Button>
              </div>
            </div>
          ))
        )}
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => setTwoB((p) => [...p, { supplierGstin: "", invoiceNumber: "", totalGst: "" }])}
        >
          <PlusIcon /> Add 2B line
        </Button>
      </FormSheet>
    </div>
  );
}
