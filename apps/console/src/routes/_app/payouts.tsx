import {
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  EmptyState,
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
import { useMutation } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { BanknoteIcon, PlusIcon, SearchIcon, UsersIcon } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/features/money/status";
import { api } from "@/lib/api";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/payouts")({ component: PayoutsPage });

type PayoutRail = "UPI" | "IMPS" | "NEFT" | "RTGS";

type PayoutView = {
  id: string;
  amountMinor: number;
  currency: string;
  rail: string;
  status: string;
  ledgerEntryId: string | null;
};

const RAILS: { value: PayoutRail; label: string }[] = [
  { value: "UPI", label: "UPI" },
  { value: "IMPS", label: "IMPS" },
  { value: "NEFT", label: "NEFT" },
  { value: "RTGS", label: "RTGS" },
];

function PayoutsPage() {
  const [rows, setRows] = useState<PayoutView[]>([]);
  const [open, setOpen] = useState(false);

  // Create form state
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [rail, setRail] = useState<PayoutRail>("UPI");
  const [destination, setDestination] = useState("");
  const [description, setDescription] = useState("");
  const [amountError, setAmountError] = useState<string | null>(null);
  const [destError, setDestError] = useState<string | null>(null);

  // Lookup form state
  const [lookupId, setLookupId] = useState("");

  const upsert = (p: PayoutView) =>
    setRows((prev) => {
      const idx = prev.findIndex((r) => r.id === p.id);
      if (idx === -1) return [p, ...prev];
      const next = [...prev];
      next[idx] = p;
      return next;
    });

  const createM = useMutation({
    mutationFn: (body: {
      amountMinor: number;
      currency: string;
      rail: PayoutRail;
      destination: string;
      description?: string;
    }) => api<PayoutView>("/v1/payouts", { method: "POST", body }),
    meta: { successMessage: "Payout staged for approval" },
    onSuccess: (p) => {
      upsert(p);
      setOpen(false);
      setAmount("");
      setDestination("");
      setDescription("");
    },
  });

  const approveM = useMutation({
    mutationFn: (id: string) => api<PayoutView>(`/v1/payouts/${id}/approve`, { method: "POST" }),
    meta: { successMessage: "Payout approved & disbursed" },
    onSuccess: (p) => upsert(p),
  });

  const rejectM = useMutation({
    mutationFn: (id: string) => api<PayoutView>(`/v1/payouts/${id}/reject`, { method: "POST" }),
    meta: { successMessage: "Payout rejected" },
    onSuccess: (p) => upsert(p),
  });

  const lookupM = useMutation({
    mutationFn: (id: string) => api<PayoutView>(`/v1/payouts/${id}`),
    meta: { successMessage: "Payout loaded" },
    onSuccess: (p) => {
      upsert(p);
      setLookupId("");
    },
  });

  function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(amount);
    let bad = false;
    if (minor === null || minor <= 0) {
      setAmountError("Enter a valid amount greater than zero.");
      bad = true;
    } else {
      setAmountError(null);
    }
    if (!destination.trim()) {
      setDestError("Enter a beneficiary (VPA, or account@IFSC).");
      bad = true;
    } else {
      setDestError(null);
    }
    if (bad || minor === null) return;
    createM.mutate({
      amountMinor: minor,
      currency: currency.trim().toUpperCase() || "INR",
      rail,
      destination: destination.trim(),
      description: description.trim() || undefined,
    });
  }

  const busy = approveM.isPending || rejectM.isPending;

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Disburse funds over UPI / IMPS / NEFT / RTGS. Each payout is staged for maker-checker approval before it posts to the ledger."
        actions={
          <div className="flex items-center gap-2">
            <Button variant="outline" render={<Link to={"/payout-batches" as never} />}>
              <UsersIcon /> Bulk batches
            </Button>
            <Button onClick={() => setOpen(true)}>
              <PlusIcon /> Create payout
            </Button>
          </div>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>Look up a payout</CardTitle>
          <CardDescription>Fetch a payout by ID to view its live status.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="flex flex-col gap-2 sm:flex-row"
            onSubmit={(e) => {
              e.preventDefault();
              if (lookupId.trim()) lookupM.mutate(lookupId.trim());
            }}
          >
            <Input
              value={lookupId}
              onChange={(e) => setLookupId(e.target.value)}
              placeholder="Payout ID (UUID)"
              className="sm:max-w-md"
              aria-label="Payout ID"
            />
            <Button type="submit" variant="outline" disabled={!lookupId.trim() || lookupM.isPending}>
              <SearchIcon /> Look up
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Payouts (this session)</CardTitle>
          <CardDescription>
            Payouts you created or looked up here. There is no server-side list endpoint yet, so this
            resets on reload — look one up by ID to bring it back.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {rows.length === 0 ? (
            <EmptyState
              icon={BanknoteIcon}
              title="No payouts yet"
              description="Create a payout or look one up by ID to see it here."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Payout ID</TableHead>
                  <TableHead>Rail</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Ledger Entry</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell className="font-mono text-xs">{p.id}</TableCell>
                    <TableCell>{p.rail}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatInr(p.amountMinor, p.currency)}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={p.status} />
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {p.ledgerEntryId ?? "—"}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end gap-2">
                        {p.status === "PENDING_APPROVAL" && (
                          <>
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={busy}
                              onClick={() => approveM.mutate(p.id)}
                            >
                              Approve
                            </Button>
                            <Button
                              size="sm"
                              variant="ghost"
                              disabled={busy}
                              onClick={() => rejectM.mutate(p.id)}
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
          )}
        </CardContent>
      </Card>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="w-full sm:max-w-md">
          <form onSubmit={submitCreate} className="flex h-full flex-col">
            <SheetHeader>
              <SheetTitle>Create payout</SheetTitle>
              <SheetDescription>
                Stages a payout as PENDING_APPROVAL. Approve it afterwards to disburse and post to the
                ledger.
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
                <FieldLabel>Rail</FieldLabel>
                <Select value={rail} onValueChange={(v) => setRail(v as PayoutRail)}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select a rail" />
                  </SelectTrigger>
                  <SelectContent>
                    {RAILS.map((r) => (
                      <SelectItem key={r.value} value={r.value}>
                        {r.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>

              <Field>
                <FieldLabel htmlFor="destination">Beneficiary</FieldLabel>
                <Input
                  id="destination"
                  value={destination}
                  onChange={(e) => setDestination(e.target.value)}
                  placeholder="name@upi  or  0001234567890@HDFC0000123"
                />
                <FieldDescription>
                  VPA for UPI, or account number @ IFSC for IMPS / NEFT / RTGS.
                </FieldDescription>
                <FieldError errors={destError ? [{ message: destError }] : undefined} />
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
                <FieldLabel htmlFor="description">Description</FieldLabel>
                <Textarea
                  id="description"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Optional note shown on the payout"
                  rows={3}
                />
              </Field>
            </FieldGroup>

            <SheetFooter>
              <Button type="submit" disabled={createM.isPending}>
                {createM.isPending ? "Creating…" : "Create payout"}
              </Button>
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>
    </div>
  );
}
