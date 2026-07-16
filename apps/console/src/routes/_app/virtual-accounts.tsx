import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
  Badge,
  Button,
  Card,
  CardContent,
  DataState,
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
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
  cn,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
  BuildingIcon,
  CopyIcon,
  DownloadCloudIcon,
  PlusIcon,
} from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/virtual-accounts")({
  component: VirtualAccountsPage,
});

type Account = {
  id: string;
  customerRef: string;
  vaNumber: string;
  ifsc: string;
  status: "ACTIVE" | "CLOSED";
  createdAt: string;
  closedAt: string | null;
};

type Credit = {
  id: string;
  vaId: string;
  amountMinor: number;
  currency: string;
  utr: string;
  payerName: string | null;
  payerRef: string | null;
  ledgerEntryId: string;
  creditedAt: string;
};

type AccountWithCredits = { account: Account; credits: Credit[] };

function statusBadge(status: Account["status"]) {
  return status === "ACTIVE" ? (
    <Badge variant="success">Active</Badge>
  ) : (
    <Badge variant="muted">Closed</Badge>
  );
}

function errMsg(e: unknown): string {
  return e instanceof ApiError ? e.message : e instanceof Error ? e.message : "Something went wrong.";
}

function VirtualAccountsPage() {
  const qc = useQueryClient();
  const [mintOpen, setMintOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [customerRef, setCustomerRef] = useState("");

  const listQ = useQuery({
    queryKey: ["virtual-accounts"],
    queryFn: () => api<Account[]>("/v1/virtual-accounts"),
    staleTime: 15_000,
  });

  const mintM = useMutation({
    mutationFn: (ref: string) =>
      api<Account>("/v1/virtual-accounts", { method: "POST", body: { customerRef: ref } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["virtual-accounts"] });
      setMintOpen(false);
      setCustomerRef("");
    },
  });

  const rows = listQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (r) => [r.vaNumber, r.ifsc, r.customerRef],
    filterFields: { status: (r) => r.status },
    sortFields: {
      customerRef: (r) => r.customerRef,
      vaNumber: (r) => r.vaNumber,
      status: (r) => r.status,
      createdAt: (r) => r.createdAt,
    },
    initialDensity: "comfortable",
  });

  const cell = (extra?: string) =>
    cn(lv.density === "compact" ? "py-1.5" : "py-3", extra);

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Mint per-customer virtual accounts and auto-reconcile inbound bank credits to the ledger."
        actions={
          <Button onClick={() => setMintOpen(true)}>
            <PlusIcon /> Mint account
          </Button>
        }
      />

      <Card className="py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search VA number, IFSC, customer…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: [
                { label: "Active", value: "ACTIVE" },
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
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("virtual-accounts", lv.view, [
                  { header: "VA Number", value: (r) => r.vaNumber },
                  { header: "IFSC", value: (r) => r.ifsc },
                  { header: "Customer Ref", value: (r) => r.customerRef },
                  { header: "Status", value: (r) => r.status },
                  { header: "Created", value: (r) => r.createdAt },
                ])
              : exportToJson("virtual-accounts", lv.view)
          }
        />

        <CardContent className="p-0">
          <DataState
            isLoading={listQ.isLoading}
            isError={listQ.isError}
            error={listQ.error}
            isEmpty={lv.view.length === 0}
            emptyIcon={BuildingIcon}
            emptyTitle="No virtual accounts"
            emptyDescription="Mint a per-customer virtual account to start collecting reconciled bank credits."
            skeletonRows={6}
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <SortHeader columnKey="vaNumber" sort={lv.sort} onToggle={lv.toggleSort}>
                    VA Number
                  </SortHeader>
                  <TableHead>IFSC</TableHead>
                  <SortHeader columnKey="customerRef" sort={lv.sort} onToggle={lv.toggleSort}>
                    Customer
                  </SortHeader>
                  <SortHeader columnKey="status" sort={lv.sort} onToggle={lv.toggleSort}>
                    Status
                  </SortHeader>
                  <SortHeader columnKey="createdAt" sort={lv.sort} onToggle={lv.toggleSort}>
                    Created
                  </SortHeader>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className={cell("font-medium tabular-nums")}>{r.vaNumber}</TableCell>
                    <TableCell className={cell("tabular-nums text-muted-foreground")}>{r.ifsc}</TableCell>
                    <TableCell className={cell()}>{r.customerRef}</TableCell>
                    <TableCell className={cell()}>{statusBadge(r.status)}</TableCell>
                    <TableCell className={cell("text-muted-foreground")}>
                      <TimeSince value={r.createdAt} />
                    </TableCell>
                    <TableCell className={cell("text-right")}>
                      <Button variant="outline" size="sm" onClick={() => setDetailId(r.id)}>
                        <DownloadCloudIcon /> Credits
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </CardContent>
      </Card>

      {/* Mint account sheet */}
      <Sheet open={mintOpen} onOpenChange={setMintOpen}>
        <SheetContent side="right" className="sm:max-w-md">
          <form
            className="flex h-full flex-col"
            onSubmit={(e) => {
              e.preventDefault();
              if (customerRef.trim()) mintM.mutate(customerRef.trim());
            }}
          >
            <SheetHeader>
              <SheetTitle>Mint virtual account</SheetTitle>
              <SheetDescription>
                A unique VA number + IFSC is issued for this customer. Inbound credits auto-reconcile
                to the ledger, keyed on the bank UTR.
              </SheetDescription>
            </SheetHeader>
            <div className="flex-1 overflow-y-auto px-4">
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="customerRef">Customer reference</FieldLabel>
                  <Input
                    id="customerRef"
                    value={customerRef}
                    onChange={(e) => setCustomerRef(e.target.value)}
                    placeholder="cust_ACME_42"
                    autoFocus
                    required
                  />
                  <FieldDescription>Your identifier for the customer this VA belongs to.</FieldDescription>
                </Field>
              </FieldGroup>
              {mintM.isError && (
                <p className="mt-3 text-sm text-destructive">{errMsg(mintM.error)}</p>
              )}
            </div>
            <SheetFooter>
              <div className="flex justify-end gap-2">
                <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
                <Button type="submit" disabled={!customerRef.trim() || mintM.isPending}>
                  {mintM.isPending ? "Minting…" : "Mint account"}
                </Button>
              </div>
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>

      {/* Account detail + credits sheet */}
      <Sheet open={detailId !== null} onOpenChange={(o) => !o && setDetailId(null)}>
        <SheetContent side="right" className="sm:max-w-lg">
          {detailId && <AccountDetail vaId={detailId} />}
        </SheetContent>
      </Sheet>
    </div>
  );
}

