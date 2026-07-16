import {
  Badge,
  Button,
  Card,
  DataState,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, createFileRoute } from "@tanstack/react-router";
import { CheckCircle2Icon, ReceiptIcon } from "lucide-react";
import { toast } from "sonner";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";
import { type CsvColumn, exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr } from "@/lib/money";

export const Route = createFileRoute("/_app/invoices")({ component: InvoicesPage });

// ── Types & options (mirrors billing/BillingController InvoiceView) ───────────

type InvoiceView = {
  id: string;
  subscriptionId: string;
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

const STATUSES = ["OPEN", "PAID", "VOID"] as const;

const STATUS_VARIANT: Record<string, BadgeVariant> = {
  OPEN: "warning",
  PAID: "success",
  VOID: "muted",
};

function InvoicesPage() {
  const qc = useQueryClient();

  const invoicesQ = useQuery({
    queryKey: ["invoices"],
    queryFn: () => api<InvoiceView[]>("/v1/invoices"),
    staleTime: 15_000,
  });

  const payM = useMutation({
    mutationFn: (id: string) => api<InvoiceView>(`/v1/invoices/${id}/pay`, { method: "POST" }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["invoices"] });
      toast.success("Invoice paid");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Payment failed"),
  });

  const lv = useListView(invoicesQ.data ?? [], {
    searchFields: (i) => [i.id, i.subscriptionId, i.status],
    filterFields: { status: (i) => i.status },
    sortFields: { amountMinor: (i) => i.amountMinor, status: (i) => i.status },
  });

  const columns: CsvColumn<InvoiceView>[] = [
    { header: "ID", value: (i) => i.id },
    { header: "Subscription ID", value: (i) => i.subscriptionId },
    { header: "Amount (minor)", value: (i) => i.amountMinor },
    { header: "Currency", value: (i) => i.currency },
    { header: "Status", value: (i) => i.status },
    { header: "Ledger entry ID", value: (i) => i.ledgerEntryId ?? "" },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Subscription invoices — collect payment on open invoices." />

      <Card className="gap-0 overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search invoices…"
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
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("invoices", lv.view, columns)
              : exportToJson("invoices", lv.view)
          }
          exportDisabled={lv.view.length === 0}
        />

        <DataState
          isLoading={invoicesQ.isLoading}
          isError={invoicesQ.isError}
          error={invoicesQ.error}
          isEmpty={lv.view.length === 0}
          empty={
            <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
              <div className="flex size-11 items-center justify-center rounded-full bg-muted text-muted-foreground">
                <ReceiptIcon className="size-5" />
              </div>
              <div className="space-y-1">
                <p className="font-heading text-sm font-medium">No invoices yet</p>
                <p className="mx-auto max-w-sm text-sm text-muted-foreground">
                  Invoices are generated when a customer subscribes to a{" "}
                  <Link to={"/subscriptions" as never} className="underline underline-offset-2">
                    subscription
                  </Link>
                  .
                </p>
              </div>
            </div>
          }
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Invoice ID</TableHead>
                  <TableHead>Subscription ID</TableHead>
                  <SortHeader columnKey="amountMinor" sort={lv.sort} onToggle={lv.toggleSort}>
                    Amount
                  </SortHeader>
                  <SortHeader columnKey="status" sort={lv.sort} onToggle={lv.toggleSort}>
                    Status
                  </SortHeader>
                  <TableHead>Ledger entry</TableHead>
                  <TableHead className="text-end">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((i) => (
                  <TableRow key={i.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                    <TableCell className="font-mono text-xs">{i.id}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {i.subscriptionId}
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {formatInr(i.amountMinor, i.currency)}
                    </TableCell>
                    <TableCell>
                      <Badge variant={STATUS_VARIANT[i.status] ?? "muted"}>{i.status}</Badge>
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {i.ledgerEntryId ?? "—"}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end">
                        {i.status === "OPEN" ? (
                          <Button
                            size="xs"
                            disabled={payM.isPending}
                            onClick={() => payM.mutate(i.id)}
                          >
                            <CheckCircle2Icon /> Pay
                          </Button>
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
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
    </div>
  );
}
