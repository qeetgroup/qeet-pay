import {
  Button,
  Card,
  CardContent,
  DataState,
  Field,
  FieldLabel,
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
  Textarea,
  TimeSince,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { PiggyBankIcon, PlusIcon } from "lucide-react";
import { useState, type FormEvent } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import {
  CurrencySelect,
  DetailRow,
  FormError,
  MoneyField,
  StatusBadge,
  TextField,
} from "@/features/finance/shared";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/escrow")({ component: EscrowPage });

type EscrowAgreement = {
  id: string;
  buyerRef: string;
  sellerRef: string;
  currency: string;
  amountMinor: number;
  releasedMinor: number;
  refundedMinor: number;
  remainingMinor: number;
  status: string;
  createdAt: string;
  closedAt: string | null;
};

type EscrowEvent = {
  type: string;
  amountMinor: number;
  ledgerEntryId: string;
  note: string | null;
  createdAt: string;
};

type AgreementView = { escrow: EscrowAgreement; events: EscrowEvent[] };

function EscrowPage() {
  const [holdOpen, setHoldOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const listQ = useQuery({
    queryKey: ["escrow"],
    queryFn: () => api<EscrowAgreement[]>("/v1/escrow"),
  });

  const rows = listQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (a) => [a.id, a.buyerRef, a.sellerRef, a.status],
    filterFields: { status: (a) => a.status },
    sortFields: {
      amount: (a) => a.amountMinor,
      remaining: (a) => a.remainingMinor,
      created: (a) => a.createdAt,
    },
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Hold a buyer's funds and release to the seller or refund on confirmation."
        actions={
          <Button onClick={() => setHoldOpen(true)}>
            <PlusIcon /> Hold funds
          </Button>
        }
      />

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search buyer, seller, id…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: [
                { label: "Held", value: "HELD" },
                { label: "Released", value: "RELEASED" },
                { label: "Refunded", value: "REFUNDED" },
                { label: "Settled", value: "SETTLED" },
              ],
              onChange: (v) => lv.setFilter("status", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          exportDisabled={lv.view.length === 0}
          onExport={(format) => {
            const cols = [
              { header: "Escrow", value: (a: EscrowAgreement) => a.id },
              { header: "Buyer", value: (a: EscrowAgreement) => a.buyerRef },
              { header: "Seller", value: (a: EscrowAgreement) => a.sellerRef },
              { header: "Currency", value: (a: EscrowAgreement) => a.currency },
              { header: "Amount", value: (a: EscrowAgreement) => a.amountMinor },
              { header: "Released", value: (a: EscrowAgreement) => a.releasedMinor },
              { header: "Refunded", value: (a: EscrowAgreement) => a.refundedMinor },
              { header: "Remaining", value: (a: EscrowAgreement) => a.remainingMinor },
              { header: "Status", value: (a: EscrowAgreement) => a.status },
              { header: "Created", value: (a: EscrowAgreement) => a.createdAt },
            ];
            if (format === "csv") exportToCsv("escrow", lv.view, cols);
            else exportToJson("escrow", lv.view);
          }}
        />
        <CardContent className="p-0">
          <DataState
            isLoading={listQ.isLoading}
            isError={listQ.isError}
            error={listQ.error}
            isEmpty={lv.view.length === 0}
            emptyIcon={PiggyBankIcon}
            emptyTitle="No escrow agreements"
            emptyDescription="Hold a buyer's funds to open a conditional escrow."
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Buyer</TableHead>
                  <TableHead>Seller</TableHead>
                  <SortHeader columnKey="amount" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Amount
                  </SortHeader>
                  <TableHead className="text-end">Released</TableHead>
                  <TableHead className="text-end">Refunded</TableHead>
                  <SortHeader columnKey="remaining" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Remaining
                  </SortHeader>
                  <TableHead>Status</TableHead>
                  <SortHeader columnKey="created" sort={lv.sort} onToggle={lv.toggleSort}>
                    Created
                  </SortHeader>
                  <TableHead className="text-end">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell className="font-medium">{a.buyerRef}</TableCell>
                    <TableCell>{a.sellerRef}</TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(a.amountMinor, a.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(a.releasedMinor, a.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(a.refundedMinor, a.currency)}
                    </TableCell>
                    <TableCell className="text-end font-medium tabular-nums">
                      {formatInr(a.remainingMinor, a.currency)}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={a.status} />
                    </TableCell>
                    <TableCell>
                      <TimeSince value={a.createdAt} />
                    </TableCell>
                    <TableCell className="text-end">
                      <Button variant="outline" size="sm" onClick={() => setSelectedId(a.id)}>
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

      <HoldSheet open={holdOpen} onOpenChange={setHoldOpen} />
      <EscrowDetailSheet escrowId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}

function HoldSheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const qc = useQueryClient();
  const [buyerRef, setBuyerRef] = useState("");
  const [sellerRef, setSellerRef] = useState("");
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [description, setDescription] = useState("");
  const [amountError, setAmountError] = useState<string>();

  const mut = useMutation({
    mutationFn: (amountMinor: number) =>
      api<AgreementView>("/v1/escrow", {
        method: "POST",
        body: { buyerRef, sellerRef, amountMinor, currency, description: description || undefined },
      }),
    meta: { successMessage: "Funds held in escrow" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["escrow"] });
      setBuyerRef("");
      setSellerRef("");
      setAmount("");
      setDescription("");
      onOpenChange(false);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(amount);
    if (minor === null || minor === 0) {
      setAmountError("Enter a valid amount.");
      return;
    }
    setAmountError(undefined);
    mut.mutate(minor);
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Hold funds</SheetTitle>
          <SheetDescription>Open a conditional escrow between a buyer and seller.</SheetDescription>
        </SheetHeader>
        <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
          <div className="flex-1 space-y-4 overflow-y-auto px-4">
            <TextField
              id="buyer-ref"
              label="Buyer reference"
              value={buyerRef}
              onChange={setBuyerRef}
              placeholder="buyer_123"
              required
            />
            <TextField
              id="seller-ref"
              label="Seller reference"
              value={sellerRef}
              onChange={setSellerRef}
              placeholder="seller_456"
              required
            />
            <MoneyField
              id="hold-amount"
              label="Amount"
              value={amount}
              onChange={setAmount}
              required
              error={amountError}
            />
            <CurrencySelect value={currency} onChange={setCurrency} />
            <Field>
              <FieldLabel htmlFor="hold-description">Description</FieldLabel>
              <Textarea
                id="hold-description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Optional — what the hold is for"
                rows={3}
              />
            </Field>
            <FormError error={mut.error} />
          </div>
          <SheetFooter>
            <Button type="submit" disabled={mut.isPending}>
              {mut.isPending ? "Holding…" : "Hold funds"}
            </Button>
            <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function EscrowDetailSheet({ escrowId, onClose }: { escrowId: string | null; onClose: () => void }) {
  const qc = useQueryClient();
  const [releaseAmount, setReleaseAmount] = useState("");
  const [releaseNote, setReleaseNote] = useState("");
  const [releaseError, setReleaseError] = useState<string>();
  const [refundAmount, setRefundAmount] = useState("");
  const [refundNote, setRefundNote] = useState("");
  const [refundError, setRefundError] = useState<string>();

  const detailQ = useQuery({
    queryKey: ["escrow-agreement", escrowId],
    queryFn: () => api<AgreementView>(`/v1/escrow/${escrowId}`),
    enabled: escrowId !== null,
  });

  function invalidate() {
    qc.invalidateQueries({ queryKey: ["escrow-agreement", escrowId] });
    qc.invalidateQueries({ queryKey: ["escrow"] });
  }

  const releaseM = useMutation({
    mutationFn: (amountMinor: number) =>
      api<AgreementView>(`/v1/escrow/${escrowId}/release`, {
        method: "POST",
        body: { amountMinor, note: releaseNote || undefined },
      }),
    meta: { successMessage: "Released to seller" },
    onSuccess: () => {
      invalidate();
      setReleaseAmount("");
      setReleaseNote("");
    },
  });

  const refundM = useMutation({
    mutationFn: (amountMinor: number) =>
      api<AgreementView>(`/v1/escrow/${escrowId}/refund`, {
        method: "POST",
        body: { amountMinor, note: refundNote || undefined },
      }),
    meta: { successMessage: "Refunded to buyer" },
    onSuccess: () => {
      invalidate();
      setRefundAmount("");
      setRefundNote("");
    },
  });

  function submitRelease(e: FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(releaseAmount);
    if (minor === null || minor === 0) {
      setReleaseError("Enter a valid amount.");
      return;
    }
    setReleaseError(undefined);
    releaseM.mutate(minor);
  }

  function submitRefund(e: FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(refundAmount);
    if (minor === null || minor === 0) {
      setRefundError("Enter a valid amount.");
      return;
    }
    setRefundError(undefined);
    refundM.mutate(minor);
  }

  const escrow = detailQ.data?.escrow;
  const canMove = escrow ? escrow.remainingMinor > 0 : false;

  return (
    <Sheet open={escrowId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Escrow {escrow ? escrow.id.slice(0, 8) : ""}</SheetTitle>
          <SheetDescription>Hold status, movements, and event history.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto px-4">
          <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error}>
            {escrow && (
              <>
                <div>
                  <DetailRow label="Status" value={<StatusBadge status={escrow.status} />} />
                  <DetailRow label="Buyer" value={escrow.buyerRef} />
                  <DetailRow label="Seller" value={escrow.sellerRef} />
                  <DetailRow label="Held amount" value={formatInr(escrow.amountMinor, escrow.currency)} />
                  <DetailRow label="Released" value={formatInr(escrow.releasedMinor, escrow.currency)} />
                  <DetailRow label="Refunded" value={formatInr(escrow.refundedMinor, escrow.currency)} />
                  <DetailRow label="Remaining" value={formatInr(escrow.remainingMinor, escrow.currency)} />
                </div>

                {canMove && (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <form onSubmit={submitRelease} className="space-y-3 rounded-lg border p-3">
                      <p className="text-sm font-medium">Release to seller</p>
                      <MoneyField
                        id="release-amount"
                        label="Amount"
                        value={releaseAmount}
                        onChange={setReleaseAmount}
                        required
                        error={releaseError}
                      />
                      <TextField
                        id="release-note"
                        label="Note"
                        value={releaseNote}
                        onChange={setReleaseNote}
                        placeholder="Optional"
                      />
                      <FormError error={releaseM.error} />
                      <Button type="submit" size="sm" disabled={releaseM.isPending}>
                        {releaseM.isPending ? "Releasing…" : "Release"}
                      </Button>
                    </form>
                    <form onSubmit={submitRefund} className="space-y-3 rounded-lg border p-3">
                      <p className="text-sm font-medium">Refund to buyer</p>
                      <MoneyField
                        id="refund-amount"
                        label="Amount"
                        value={refundAmount}
                        onChange={setRefundAmount}
                        required
                        error={refundError}
                      />
                      <TextField
                        id="refund-note"
                        label="Note"
                        value={refundNote}
                        onChange={setRefundNote}
                        placeholder="Optional"
                      />
                      <FormError error={refundM.error} />
                      <Button type="submit" variant="outline" size="sm" disabled={refundM.isPending}>
                        {refundM.isPending ? "Refunding…" : "Refund"}
                      </Button>
                    </form>
                  </div>
                )}

                <div>
                  <p className="mb-2 text-sm font-medium">Events</p>
                  {detailQ.data && detailQ.data.events.length > 0 ? (
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Type</TableHead>
                          <TableHead className="text-end">Amount</TableHead>
                          <TableHead>Note</TableHead>
                          <TableHead>When</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {detailQ.data.events.map((ev) => (
                          <TableRow key={ev.ledgerEntryId}>
                            <TableCell>
                              <StatusBadge status={ev.type} />
                            </TableCell>
                            <TableCell className="text-end tabular-nums">
                              {formatInr(ev.amountMinor, escrow.currency)}
                            </TableCell>
                            <TableCell className="text-muted-foreground">{ev.note ?? "—"}</TableCell>
                            <TableCell>
                              <TimeSince value={ev.createdAt} />
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  ) : (
                    <p className="text-sm text-muted-foreground">No events yet.</p>
                  )}
                </div>
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