function AccountDetail({ vaId }: { vaId: string }) {
  const qc = useQueryClient();
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [utr, setUtr] = useState("");
  const [payerName, setPayerName] = useState("");

  const detailQ = useQuery({
    queryKey: ["virtual-account", vaId],
    queryFn: () => api<AccountWithCredits>(`/v1/virtual-accounts/${vaId}`),
  });

  const creditM = useMutation({
    mutationFn: (body: { amountMinor: number; currency: string; utr: string; payerName?: string }) =>
      api<Credit>(`/v1/virtual-accounts/${vaId}/credits`, { method: "POST", body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["virtual-account", vaId] });
      qc.invalidateQueries({ queryKey: ["virtual-accounts"] });
      setAmount("");
      setUtr("");
      setPayerName("");
    },
  });

  const closeM = useMutation({
    mutationFn: () => api<Account>(`/v1/virtual-accounts/${vaId}/close`, { method: "POST" }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["virtual-account", vaId] });
      qc.invalidateQueries({ queryKey: ["virtual-accounts"] });
    },
  });

  const account = detailQ.data?.account;
  const credits = detailQ.data?.credits ?? [];
  const minor = rupeesToMinor(amount);
  const canCredit = minor !== null && minor > 0 && utr.trim() !== "" && account?.status === "ACTIVE";

  return (
    <div className="flex h-full flex-col">
      <SheetHeader>
        <SheetTitle className="flex items-center gap-2">
          <CopyIcon className="size-4 text-muted-foreground" />
          {account?.vaNumber ?? "Virtual account"}
        </SheetTitle>
        <SheetDescription>
          {account ? `${account.ifsc} · ${account.customerRef}` : "Loading account…"}
        </SheetDescription>
      </SheetHeader>

      <div className="flex-1 space-y-5 overflow-y-auto px-4">
        <DataState
          isLoading={detailQ.isLoading}
          isError={detailQ.isError}
          error={detailQ.error}
          skeletonRows={4}
        >
          <div className="flex items-center justify-between">
            <div>{account && statusBadge(account.status)}</div>
            {account?.status === "ACTIVE" && (
              <AlertDialog>
                <AlertDialogTrigger
                  render={
                    <Button variant="outline" size="sm">
                      Close account
                    </Button>
                  }
                />
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Close this virtual account?</AlertDialogTitle>
                    <AlertDialogDescription>
                      {account.vaNumber} will stop accepting inbound credits. Existing credits are
                      unaffected. This cannot be undone.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                    <AlertDialogAction variant="destructive" onClick={() => closeM.mutate()}>
                      Close account
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            )}
          </div>

          <div>
            <h3 className="mb-2 text-sm font-medium">Credits ({credits.length})</h3>
            {credits.length === 0 ? (
              <p className="rounded-md bg-muted/50 p-3 text-sm text-muted-foreground">
                No credits received yet.
              </p>
            ) : (
              <div className="overflow-hidden rounded-lg border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Amount</TableHead>
                      <TableHead>UTR</TableHead>
                      <TableHead>Payer</TableHead>
                      <TableHead>When</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {credits.map((c) => (
                      <TableRow key={c.id}>
                        <TableCell className="py-2 font-medium tabular-nums">
                          {formatInr(c.amountMinor, c.currency)}
                        </TableCell>
                        <TableCell className="py-2 tabular-nums text-muted-foreground">{c.utr}</TableCell>
                        <TableCell className="py-2">{c.payerName ?? "—"}</TableCell>
                        <TableCell className="py-2 text-muted-foreground">
                          <TimeSince value={c.creditedAt} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </div>
        </DataState>
      </div>

      {account?.status === "ACTIVE" && (
        <SheetFooter>
          <Separator className="mb-1" />
          <p className="text-sm font-medium">Ingest credit</p>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              if (canCredit && minor !== null) {
                creditM.mutate({
                  amountMinor: minor,
                  currency,
                  utr: utr.trim(),
                  payerName: payerName.trim() || undefined,
                });
              }
            }}
          >
            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel htmlFor="cr-amount">Amount (₹)</FieldLabel>
                <Input
                  id="cr-amount"
                  inputMode="decimal"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="2500.00"
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="cr-currency">Currency</FieldLabel>
                <Select value={currency} onValueChange={(v) => setCurrency(v as string)}>
                  <SelectTrigger id="cr-currency">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="INR">INR</SelectItem>
                  </SelectContent>
                </Select>
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="cr-utr">Bank UTR</FieldLabel>
              <Input
                id="cr-utr"
                value={utr}
                onChange={(e) => setUtr(e.target.value)}
                placeholder="UTR reference (idempotency key)"
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="cr-payer">Payer name</FieldLabel>
              <Input
                id="cr-payer"
                value={payerName}
                onChange={(e) => setPayerName(e.target.value)}
                placeholder="Optional"
              />
            </Field>
            {creditM.isError && <p className="text-sm text-destructive">{errMsg(creditM.error)}</p>}
            <Button type="submit" className="w-full" disabled={!canCredit || creditM.isPending}>
              {creditM.isPending ? "Recording…" : "Record credit"}
            </Button>
          </form>
        </SheetFooter>
      )}
    </div>
  );
}
