import {
  Button,
  Card,
  CardContent,
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
import { CalendarClockIcon, PlusIcon } from "lucide-react";
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
  todayIso,
} from "@/features/finance/shared";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatBps, formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/bnpl")({ component: BnplPage });

type Agreement = {
  id: string;
  customerRef: string;
  orderRef: string;
  currency: string;
  orderAmountMinor: number;
  interestBps: number;
  totalPayableMinor: number;
  installmentsCount: number;
  paidInstallments: number;
  status: string;
  createdAt: string;
  settledAt: string | null;
};

type Installment = {
  seq: number;
  dueDate: string;
  amountMinor: number;
  status: string;
  paidAt: string | null;
};

type AgreementView = { agreement: Agreement; installments: Installment[] };

function BnplPage() {
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const listQ = useQuery({
    queryKey: ["bnpl-agreements"],
    queryFn: () => api<Agreement[]>("/v1/bnpl/agreements"),
  });

  const rows = listQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (a) => [a.id, a.customerRef, a.orderRef, a.status],
    filterFields: { status: (a) => a.status },
    sortFields: {
      amount: (a) => a.orderAmountMinor,
      payable: (a) => a.totalPayableMinor,
      created: (a) => a.createdAt,
    },
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Split a customer's order into installments while funding the merchant upfront."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon /> Create agreement
          </Button>
        }
      />

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search customer, order, id…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: [
                { label: "Active", value: "ACTIVE" },
                { label: "Settled", value: "SETTLED" },
                { label: "Cancelled", value: "CANCELLED" },
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
              { header: "Agreement", value: (a: Agreement) => a.id },
              { header: "Customer", value: (a: Agreement) => a.customerRef },
              { header: "Order", value: (a: Agreement) => a.orderRef },
              { header: "Currency", value: (a: Agreement) => a.currency },
              { header: "Order amount", value: (a: Agreement) => a.orderAmountMinor },
              { header: "Interest bps", value: (a: Agreement) => a.interestBps },
              { header: "Total payable", value: (a: Agreement) => a.totalPayableMinor },
              { header: "Installments", value: (a: Agreement) => a.installmentsCount },
              { header: "Paid", value: (a: Agreement) => a.paidInstallments },
              { header: "Status", value: (a: Agreement) => a.status },
              { header: "Created", value: (a: Agreement) => a.createdAt },
            ];
            if (format === "csv") exportToCsv("bnpl-agreements", lv.view, cols);
            else exportToJson("bnpl-agreements", lv.view);
          }}
        />
        <CardContent className="p-0">
          <DataState
            isLoading={listQ.isLoading}
            isError={listQ.isError}
            error={listQ.error}
            isEmpty={lv.view.length === 0}
            emptyIcon={CalendarClockIcon}
            emptyTitle="No agreements yet"
            emptyDescription="Create a BNPL agreement to split an order into installments."
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Customer</TableHead>
                  <TableHead>Order</TableHead>
                  <SortHeader columnKey="amount" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Order amount
                  </SortHeader>
                  <TableHead className="text-end">Interest</TableHead>
                  <SortHeader columnKey="payable" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Total payable
                  </SortHeader>
                  <TableHead className="text-end">Installments</TableHead>
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
                    <TableCell className="font-medium">{a.customerRef}</TableCell>
                    <TableCell>{a.orderRef}</TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(a.orderAmountMinor, a.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">{formatBps(a.interestBps)}</TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(a.totalPayableMinor, a.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {a.paidInstallments}/{a.installmentsCount}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={a.status} />
                    </TableCell>
                    <TableCell>
                      <TimeSince value={a.createdAt} />
                    </TableCell>
                    <TableCell className="text-end">
                      <Button variant="outline" size="sm" onClick={() => setSelectedId(a.id)}>
                        View
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </CardContent>
      </Card>

      <CreateAgreementSheet open={createOpen} onOpenChange={setCreateOpen} />
      <AgreementDetailSheet agreementId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}

function CreateAgreementSheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const qc = useQueryClient();
  const [customerRef, setCustomerRef] = useState("");
  const [orderRef, setOrderRef] = useState("");
  const [orderAmount, setOrderAmount] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [installments, setInstallments] = useState("3");
  const [interestBps, setInterestBps] = useState("0");
  const [firstDueDate, setFirstDueDate] = useState(todayIso());
  const [amountError, setAmountError] = useState<string>();

  const mut = useMutation({
    mutationFn: (orderAmountMinor: number) =>
      api<AgreementView>("/v1/bnpl/agreements", {
        method: "POST",
        body: {
          customerRef,
          orderRef,
          orderAmountMinor,
          currency,
          installments: Number(installments),
          interestBps: Number(interestBps),
          firstDueDate,
        },
      }),
    meta: { successMessage: "Agreement created" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["bnpl-agreements"] });
      setCustomerRef("");
      setOrderRef("");
      setOrderAmount("");
      onOpenChange(false);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(orderAmount);
    if (minor === null || minor === 0) {
      setAmountError("Enter a valid order amount.");
      return;
    }
    setAmountError(undefined);
    mut.mutate(minor);
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Create BNPL agreement</SheetTitle>
          <SheetDescription>The merchant is funded now; the customer repays in installments.</SheetDescription>
        </SheetHeader>
        <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
          <div className="flex-1 space-y-4 overflow-y-auto px-4">
            <TextField
              id="customer-ref"
              label="Customer reference"
              value={customerRef}
              onChange={setCustomerRef}
              placeholder="cust_123"
              required
            />
            <TextField
              id="order-ref"
              label="Order reference"
              value={orderRef}
              onChange={setOrderRef}
              placeholder="order_456"
              required
            />
            <MoneyField
              id="order-amount"
              label="Order amount"
              value={orderAmount}
              onChange={setOrderAmount}
              required
              error={amountError}
            />
            <CurrencySelect value={currency} onChange={setCurrency} />
            <TextField
              id="installments"
              label="Installments"
              value={installments}
              onChange={setInstallments}
              type="number"
              inputMode="numeric"
              required
            />
            <TextField
              id="interest-bps"
              label="Interest (basis points)"
              value={interestBps}
              onChange={setInterestBps}
              type="number"
              inputMode="numeric"
              description="100 bps = 1%. Applied across the schedule."
              required
            />
            <TextField
              id="first-due-date"
              label="First due date"
              value={firstDueDate}
              onChange={setFirstDueDate}
              type="date"
              required
            />
            <FormError error={mut.error} />
          </div>
          <SheetFooter>
            <Button type="submit" disabled={mut.isPending}>
              {mut.isPending ? "Creating…" : "Create agreement"}
            </Button>
            <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function AgreementDetailSheet({
  agreementId,
  onClose,
}: {
  agreementId: string | null;
  onClose: () => void;
}) {
  const qc = useQueryClient();

  const detailQ = useQuery({
    queryKey: ["bnpl-agreement", agreementId],
    queryFn: () => api<AgreementView>(`/v1/bnpl/agreements/${agreementId}`),
    enabled: agreementId !== null,
  });

  const payM = useMutation({
    mutationFn: (seq: number) =>
      api<AgreementView>(`/v1/bnpl/agreements/${agreementId}/installments/${seq}/pay`, {
        method: "POST",
      }),
    meta: { successMessage: "Installment paid" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["bnpl-agreement", agreementId] });
      qc.invalidateQueries({ queryKey: ["bnpl-agreements"] });
    },
  });

  const agreement = detailQ.data?.agreement;

  return (
    <Sheet open={agreementId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Agreement {agreement ? agreement.id.slice(0, 8) : ""}</SheetTitle>
          <SheetDescription>Installment schedule and repayment status.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto px-4">
          <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error}>
            {agreement && (
              <>
                <div>
                  <DetailRow label="Status" value={<StatusBadge status={agreement.status} />} />
                  <DetailRow label="Customer" value={agreement.customerRef} />
                  <DetailRow label="Order" value={agreement.orderRef} />
                  <DetailRow label="Order amount" value={formatInr(agreement.orderAmountMinor, agreement.currency)} />
                  <DetailRow label="Interest" value={formatBps(agreement.interestBps)} />
                  <DetailRow label="Total payable" value={formatInr(agreement.totalPayableMinor, agreement.currency)} />
                  <DetailRow
                    label="Installments paid"
                    value={`${agreement.paidInstallments} / ${agreement.installmentsCount}`}
                  />
                </div>

                <div>
                  <p className="mb-2 text-sm font-medium">Schedule</p>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>#</TableHead>
                        <TableHead>Due</TableHead>
                        <TableHead className="text-end">Amount</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead className="text-end">Action</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {(detailQ.data?.installments ?? []).map((i) => (
                        <TableRow key={i.seq}>
                          <TableCell className="tabular-nums">{i.seq}</TableCell>
                          <TableCell className="tabular-nums">{i.dueDate}</TableCell>
                          <TableCell className="text-end tabular-nums">
                            {formatInr(i.amountMinor, agreement.currency)}
                          </TableCell>
                          <TableCell>
                            <StatusBadge status={i.status} />
                          </TableCell>
                          <TableCell className="text-end">
                            {i.status === "PENDING" && (
                              <Button
                                size="sm"
                                onClick={() => payM.mutate(i.seq)}
                                disabled={payM.isPending}
                              >
                                Pay
                              </Button>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
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
