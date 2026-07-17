import {
  Badge,
  Button,
  Card,
  CardContent,
  DataState,
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
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
  TimeSince,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { LinkIcon, PlusIcon } from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/payment-links")({ component: PaymentLinksPage });

type PaymentMethod = "UPI" | "CARD" | "NET_BANKING" | "WALLET";
type LinkStatus = "ACTIVE" | "PAID" | "EXPIRED" | "CANCELLED";

type LinkView = {
  id: string;
  code: string;
  title: string;
  amountMinor: number | null;
  currency: string;
  reference: string | null;
  status: string;
  paymentId: string | null;
  expiresAt: string | null;
  createdAt: string | null;
  paidAt: string | null;
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

const STATUS_OPTIONS = ["ACTIVE", "PAID", "EXPIRED", "CANCELLED"].map((s) => ({
  label: s.charAt(0) + s.slice(1).toLowerCase(),
  value: s,
}));

function statusVariant(status: string) {
  switch (status as LinkStatus) {
    case "PAID":
      return "success" as const;
    case "ACTIVE":
      return "default" as const;
    case "EXPIRED":
      return "muted" as const;
    case "CANCELLED":
      return "destructive" as const;
    default:
      return "secondary" as const;
  }
}

function PaymentLinksPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [paying, setPaying] = useState<LinkView | null>(null);

  // Create form
  const [title, setTitle] = useState("");
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [reference, setReference] = useState("");
  const [expiresAt, setExpiresAt] = useState("");

  // Pay dialog form
  const [payMethod, setPayMethod] = useState<PaymentMethod>("UPI");
  const [payAmount, setPayAmount] = useState("");
  const [payAmountError, setPayAmountError] = useState<string | null>(null);

  const linksQ = useQuery({
    queryKey: ["payment-links"],
    queryFn: () => api<LinkView[]>("/v1/payment-links"),
    staleTime: 15_000,
  });

  const rows = linksQ.data ?? [];

  const {
    view,
    search,
    setSearch,
    filters,
    setFilter,
    sort,
    toggleSort,
    density,
    setDensity,
    hasActiveFilters,
    clear,
  } = useListView(rows, {
    searchFields: (l) => [l.code, l.title, l.reference],
    filterFields: { status: (l) => l.status },
    sortFields: {
      title: (l) => l.title,
      amount: (l) => l.amountMinor ?? -1,
      status: (l) => l.status,
      createdAt: (l) => l.createdAt ?? "",
    },
    initialDensity: "comfortable",
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["payment-links"] });

  const createM = useMutation({
    mutationFn: (body: {
      title: string;
      amountMinor?: number;
      currency: string;
      reference?: string;
      expiresAt?: string;
    }) => api<LinkView>("/v1/payment-links", { method: "POST", body }),
    meta: { successMessage: "Payment link created" },
    onSuccess: () => {
      invalidate();
      setOpen(false);
      setTitle("");
      setAmount("");
      setReference("");
      setExpiresAt("");
    },
  });

  const payM = useMutation({
    mutationFn: (vars: { code: string; body: { method: PaymentMethod; amountMinor?: number } }) =>
      api<LinkView>(`/v1/payment-links/${vars.code}/pay`, { method: "POST", body: vars.body }),
    meta: { successMessage: "Payment link paid" },
    onSuccess: () => {
      invalidate();
      setPaying(null);
      setPayAmount("");
    },
  });

  const cancelM = useMutation({
    mutationFn: (linkId: string) =>
      api<LinkView>(`/v1/payment-links/${linkId}/cancel`, { method: "POST" }),
    meta: { successMessage: "Payment link cancelled" },
    onSuccess: invalidate,
  });

  function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    const body: {
      title: string;
      amountMinor?: number;
      currency: string;
      reference?: string;
      expiresAt?: string;
    } = {
      title: title.trim(),
      currency: currency.trim().toUpperCase() || "INR",
      reference: reference.trim() || undefined,
      expiresAt: expiresAt ? new Date(expiresAt).toISOString() : undefined,
    };
    if (amount.trim()) {
      const minor = rupeesToMinor(amount);
      if (minor === null || minor <= 0) return;
      body.amountMinor = minor;
    }
    createM.mutate(body);
  }

  function submitPay(e: React.FormEvent) {
    e.preventDefault();
    if (!paying) return;
    const isOpen = paying.amountMinor == null;
    const body: { method: PaymentMethod; amountMinor?: number } = { method: payMethod };
    if (isOpen) {
      const minor = rupeesToMinor(payAmount);
      if (minor === null || minor <= 0) {
        setPayAmountError("Enter the amount the payer will be charged.");
        return;
      }
      body.amountMinor = minor;
    }
    setPayAmountError(null);
    payM.mutate({ code: paying.code, body });
  }

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Create shareable fixed- or open-amount links, then collect or revoke them."
        actions={
          <Button onClick={() => setOpen(true)}>
            <PlusIcon /> Create link
          </Button>
        }
      />

      <Card className="overflow-hidden p-0">
        <ListToolbar
          search={search}
          onSearchChange={setSearch}
          searchPlaceholder="Search code, title, reference…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: filters.status ?? "",
              options: STATUS_OPTIONS,
              onChange: (v) => setFilter("status", v),
            },
          ]}
          density={density}
          onDensityChange={setDensity}
          onExport={(fmt) => {
            if (fmt === "csv") {
              exportToCsv("payment-links", view, [
                { header: "Code", value: (l) => l.code },
                { header: "Title", value: (l) => l.title },
                { header: "Amount (minor)", value: (l) => l.amountMinor ?? "" },
                { header: "Currency", value: (l) => l.currency },
                { header: "Status", value: (l) => l.status },
                { header: "Reference", value: (l) => l.reference ?? "" },
                { header: "Created", value: (l) => l.createdAt ?? "" },
              ]);
            } else {
              exportToJson("payment-links", view);
            }
          }}
          exportDisabled={view.length === 0}
          hasActiveFilters={hasActiveFilters}
          onClear={clear}
        />
        <CardContent className="p-0">
          <DataState
            isLoading={linksQ.isLoading}
            isError={linksQ.isError}
            error={linksQ.error}
            isEmpty={view.length === 0}
            emptyIcon={LinkIcon}
            emptyTitle={hasActiveFilters ? "No matching links" : "No payment links yet"}
            emptyDescription={
              hasActiveFilters
                ? "Try clearing filters or search."
                : "Create your first payment link to start collecting."
            }
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <SortHeader columnKey="title" sort={sort} onToggle={toggleSort}>
                    Title
                  </SortHeader>
                  <SortHeader columnKey="amount" sort={sort} onToggle={toggleSort} className="text-right">
                    Amount
                  </SortHeader>
                  <SortHeader columnKey="status" sort={sort} onToggle={toggleSort}>
                    Status
                  </SortHeader>
                  <SortHeader columnKey="createdAt" sort={sort} onToggle={toggleSort}>
                    Created
                  </SortHeader>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {view.map((l) => (
                  <TableRow key={l.id} className={density === "compact" ? "[&>td]:py-1.5" : undefined}>
                    <TableCell className="font-mono text-xs font-medium">{l.code}</TableCell>
                    <TableCell>
                      <span className="font-medium">{l.title}</span>
                      {l.reference && (
                        <span className="block text-xs text-muted-foreground">{l.reference}</span>
                      )}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {l.amountMinor == null ? (
                        <Badge variant="outline">Open</Badge>
                      ) : (
                        formatInr(l.amountMinor, l.currency)
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(l.status)}>{l.status}</Badge>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {l.createdAt ? <TimeSince value={l.createdAt} /> : "—"}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end gap-2">
                        {l.status === "ACTIVE" && (
                          <>
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => {
                                setPaying(l);
                                setPayMethod("UPI");
                                setPayAmount("");
                                setPayAmountError(null);
                              }}
                            >
                              Pay
                            </Button>
                            <Button
                              size="sm"
                              variant="ghost"
                              disabled={cancelM.isPending}
                              onClick={() => cancelM.mutate(l.id)}
                            >
                              Cancel
                            </Button>
                          </>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </CardContent>
      </Card>

      {/* Create link Sheet */}
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="w-full sm:max-w-md">
          <form onSubmit={submitCreate} className="flex h-full flex-col">
            <SheetHeader>
              <SheetTitle>Create payment link</SheetTitle>
              <SheetDescription>
                Leave the amount blank for an open link where the payer chooses the amount.
              </SheetDescription>
            </SheetHeader>

            <FieldGroup className="flex-1 overflow-y-auto p-4">
              <Field>
                <FieldLabel htmlFor="title">Title</FieldLabel>
                <Input
                  id="title"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="e.g. Invoice #1024"
                  required
                />
              </Field>

              <Field>
                <FieldLabel htmlFor="link-amount">Amount</FieldLabel>
                <Input
                  id="link-amount"
                  type="number"
                  min="0"
                  step="0.01"
                  inputMode="decimal"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="Leave blank for open amount"
                />
                <FieldDescription>Rupees; converted to paise. Blank = open amount.</FieldDescription>
              </Field>

              <Field>
                <FieldLabel htmlFor="link-currency">Currency</FieldLabel>
                <Input
                  id="link-currency"
                  value={currency}
                  onChange={(e) => setCurrency(e.target.value)}
                  placeholder="INR"
                  maxLength={3}
                />
              </Field>

              <Field>
                <FieldLabel htmlFor="reference">Reference</FieldLabel>
                <Input
                  id="reference"
                  value={reference}
                  onChange={(e) => setReference(e.target.value)}
                  placeholder="Optional internal reference"
                />
              </Field>

              <Field>
                <FieldLabel htmlFor="expires">Expires at</FieldLabel>
                <Input
                  id="expires"
                  type="datetime-local"
                  value={expiresAt}
                  onChange={(e) => setExpiresAt(e.target.value)}
                />
                <FieldDescription>Optional — the link is revoked automatically after this.</FieldDescription>
              </Field>
            </FieldGroup>

            <SheetFooter>
              <Button type="submit" disabled={createM.isPending || !title.trim()}>
                {createM.isPending ? "Creating…" : "Create link"}
              </Button>
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>

      {/* Pay dialog */}
      <Dialog open={paying !== null} onOpenChange={(o) => !o && setPaying(null)}>
        <DialogContent>
          <form onSubmit={submitPay}>
            <DialogHeader>
              <DialogTitle>Collect payment</DialogTitle>
            </DialogHeader>
            <FieldGroup className="py-4">
              <Field>
                <FieldLabel>Method</FieldLabel>
                <Select
                  items={METHOD_LABELS}
                  value={payMethod}
                  onValueChange={(v) => setPayMethod(v as PaymentMethod)}
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
              {paying?.amountMinor == null && (
                <Field>
                  <FieldLabel htmlFor="pay-amount">Amount</FieldLabel>
                  <Input
                    id="pay-amount"
                    type="number"
                    min="0"
                    step="0.01"
                    inputMode="decimal"
                    value={payAmount}
                    onChange={(e) => setPayAmount(e.target.value)}
                    placeholder="0.00"
                  />
                  <FieldDescription>This is an open-amount link — enter the charge in rupees.</FieldDescription>
                  <FieldError errors={payAmountError ? [{ message: payAmountError }] : undefined} />
                </Field>
              )}
              {paying?.amountMinor != null && (
                <p className="text-sm text-muted-foreground">
                  Charging{" "}
                  <span className="font-medium text-foreground tabular-nums">
                    {formatInr(paying.amountMinor, paying.currency)}
                  </span>
                  .
                </p>
              )}
            </FieldGroup>
            <DialogFooter>
              <Button type="submit" disabled={payM.isPending}>
                {payM.isPending ? "Processing…" : "Pay now"}
              </Button>
              <DialogClose render={<Button type="button" variant="outline">Cancel</Button>} />
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
