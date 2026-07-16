import {
  Button,
  Card,
  DataState,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { FileTextIcon, PlusIcon, SendIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { FormSheet, LabeledField, StatusBadge } from "@/features/gst/ui";
import { ApiError, api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr } from "@/lib/money";

export const Route = createFileRoute("/_app/gst-returns")({ component: GstReturnsPage });

type ReturnSummary = {
  id: string;
  type: string;
  period: string;
  status: string;
  invoiceCount: number;
  totalTaxableMinor: number;
  totalCgstMinor: number;
  totalSgstMinor: number;
  totalIgstMinor: number;
  totalTaxMinor: number;
  arn: string | null;
  preparedAt: string | null;
  filedAt: string | null;
};

function GstReturnsPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [type, setType] = useState("GSTR1");
  const [period, setPeriod] = useState("");

  const returnsQ = useQuery({
    queryKey: ["gst-returns"],
    queryFn: () => api<ReturnSummary[]>("/v1/gst/returns"),
    staleTime: 15_000,
  });

  const prepareMut = useMutation({
    mutationFn: () =>
      api("/v1/gst/returns/prepare", { method: "POST", body: { type, period } }),
    meta: { successMessage: "Return prepared" },
    onSuccess: () => {
      setOpen(false);
      setPeriod("");
      qc.invalidateQueries({ queryKey: ["gst-returns"] });
    },
  });

  const fileMut = useMutation({
    mutationFn: (id: string) =>
      api<ReturnSummary>(`/v1/gst/returns/${id}/file`, { method: "POST" }),
    meta: { silent: true },
    onSuccess: (ret) => {
      toast.success(`${ret.type} filed to GSTN`, { description: `ARN ${ret.arn ?? "—"}` });
      qc.invalidateQueries({ queryKey: ["gst-returns"] });
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Failed to file return"),
  });

  const rows = returnsQ.data ?? [];

  const lv = useListView(rows, {
    searchFields: (r) => [r.type, r.period, r.status, r.arn],
    filterFields: { type: (r) => r.type, status: (r) => r.status },
    sortFields: {
      period: (r) => r.period,
      tax: (r) => r.totalTaxMinor,
      status: (r) => r.status,
    },
    initialDensity: "comfortable",
  });

  const csvColumns = [
    { header: "Type", value: (r: ReturnSummary) => r.type },
    { header: "Period", value: (r: ReturnSummary) => r.period },
    { header: "Status", value: (r: ReturnSummary) => r.status },
    { header: "Invoices", value: (r: ReturnSummary) => r.invoiceCount },
    { header: "Taxable", value: (r: ReturnSummary) => r.totalTaxableMinor / 100 },
    { header: "Total tax", value: (r: ReturnSummary) => r.totalTaxMinor / 100 },
    { header: "ARN", value: (r: ReturnSummary) => r.arn ?? "" },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Prepare GSTR-1 / GSTR-3B returns from the period's invoices, then file them to GSTN and capture the ARN."
        actions={
          <Button onClick={() => setOpen(true)}>
            <PlusIcon /> Prepare return
          </Button>
        }
      />

      <Card className="gap-0 py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search by period, ARN…"
          filters={[
            {
              id: "type",
              label: "Type",
              value: lv.filters.type ?? "",
              onChange: (v) => lv.setFilter("type", v),
              options: [
                { label: "GSTR-1", value: "GSTR1" },
                { label: "GSTR-3B", value: "GSTR3B" },
              ],
            },
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              onChange: (v) => lv.setFilter("status", v),
              options: [
                { label: "Draft", value: "DRAFT" },
                { label: "Prepared", value: "PREPARED" },
                { label: "Filed", value: "FILED" },
                { label: "Error", value: "ERROR" },
              ],
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("gst-returns", lv.view, csvColumns)
              : exportToJson("gst-returns", lv.view)
          }
          exportDisabled={lv.view.length === 0}
        />

        <DataState
          isLoading={returnsQ.isLoading}
          isError={returnsQ.isError}
          error={returnsQ.error}
          isEmpty={rows.length === 0}
          emptyIcon={FileTextIcon}
          emptyTitle="No returns yet"
          emptyDescription="Prepare a GSTR-1 or GSTR-3B return for a tax period to get started."
          skeletonRows={5}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Type</TableHead>
                <SortHeader columnKey="period" sort={lv.sort} onToggle={lv.toggleSort}>
                  Period
                </SortHeader>
                <SortHeader columnKey="status" sort={lv.sort} onToggle={lv.toggleSort}>
                  Status
                </SortHeader>
                <TableHead className="text-end">Invoices</TableHead>
                <TableHead className="text-end">Taxable</TableHead>
                <SortHeader columnKey="tax" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                  Total tax
                </SortHeader>
                <TableHead>ARN</TableHead>
                <TableHead className="text-end">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((r) => (
                <TableRow key={r.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                  <TableCell className="font-medium">{r.type.replace("GSTR", "GSTR-")}</TableCell>
                  <TableCell className="tabular-nums">{r.period}</TableCell>
                  <TableCell>
                    <StatusBadge value={r.status} />
                  </TableCell>
                  <TableCell className="text-end tabular-nums">{r.invoiceCount}</TableCell>
                  <TableCell className="text-end tabular-nums">{formatInr(r.totalTaxableMinor)}</TableCell>
                  <TableCell className="text-end font-medium tabular-nums">{formatInr(r.totalTaxMinor)}</TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">{r.arn ?? "—"}</TableCell>
                  <TableCell className="text-end">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={r.status === "FILED" || fileMut.isPending}
                      onClick={() => fileMut.mutate(r.id)}
                    >
                      <SendIcon /> {r.status === "FILED" ? "Filed" : "File"}
                    </Button>
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
        title="Prepare return"
        description="Aggregates the tax period's issued invoices into a re-preparable GSTR return."
        submitLabel="Prepare return"
        submitting={prepareMut.isPending}
        disabled={!/^\d{4}-\d{2}$/.test(period)}
        onSubmit={() => prepareMut.mutate()}
      >
        <LabeledField label="Return type" htmlFor="type">
          <Select value={type} onValueChange={(v) => v && setType(v)}>
            <SelectTrigger id="type">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="GSTR1">GSTR-1 (outward-supply detail)</SelectItem>
              <SelectItem value="GSTR3B">GSTR-3B (consolidated summary)</SelectItem>
            </SelectContent>
          </Select>
        </LabeledField>
        <LabeledField label="Tax period" htmlFor="period" description="Month the return covers (YYYY-MM).">
          <Input id="period" type="month" value={period} onChange={(e) => setPeriod(e.target.value)} />
        </LabeledField>
      </FormSheet>
    </div>
  );
}
