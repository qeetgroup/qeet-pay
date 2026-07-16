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
  Textarea,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { CreditCardIcon, PlusIcon } from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { api } from "@/lib/api";
import { type CsvColumn, exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/payments")({ component: PaymentsPage });

type PaymentMethod = "UPI" | "CARD" | "NET_BANKING" | "WALLET";
type PaymentStatus = "CREATED" | "AUTHORIZED" | "CAPTURED" | "FAILED" | "CANCELLED";

// Mirrors payments/PaymentController PaymentView (camelCase).
type PaymentView = {
  id: string;
  amountMinor: number;
  currency: string;
  method: string;
  status: string;
  providerPaymentId: string | null;
  ledgerEntryId: string | null;
};

const METHODS: { value: PaymentMethod; label: string }[] = [
  { value: "UPI", label: "UPI" },
  { value: "CARD", label: "Card" },
  { value: "NET_BANKING", label: "Net Banking" },
  { value: "WALLET", label: "Wallet" },
];

const METHOD_LABELS: Record<string, string> = {
  UPI: "UPI",
  CARD: "Card",
  NET_BANKING: "Net Banking",
  WALLET: "Wallet",
};

const OUTCOME_LABELS: Record<string, string> = {
  false: "Authorize (success)",
  true: "Simulate failure",
};

const STATUSES = ["CREATED", "AUTHORIZED", "CAPTURED", "FAILED", "CANCELLED"] as const;

function statusVariant(status: string) {
  switch (status as PaymentStatus) {
    case "CAPTURED":
      return "success" as const;
    case "AUTHORIZED":
      return "warning" as const;
    case "FAILED":
      return "destructive" as const;
    case "CANCELLED":
      return "muted" as const;
    default:
      return "secondary" as const;
  }
}

const CAPTURABLE = new Set<string>(["CREATED", "AUTHORIZED"]);

function PaymentsPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  // Create form state
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [method, setMethod] = useState<PaymentMethod>("UPI");
  const [description, setDescription] = useState("");
  const [simulateFailure, setSimulateFailure] = useState("false");
  const [amountError, setAmountError] = useState<string | null>(null);

  const paymentsQ = useQuery({
    queryKey: ["payments"],
    queryFn: () => api<PaymentView[]>("/v1/payments"),
    staleTime: 15_000,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["payments"] });

  const createM = useMutation({
    mutationFn: (body: {
      amountMinor: number;
      currency: string;
      method: PaymentMethod;
      description?: string;
      simulateFailure: boolean;
    }) => api<PaymentView>("/v1/payments", { method: "POST", body }),
    meta: { successMessage: "Payment created" },
    onSuccess: () => {
      invalidate();
      setOpen(false);
      setAmount("");
      setDescription("");
      setSimulateFailure("false");
    },
  });

  const captureM = useMutation({
    mutationFn: (id: string) =>
      api<PaymentView>(`/v1/payments/${id}/capture`, { method: "POST" }),
    meta: { successMessage: "Payment captured" },
    onSuccess: invalidate,
  });

  function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(amount);
    if (minor === null || minor <= 0) {
      setAmountError("Enter a valid amount greater than zero.");
      return;
    }
    setAmountError(null);
    createM.mutate({
      amountMinor: minor,
      currency: currency.trim().toUpperCase() || "INR",
      method,
      description: description.trim() || undefined,
      simulateFailure: simulateFailure === "true",
    });
  }

  const lv = useListView(paymentsQ.data ?? [], {
    searchFields: (p) => [p.id, p.method, p.status, p.providerPaymentId],
    filterFields: { status: (p) => p.status, method: (p) => p.method },
    sortFields: {
      amountMinor: (p) => p.amountMinor,
      status: (p) => p.status,
    },
  });

  const columns: CsvColumn<PaymentView>[] = [
    { header: "Payment ID", value: (p) => p.id },
    { header: "Method", value: (p) => p.method },
    { header: "Amount (minor)", value: (p) => p.amountMinor },
    { header: "Currency", value: (p) => p.currency },
    { header: "Status", value: (p) => p.status },
    { header: "Provider ref", value: (p) => p.providerPaymentId ?? "" },
    { header: "Ledger entry", value: (p) => p.ledgerEntryId ?? "" },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Accept a payment, then capture the authorized amount to post it to the ledger."
        actions={
          <Button onClick={() => setOpen(true)}>
            <PlusIcon /> Create payment
          </Button>
        }
      />

      <Card className="gap-0 overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search payments…"
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
              options: METHODS.map((m) => ({ label: m.label, value: m.value })),
              onChange: (v) => lv.setFilter("method", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("payments", lv.view, columns)
              : exportToJson("payments", lv.view)
          }
          exportDisabled={lv.view.length === 0}
        >
          <Button size="sm" onClick={() => setOpen(true)}>
            <PlusIcon /> New payment
          </Button>
        </ListToolbar>

        <DataState
          isLoading={paymentsQ.isLoading}
          isError={paymentsQ.isError}
          error={paymentsQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={CreditCardIcon}
          emptyTitle={lv.hasActiveFilters ? "No matching payments" : "No payments yet"}
          emptyDescription={
            lv.hasActiveFilters
              ? "Try clearing filters or search."
              : "Create a payment, then capture the authorized amount to post it to the ledger."
          }
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Payment ID</TableHead>
                  <TableHead>Method</TableHead>
                  <SortHeader
                    columnKey="amountMinor"
                    sort={lv.sort}
                    onToggle={lv.toggleSort}
                    className="text-right"
                  >
                    Amount
                  </SortHeader>
                  <SortHeader columnKey="status" sort={lv.sort} onToggle={lv.toggleSort}>
                    Status
                  </SortHeader>
                  <TableHead>Provider Ref</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((p) => (
                  <TableRow key={p.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                    <TableCell className="font-mono text-xs">{p.id}</TableCell>
                    <TableCell>{METHOD_LABELS[p.method] ?? p.method}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatInr(p.amountMinor, p.currency)}
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(p.status)}>{p.status}</Badge>
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {p.providerPaymentId ?? "—"}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end gap-2">
                        {CAPTURABLE.has(p.status) && (
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={captureM.isPending}
                            onClick={() => captureM.mutate(p.id)}
                          >
                            Capture
                          </Button>
                        )}
                        {p.status === "CAPTURED" && (
                          <Button size="sm" variant="ghost" render={<Link to={"/refunds" as never} />}>
                            Refund
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

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="w-full sm:max-w-md">
          <form onSubmit={submitCreate} className="flex h-full flex-col">
            <SheetHeader>
              <SheetTitle>Create payment</SheetTitle>
              <SheetDescription>
                Creates and authorizes a payment. Capture it afterwards to post to the ledger.
              </SheetDescription>
            </SheetHeader>

            <FieldGroup className="flex-1 overflow-y-auto p-4">
              <Field>
                <FieldLabel htmlFor="amount">Amount</FieldLabel>
                <Input
                  id="amount"
                  type="number"
                  min="0"
                  step="0.01"
                  inputMode="decimal"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0.00"
                />
                <FieldDescription>Entered in rupees; sent to the API as paise.</FieldDescription>
                <FieldError errors={amountError ? [{ message: amountError }] : undefined} />
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
                <Select
                  items={METHOD_LABELS}
                  value={method}
                  onValueChange={(v) => setMethod(v as PaymentMethod)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select a method" />
                  </SelectTrigger>
                  <SelectContent>
                    {METHODS.map((m) => (
                      <SelectItem key={m.value} value={m.value}>
                        {m.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>

              <Field>
                <FieldLabel htmlFor="description">Description</FieldLabel>
                <Textarea
                  id="description"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Optional note shown on the payment"
                  rows={3}
                />
              </Field>

              <Field>
                <FieldLabel>Sandbox outcome</FieldLabel>
                <Select
                  items={OUTCOME_LABELS}
                  value={simulateFailure}
                  onValueChange={(v) => setSimulateFailure(String(v))}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="false">Authorize (success)</SelectItem>
                    <SelectItem value="true">Simulate failure</SelectItem>
                  </SelectContent>
                </Select>
                <FieldDescription>Force a declined authorization for testing.</FieldDescription>
              </Field>
            </FieldGroup>

            <SheetFooter>
              <Button type="submit" disabled={createM.isPending}>
                {createM.isPending ? "Creating…" : "Create payment"}
              </Button>
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>
    </div>
  );
}
