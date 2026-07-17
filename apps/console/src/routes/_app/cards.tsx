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
import { CreditCardIcon, PlusIcon } from "lucide-react";
import { useState, type FormEvent } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import {
  CurrencySelect,
  DetailRow,
  FormError,
  MoneyField,
  SelectField,
  StatusBadge,
  TextField,
} from "@/features/finance/shared";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/cards")({ component: CardsPage });

type VirtualCard = {
  id: string;
  holderRef: string;
  type: string;
  maskedPan: string;
  currency: string;
  balanceMinor: number;
  status: string;
  createdAt: string;
  closedAt: string | null;
};

type CardTransaction = {
  type: string;
  amountMinor: number;
  ledgerEntryId: string;
  description: string | null;
  createdAt: string;
};

type CardView = { card: VirtualCard; transactions: CardTransaction[] };

function CardsPage() {
  const [issueOpen, setIssueOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const listQ = useQuery({
    queryKey: ["cards"],
    queryFn: () => api<VirtualCard[]>("/v1/cards"),
  });

  const rows = listQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (c) => [c.id, c.holderRef, c.maskedPan, c.status],
    filterFields: { status: (c) => c.status, type: (c) => c.type },
    sortFields: {
      balance: (c) => c.balanceMinor,
      created: (c) => c.createdAt,
    },
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Issue virtual expense and wallet cards, load funds, and control spend."
        actions={
          <Button onClick={() => setIssueOpen(true)}>
            <PlusIcon /> Issue card
          </Button>
        }
      />

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search holder, PAN, id…"
          filters={[
            {
              id: "type",
              label: "Type",
              value: lv.filters.type ?? "",
              options: [
                { label: "Expense", value: "EXPENSE" },
                { label: "Wallet", value: "WALLET" },
              ],
              onChange: (v) => lv.setFilter("type", v),
            },
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: [
                { label: "Active", value: "ACTIVE" },
                { label: "Frozen", value: "FROZEN" },
                { label: "Closed", value: "CLOSED" },
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
              { header: "Card", value: (c: VirtualCard) => c.id },
              { header: "Holder", value: (c: VirtualCard) => c.holderRef },
              { header: "Type", value: (c: VirtualCard) => c.type },
              { header: "Masked PAN", value: (c: VirtualCard) => c.maskedPan },
              { header: "Currency", value: (c: VirtualCard) => c.currency },
              { header: "Balance", value: (c: VirtualCard) => c.balanceMinor },
              { header: "Status", value: (c: VirtualCard) => c.status },
              { header: "Created", value: (c: VirtualCard) => c.createdAt },
            ];
            if (format === "csv") exportToCsv("cards", lv.view, cols);
            else exportToJson("cards", lv.view);
          }}
        />
        <CardContent className="p-0">
          <DataState
            isLoading={listQ.isLoading}
            isError={listQ.isError}
            error={listQ.error}
            isEmpty={lv.view.length === 0}
            emptyIcon={CreditCardIcon}
            emptyTitle="No cards issued"
            emptyDescription="Issue a virtual card to start loading and spending funds."
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Holder</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Masked PAN</TableHead>
                  <SortHeader columnKey="balance" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Balance
                  </SortHeader>
                  <TableHead>Status</TableHead>
                  <SortHeader columnKey="created" sort={lv.sort} onToggle={lv.toggleSort}>
                    Created
                  </SortHeader>
                  <TableHead className="text-end">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell className="font-medium">{c.holderRef}</TableCell>
                    <TableCell>{c.type}</TableCell>
                    <TableCell className="font-mono text-xs">{c.maskedPan}</TableCell>
                    <TableCell className="text-end font-medium tabular-nums">
                      {formatInr(c.balanceMinor, c.currency)}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={c.status} />
                    </TableCell>
                    <TableCell>
                      <TimeSince value={c.createdAt} />
                    </TableCell>
                    <TableCell className="text-end">
                      <Button variant="outline" size="sm" onClick={() => setSelectedId(c.id)}>
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

      <IssueCardSheet open={issueOpen} onOpenChange={setIssueOpen} />
      <CardDetailSheet cardId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}

function IssueCardSheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const qc = useQueryClient();
  const [holderRef, setHolderRef] = useState("");
  const [type, setType] = useState("EXPENSE");
  const [currency, setCurrency] = useState("INR");

  const mut = useMutation({
    mutationFn: () =>
      api<CardView>("/v1/cards", {
        method: "POST",
        body: { holderRef, type, currency },
      }),
    meta: { successMessage: "Card issued" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["cards"] });
      setHolderRef("");
      onOpenChange(false);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    mut.mutate();
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Issue virtual card</SheetTitle>
          <SheetDescription>Provision an expense or wallet card for a holder.</SheetDescription>
        </SheetHeader>
        <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
          <div className="flex-1 space-y-4 overflow-y-auto px-4">
            <TextField
              id="holder-ref"
              label="Holder reference"
              value={holderRef}
              onChange={setHolderRef}
              placeholder="emp_123 or wallet_abc"
              required
            />
            <SelectField
              id="card-type"
              label="Card type"
              value={type}
              onChange={setType}
              options={[
                { label: "Expense", value: "EXPENSE" },
                { label: "Wallet", value: "WALLET" },
              ]}
            />
            <CurrencySelect value={currency} onChange={setCurrency} />
            <FormError error={mut.error} />
          </div>
          <SheetFooter>
            <Button type="submit" disabled={mut.isPending}>
              {mut.isPending ? "Issuing…" : "Issue card"}
            </Button>
            <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function CardDetailSheet({ cardId, onClose }: { cardId: string | null; onClose: () => void }) {
  const qc = useQueryClient();
  const [loadAmount, setLoadAmount] = useState("");
  const [loadError, setLoadError] = useState<string>();
  const [spendAmount, setSpendAmount] = useState("");
  const [spendDescription, setSpendDescription] = useState("");
  const [spendError, setSpendError] = useState<string>();

  const detailQ = useQuery({
    queryKey: ["card", cardId],
    queryFn: () => api<CardView>(`/v1/cards/${cardId}`),
    enabled: cardId !== null,
  });

  function invalidate() {
    qc.invalidateQueries({ queryKey: ["card", cardId] });
    qc.invalidateQueries({ queryKey: ["cards"] });
  }

  const loadM = useMutation({
    mutationFn: (amountMinor: number) =>
      api<CardView>(`/v1/cards/${cardId}/load`, { method: "POST", body: { amountMinor } }),
    meta: { successMessage: "Funds loaded" },
    onSuccess: () => {
      invalidate();
      setLoadAmount("");
    },
  });

  const spendM = useMutation({
    mutationFn: (amountMinor: number) =>
      api<CardView>(`/v1/cards/${cardId}/spend`, {
        method: "POST",
        body: { amountMinor, description: spendDescription || undefined },
      }),
    meta: { successMessage: "Spend recorded" },
    onSuccess: () => {
      invalidate();
      setSpendAmount("");
      setSpendDescription("");
    },
  });

  const statusM = useMutation({
    mutationFn: (action: "freeze" | "unfreeze" | "close") =>
      api<CardView>(`/v1/cards/${cardId}/${action}`, { method: "POST" }),
    meta: { successMessage: "Card updated" },
    onSuccess: invalidate,
  });

  function submitLoad(e: FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(loadAmount);
    if (minor === null || minor === 0) {
      setLoadError("Enter a valid amount.");
      return;
    }
    setLoadError(undefined);
    loadM.mutate(minor);
  }

  function submitSpend(e: FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(spendAmount);
    if (minor === null || minor === 0) {
      setSpendError("Enter a valid amount.");
      return;
    }
    setSpendError(undefined);
    spendM.mutate(minor);
  }

  const card = detailQ.data?.card;
  const active = card?.status === "ACTIVE";
  const closed = card?.status === "CLOSED";

  return (
    <Sheet open={cardId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Card {card ? card.maskedPan : ""}</SheetTitle>
          <SheetDescription>Balance, controls, and transaction history.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto px-4">
          <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error}>
            {card && (
              <>
                <div>
                  <DetailRow label="Status" value={<StatusBadge status={card.status} />} />
                  <DetailRow label="Holder" value={card.holderRef} />
                  <DetailRow label="Type" value={card.type} />
                  <DetailRow label="Masked PAN" value={<span className="font-mono">{card.maskedPan}</span>} />
                  <DetailRow label="Balance" value={formatInr(card.balanceMinor, card.currency)} />
                </div>

                {!closed && (
                  <div className="flex flex-wrap gap-2">
                    {card.status === "ACTIVE" ? (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => statusM.mutate("freeze")}
                        disabled={statusM.isPending}
                      >
                        Freeze
                      </Button>
                    ) : (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => statusM.mutate("unfreeze")}
                        disabled={statusM.isPending}
                      >
                        Unfreeze
                      </Button>
                    )}
                    <Button
                      variant="destructive"
                      size="sm"
                      onClick={() => statusM.mutate("close")}
                      disabled={statusM.isPending}
                    >
                      Close
                    </Button>
                  </div>
                )}

                {active && (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <form onSubmit={submitLoad} className="space-y-3 rounded-lg border p-3">
                      <p className="text-sm font-medium">Load funds</p>
                      <MoneyField
                        id="load-amount"
                        label="Amount"
                        value={loadAmount}
                        onChange={setLoadAmount}
                        required
                        error={loadError}
                      />
                      <FormError error={loadM.error} />
                      <Button type="submit" size="sm" disabled={loadM.isPending}>
                        {loadM.isPending ? "Loading…" : "Load"}
                      </Button>
                    </form>
                    <form onSubmit={submitSpend} className="space-y-3 rounded-lg border p-3">
                      <p className="text-sm font-medium">Spend</p>
                      <MoneyField
                        id="spend-amount"
                        label="Amount"
                        value={spendAmount}
                        onChange={setSpendAmount}
                        required
                        error={spendError}
                      />
                      <TextField
                        id="spend-description"
                        label="Description"
                        value={spendDescription}
                        onChange={setSpendDescription}
                        placeholder="Optional"
                      />
                      <FormError error={spendM.error} />
                      <Button type="submit" size="sm" disabled={spendM.isPending}>
                        {spendM.isPending ? "Spending…" : "Spend"}
                      </Button>
                    </form>
                  </div>
                )}

                <div>
                  <p className="mb-2 text-sm font-medium">Transactions</p>
                  {detailQ.data && detailQ.data.transactions.length > 0 ? (
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Type</TableHead>
                          <TableHead className="text-end">Amount</TableHead>
                          <TableHead>Description</TableHead>
                          <TableHead>When</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {detailQ.data.transactions.map((t) => (
                          <TableRow key={t.ledgerEntryId}>
                            <TableCell>{t.type}</TableCell>
                            <TableCell className="text-end tabular-nums">
                              {formatInr(t.amountMinor, card.currency)}
                            </TableCell>
                            <TableCell className="text-muted-foreground">{t.description ?? "—"}</TableCell>
                            <TableCell>
                              <TimeSince value={t.createdAt} />
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  ) : (
                    <p className="text-sm text-muted-foreground">No transactions yet.</p>
                  )}
                </div>
              </>
            )}
          </DataState>
        </div>
        <SheetFooter>
          <Separator className="mb-2" />
          <SheetClose render={<Button variant="outline">Close panel</Button>} />
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
