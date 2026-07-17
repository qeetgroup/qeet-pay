import {
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  DataState,
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
  Input,
  Separator,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Textarea,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { RefreshCwIcon } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/page-header";
import { api } from "@/lib/api";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/refunds")({ component: RefundsPage });

type RefundStatus = "SUCCEEDED" | "FAILED";

type RefundView = {
  id: string;
  paymentId: string;
  amountMinor: number;
  status: string;
  ledgerEntryId: string | null;
};

function statusVariant(status: string) {
  return (status as RefundStatus) === "SUCCEEDED"
    ? ("success" as const)
    : ("destructive" as const);
}

function RefundsPage() {
  const qc = useQueryClient();

  const [paymentId, setPaymentId] = useState("");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [amountError, setAmountError] = useState<string | null>(null);
  const [paymentIdError, setPaymentIdError] = useState<string | null>(null);

  // The payment currently loaded into the refund history panel.
  const [activePaymentId, setActivePaymentId] = useState<string | null>(null);

  const refundsQ = useQuery({
    queryKey: ["refunds", activePaymentId],
    queryFn: () => api<RefundView[]>(`/v1/payments/${activePaymentId}/refunds`),
    enabled: activePaymentId !== null,
    staleTime: 10_000,
  });

  const refundM = useMutation({
    mutationFn: (vars: { id: string; body: { amountMinor: number; reason?: string } }) =>
      api<RefundView>(`/v1/payments/${vars.id}/refund`, { method: "POST", body: vars.body }),
    meta: { successMessage: "Refund issued" },
    onSuccess: (_r, vars) => {
      setActivePaymentId(vars.id);
      qc.invalidateQueries({ queryKey: ["refunds", vars.id] });
      setAmount("");
      setReason("");
    },
  });

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const id = paymentId.trim();
    if (!id) {
      setPaymentIdError("A payment ID is required.");
      return;
    }
    setPaymentIdError(null);
    const minor = rupeesToMinor(amount);
    if (minor === null || minor <= 0) {
      setAmountError("Enter a valid refund amount greater than zero.");
      return;
    }
    setAmountError(null);
    refundM.mutate({ id, body: { amountMinor: minor, reason: reason.trim() || undefined } });
  }

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Refund a captured payment in full or in part. Refunds post an offsetting ledger entry." />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Issue a refund</CardTitle>
            <CardDescription>
              Provide the payment ID and the amount to return to the customer.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={submit}>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="payment-id">Payment ID</FieldLabel>
                  <Input
                    id="payment-id"
                    value={paymentId}
                    onChange={(e) => setPaymentId(e.target.value)}
                    placeholder="Payment UUID to refund"
                    className="font-mono"
                  />
                  <FieldDescription>The captured payment you want to refund against.</FieldDescription>
                  <FieldError errors={paymentIdError ? [{ message: paymentIdError }] : undefined} />
                </Field>

                <Field>
                  <FieldLabel htmlFor="refund-amount">Amount</FieldLabel>
                  <Input
                    id="refund-amount"
                    type="number"
                    min="0"
                    step="0.01"
                    inputMode="decimal"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="0.00"
                  />
                  <FieldDescription>Rupees; converted to paise. Must not exceed the captured amount.</FieldDescription>
                  <FieldError errors={amountError ? [{ message: amountError }] : undefined} />
                </Field>

                <Field>
                  <FieldLabel htmlFor="reason">Reason</FieldLabel>
                  <Textarea
                    id="reason"
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    placeholder="Optional reason recorded with the refund"
                    rows={3}
                  />
                </Field>

                <Separator />

                <div className="flex items-center gap-2">
                  <Button type="submit" disabled={refundM.isPending}>
                    <RefreshCwIcon /> {refundM.isPending ? "Issuing…" : "Issue refund"}
                  </Button>
                  {paymentId.trim() && (
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setActivePaymentId(paymentId.trim())}
                    >
                      View refunds for this payment
                    </Button>
                  )}
                </div>
              </FieldGroup>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>How refunds work</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm text-muted-foreground">
            <p>
              Refunds are only valid against a <span className="font-medium text-foreground">CAPTURED</span>{" "}
              payment. A partial refund returns part of the captured amount; the rest stays settled.
            </p>
            <p>
              Each refund posts an offsetting double-entry to the ledger, so balances always reconcile —
              nothing is edited in place.
            </p>
            <p>
              A refund resolves to <Badge variant="success">SUCCEEDED</Badge> or{" "}
              <Badge variant="destructive">FAILED</Badge>. Failed refunds do not move money.
            </p>
          </CardContent>
        </Card>
      </div>

      {activePaymentId && (
        <Card>
          <CardHeader>
            <CardTitle>Refunds for payment</CardTitle>
            <CardDescription className="font-mono text-xs">{activePaymentId}</CardDescription>
          </CardHeader>
          <CardContent>
            <DataState
              isLoading={refundsQ.isLoading}
              isError={refundsQ.isError}
              error={refundsQ.error}
              isEmpty={(refundsQ.data ?? []).length === 0}
              emptyIcon={RefreshCwIcon}
              emptyTitle="No refunds"
              emptyDescription="This payment has no refunds yet."
            >
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Refund ID</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Ledger Entry</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(refundsQ.data ?? []).map((r) => (
                    <TableRow key={r.id}>
                      <TableCell className="font-mono text-xs">{r.id}</TableCell>
                      <TableCell className="text-right tabular-nums">{formatInr(r.amountMinor)}</TableCell>
                      <TableCell>
                        <Badge variant={statusVariant(r.status)}>{r.status}</Badge>
                      </TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">
                        {r.ledgerEntryId ?? "—"}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </DataState>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
