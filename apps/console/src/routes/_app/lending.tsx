import {
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  DataState,
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
  TimeSince,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { HandCoinsIcon, PlusIcon } from "lucide-react";
import { useState, type FormEvent } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import {
  CurrencySelect,
  DetailRow,
  FormError,
  MoneyField,
  ShortId,
  StatusBadge,
  TextField,
} from "@/features/finance/shared";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatBps, formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/lending")({ component: LendingPage });

type Offer = {
  id: string;
  currency: string;
  principalMinor: number;
  feeBps: number;
  feeMinor: number;
  totalRepayableMinor: number;
  repaymentPercentBps: number;
  status: string;
  expiresAt: string;
  createdAt: string;
};

type Loan = {
  id: string;
  offerId: string;
  currency: string;
  principalMinor: number;
  feeMinor: number;
  totalRepayableMinor: number;
  outstandingMinor: number;
  repaymentPercentBps: number;
  status: string;
  disbursedEntryId: string;
  disbursedAt: string;
  repaidAt: string | null;
};

type Repayment = {
  settlementAmountMinor: number;
  sweptMinor: number;
  ledgerEntryId: string;
  createdAt: string;
};

type LoanView = { loan: Loan; repayments: Repayment[] };

function LendingPage() {
  const qc = useQueryClient();
  const [requestOpen, setRequestOpen] = useState(false);
  const [selectedLoanId, setSelectedLoanId] = useState<string | null>(null);

  const offersQ = useQuery({
    queryKey: ["lending-offers"],
    queryFn: () => api<Offer[]>("/v1/lending/offers"),
  });
  const loansQ = useQuery({
    queryKey: ["lending-loans"],
    queryFn: () => api<Loan[]>("/v1/lending/loans"),
  });

  const acceptM = useMutation({
    mutationFn: (offerId: string) =>
      api<LoanView>(`/v1/lending/offers/${offerId}/accept`, { method: "POST" }),
    meta: { successMessage: "Offer accepted — advance disbursed" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["lending-offers"] });
      qc.invalidateQueries({ queryKey: ["lending-loans"] });
    },
  });

  const loans = loansQ.data ?? [];
  const lv = useListView(loans, {
    searchFields: (l) => [l.id, l.offerId, l.status, l.currency],
    filterFields: { status: (l) => l.status },
    sortFields: {
      principal: (l) => l.principalMinor,
      outstanding: (l) => l.outstandingMinor,
      disbursed: (l) => l.disbursedAt,
    },
  });

  const openOffers = (offersQ.data ?? []).filter((o) => o.status === "OFFERED");

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Underwritten working-capital advances repaid as a share of daily settlement."
        actions={
          <Button onClick={() => setRequestOpen(true)}>
            <PlusIcon /> Request offer
          </Button>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>Offers</CardTitle>
          <CardDescription>Pending advance offers — accept to disburse funds.</CardDescription>
        </CardHeader>
        <CardContent>
          <DataState
            isLoading={offersQ.isLoading}
            isError={offersQ.isError}
            error={offersQ.error}
            isEmpty={openOffers.length === 0}
            emptyIcon={HandCoinsIcon}
            emptyTitle="No open offers"
            emptyDescription="Request an offer to see your available advance terms."
            skeletonRows={3}
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Offer</TableHead>
                  <TableHead className="text-end">Principal</TableHead>
                  <TableHead className="text-end">Fee</TableHead>
                  <TableHead className="text-end">Repayable</TableHead>
                  <TableHead className="text-end">Sweep</TableHead>
                  <TableHead>Expires</TableHead>
                  <TableHead className="text-end">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {openOffers.map((o) => (
                  <TableRow key={o.id}>
                    <TableCell>
                      <ShortId id={o.id} />
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(o.principalMinor, o.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(o.feeMinor, o.currency)}{" "}
                      <span className="text-xs text-muted-foreground">({formatBps(o.feeBps)})</span>
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(o.totalRepayableMinor, o.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatBps(o.repaymentPercentBps)}
                    </TableCell>
                    <TableCell>
                      <TimeSince value={o.expiresAt} />
                    </TableCell>
                    <TableCell className="text-end">
                      <Button
                        size="sm"
                        onClick={() => acceptM.mutate(o.id)}
                        disabled={acceptM.isPending}
                      >
                        Accept
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </CardContent>
      </Card>

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search loans…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: [
                { label: "Active", value: "ACTIVE" },
                { label: "Repaid", value: "REPAID" },
                { label: "Written off", value: "WRITTEN_OFF" },
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
              { header: "Loan", value: (l: Loan) => l.id },
              { header: "Currency", value: (l: Loan) => l.currency },
              { header: "Principal", value: (l: Loan) => l.principalMinor },
              { header: "Fee", value: (l: Loan) => l.feeMinor },
              { header: "Repayable", value: (l: Loan) => l.totalRepayableMinor },
              { header: "Outstanding", value: (l: Loan) => l.outstandingMinor },
              { header: "Sweep bps", value: (l: Loan) => l.repaymentPercentBps },
              { header: "Status", value: (l: Loan) => l.status },
              { header: "Disbursed", value: (l: Loan) => l.disbursedAt },
            ];
            if (format === "csv") exportToCsv("lending-loans", lv.view, cols);
            else exportToJson("lending-loans", lv.view);
          }}
        />
        <CardContent className="p-0">
          <DataState
            isLoading={loansQ.isLoading}
            isError={loansQ.isError}
            error={loansQ.error}
            isEmpty={lv.view.length === 0}
            emptyIcon={HandCoinsIcon}
            emptyTitle="No loans yet"
            emptyDescription="Accept an offer to disburse your first advance."
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Loan</TableHead>
                  <SortHeader columnKey="principal" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Principal
                  </SortHeader>
                  <TableHead className="text-end">Fee</TableHead>
                  <TableHead className="text-end">Repayable</TableHead>
                  <SortHeader columnKey="outstanding" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Outstanding
                  </SortHeader>
                  <TableHead className="text-end">Sweep</TableHead>
                  <TableHead>Status</TableHead>
                  <SortHeader columnKey="disbursed" sort={lv.sort} onToggle={lv.toggleSort}>
                    Disbursed
                  </SortHeader>
                  <TableHead className="text-end">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((l) => (
                  <TableRow key={l.id}>
                    <TableCell>
                      <ShortId id={l.id} />
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(l.principalMinor, l.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(l.feeMinor, l.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(l.totalRepayableMinor, l.currency)}
                    </TableCell>
                    <TableCell className="text-end font-medium tabular-nums">
                      {formatInr(l.outstandingMinor, l.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatBps(l.repaymentPercentBps)}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={l.status} />
                    </TableCell>
                    <TableCell>
                      <TimeSince value={l.disbursedAt} />
                    </TableCell>
                    <TableCell className="text-end">
                      <Button variant="outline" size="sm" onClick={() => setSelectedLoanId(l.id)}>
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

      <RequestOfferSheet open={requestOpen} onOpenChange={setRequestOpen} />
      <LoanDetailSheet loanId={selectedLoanId} onClose={() => setSelectedLoanId(null)} />
    </div>
  );
}

function RequestOfferSheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const qc = useQueryClient();
  const [currency, setCurrency] = useState("INR");
  const [volume, setVolume] = useState("");
  const [error, setError] = useState<string>();

  const mut = useMutation({
    mutationFn: (avgMonthlyVolumeMinor: number) =>
      api<Offer>("/v1/lending/offers", {
        method: "POST",
        body: { currency, avgMonthlyVolumeMinor },
      }),
    meta: { successMessage: "Offer generated" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["lending-offers"] });
      setVolume("");
      onOpenChange(false);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(volume);
    if (minor === null) {
      setError("Enter a valid amount.");
      return;
    }
    setError(undefined);
    mut.mutate(minor);
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Request advance offer</SheetTitle>
          <SheetDescription>
            We underwrite terms from your average monthly settlement volume.
          </SheetDescription>
        </SheetHeader>
        <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
          <div className="flex-1 space-y-4 overflow-y-auto px-4">
            <CurrencySelect value={currency} onChange={setCurrency} />
            <MoneyField
              id="avg-volume"
              label="Average monthly volume"
              value={volume}
              onChange={setVolume}
              description="Trailing monthly captured volume used to size the advance."
              required
              error={error}
            />
            <FormError error={mut.error} />
          </div>
          <SheetFooter>
            <Button type="submit" disabled={mut.isPending}>
              {mut.isPending ? "Requesting…" : "Request offer"}
            </Button>
            <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function LoanDetailSheet({
  loanId,
  onClose,
}: {
  loanId: string | null;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const [amount, setAmount] = useState("");
  const [sourceRef, setSourceRef] = useState("");
  const [error, setError] = useState<string>();

  const detailQ = useQuery({
    queryKey: ["lending-loan", loanId],
    queryFn: () => api<LoanView>(`/v1/lending/loans/${loanId}`),
    enabled: loanId !== null,
  });

  const repayM = useMutation({
    mutationFn: (settlementAmountMinor: number) =>
      api<LoanView>(`/v1/lending/loans/${loanId}/repayments`, {
        method: "POST",
        body: { settlementAmountMinor, sourceRef: sourceRef || undefined },
      }),
    meta: { successMessage: "Repayment applied" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["lending-loan", loanId] });
      qc.invalidateQueries({ queryKey: ["lending-loans"] });
      setAmount("");
      setSourceRef("");
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(amount);
    if (minor === null || minor === 0) {
      setError("Enter a valid settlement amount.");
      return;
    }
    setError(undefined);
    repayM.mutate(minor);
  }

  const loan = detailQ.data?.loan;

  return (
    <Sheet open={loanId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Loan {loan ? loan.id.slice(0, 8) : ""}</SheetTitle>
          <SheetDescription>Outstanding balance, repayments, and settlement sweeps.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto px-4">
          <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error}>
            {loan && (
              <>
                <div>
                  <DetailRow label="Status" value={<StatusBadge status={loan.status} />} />
                  <DetailRow label="Principal" value={formatInr(loan.principalMinor, loan.currency)} />
                  <DetailRow label="Fee" value={formatInr(loan.feeMinor, loan.currency)} />
                  <DetailRow label="Total repayable" value={formatInr(loan.totalRepayableMinor, loan.currency)} />
                  <DetailRow label="Outstanding" value={formatInr(loan.outstandingMinor, loan.currency)} />
                  <DetailRow label="Settlement sweep" value={formatBps(loan.repaymentPercentBps)} />
                </div>

                {loan.status === "ACTIVE" && (
                  <form onSubmit={submit} className="space-y-4 rounded-lg border p-3">
                    <p className="text-sm font-medium">Apply repayment</p>
                    <MoneyField
                      id="repay-amount"
                      label="Settlement amount"
                      value={amount}
                      onChange={setAmount}
                      description="A share of this settlement is swept toward the outstanding balance."
                      required
                      error={error}
                    />
                    <TextField
                      id="repay-source"
                      label="Source reference"
                      value={sourceRef}
                      onChange={setSourceRef}
                      placeholder="Optional (e.g. settlement batch id)"
                    />
                    <FormError error={repayM.error} />
                    <Button type="submit" size="sm" disabled={repayM.isPending}>
                      {repayM.isPending ? "Applying…" : "Apply repayment"}
                    </Button>
                  </form>
                )}

                <div>
                  <p className="mb-2 text-sm font-medium">Repayments</p>
                  {detailQ.data && detailQ.data.repayments.length > 0 ? (
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead className="text-end">Settlement</TableHead>
                          <TableHead className="text-end">Swept</TableHead>
                          <TableHead>When</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {detailQ.data.repayments.map((r) => (
                          <TableRow key={r.ledgerEntryId}>
                            <TableCell className="text-end tabular-nums">
                              {formatInr(r.settlementAmountMinor, loan.currency)}
                            </TableCell>
                            <TableCell className="text-end tabular-nums">
                              {formatInr(r.sweptMinor, loan.currency)}
                            </TableCell>
                            <TableCell>
                              <TimeSince value={r.createdAt} />
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  ) : (
                    <p className="text-sm text-muted-foreground">No repayments recorded yet.</p>
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
