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
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { BanknoteIcon, HandCoinsIcon, NetworkIcon, PackageIcon, PlusIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";

import { ListToolbar } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { KpiRow, KpiTile } from "@/components/stat-tile";
import { ApiError, api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/ondc")({
  component: OndcPage,
});

type OrderStatus = "CREATED" | "FULFILLED" | "SETTLED" | "CANCELLED";
type PartyRole = "SELLER" | "LOGISTICS" | "PLATFORM" | "OTHER";

type OrderSummary = {
  id: string;
  networkOrderId: string;
  buyerApp: string;
  sellerApp: string;
  currency: string;
  grossMinor: number;
  commissionMinor: number;
  commissionGstMinor: number;
  tcsMinor: number;
  partyNetMinor: number;
  partyCount: number;
  status: OrderStatus;
  holdEntryId: string;
  settleEntryId: string | null;
  createdAt: string;
};

type OrderLine = {
  partyRef: string;
  role: PartyRole;
  grossMinor: number;
  commissionMinor: number;
  commissionGstMinor: number;
  tcsMinor: number;
  netMinor: number;
};

type OrderView = { order: OrderSummary; lines: OrderLine[] };

const ROLES: PartyRole[] = ["SELLER", "LOGISTICS", "PLATFORM", "OTHER"];

function errMsg(e: unknown): string {
  return e instanceof ApiError ? e.message : e instanceof Error ? e.message : "Something went wrong.";
}

function StatusBadge({ status }: { status: OrderStatus }) {
  const variant =
    status === "SETTLED"
      ? "success"
      : status === "FULFILLED"
        ? "warning"
        : status === "CANCELLED"
          ? "muted"
          : "secondary";
  return <Badge variant={variant}>{status}</Badge>;
}

function OndcPage() {
  const [createOpen, setCreateOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Settle ONDC network orders across parties — hold the collected gross, then release each party's net after commission, GST, and statutory TCS (§52) post-fulfilment."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon /> Create order
          </Button>
        }
      />

      <OrdersSection onView={setDetailId} />

      <CreateOrderSheet open={createOpen} onOpenChange={setCreateOpen} />

      <Sheet open={detailId !== null} onOpenChange={(o) => !o && setDetailId(null)}>
        <SheetContent side="right" className="sm:max-w-xl">
          {detailId && <OrderDetail orderId={detailId} />}
        </SheetContent>
      </Sheet>
    </div>
  );
}

// ── Orders ───────────────────────────────────────────────────────────────

function OrdersSection({ onView }: { onView: (id: string) => void }) {
  const qc = useQueryClient();
  const ordersQ = useQuery({
    queryKey: ["ondc-orders"],
    queryFn: () => api<OrderSummary[]>("/v1/ondc/orders"),
    staleTime: 15_000,
  });

  const rows = ordersQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (r) => [r.networkOrderId, r.buyerApp, r.sellerApp, r.status],
    filterFields: { status: (r) => r.status },
  });

  const heldGross = rows
    .filter((r) => r.status === "CREATED" || r.status === "FULFILLED")
    .reduce((s, r) => s + r.grossMinor, 0);
  const partyNet = rows.reduce((s, r) => s + r.partyNetMinor, 0);
  const settled = rows.filter((r) => r.status === "SETTLED").length;

  const invalidate = () => qc.invalidateQueries({ queryKey: ["ondc-orders"] });

  const actionM = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "fulfill" | "settle" | "cancel" }) =>
      api<OrderView>(`/v1/ondc/orders/${id}/${action}`, { method: "POST" }),
    onSuccess: invalidate,
  });

  return (
    <>
      <KpiRow>
        <KpiTile label="Orders" value={rows.length.toLocaleString("en-IN")} icon={PackageIcon} tone="info" loading={ordersQ.isLoading} />
        <KpiTile label="Held gross" value={formatInr(heldGross)} icon={NetworkIcon} tone="warning" hint="Awaiting settlement" loading={ordersQ.isLoading} />
        <KpiTile label="Settled" value={settled.toLocaleString("en-IN")} icon={BanknoteIcon} tone="success" loading={ordersQ.isLoading} />
        <KpiTile label="Party net" value={formatInr(partyNet)} icon={HandCoinsIcon} tone="brand" hint="After commission, GST & TCS" loading={ordersQ.isLoading} />
      </KpiRow>

      <Card className="overflow-hidden py-0">
      <ListToolbar
        search={lv.search}
        onSearchChange={lv.setSearch}
        searchPlaceholder="Search network order, buyer/seller app…"
        filters={[
          {
            id: "status",
            label: "Status",
            value: lv.filters.status ?? "",
            options: [
              { label: "Created", value: "CREATED" },
              { label: "Fulfilled", value: "FULFILLED" },
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
        onExport={(fmt) =>
          fmt === "csv"
            ? exportToCsv("ondc-orders", lv.view, [
                { header: "Network Order", value: (r) => r.networkOrderId },
                { header: "Buyer App", value: (r) => r.buyerApp },
                { header: "Seller App", value: (r) => r.sellerApp },
                { header: "Gross (minor)", value: (r) => r.grossMinor },
                { header: "Commission (minor)", value: (r) => r.commissionMinor },
                { header: "TCS (minor)", value: (r) => r.tcsMinor },
                { header: "Party Net (minor)", value: (r) => r.partyNetMinor },
                { header: "Status", value: (r) => r.status },
              ])
            : exportToJson("ondc-orders", lv.view)
        }
      />

      <CardContent className="p-0">
        <DataState
          isLoading={ordersQ.isLoading}
          isError={ordersQ.isError}
          error={ordersQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={PackageIcon}
          emptyTitle="No ONDC orders"
          emptyDescription="Create a network order to hold and settle a collected payment across its parties."
          skeletonRows={4}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Network order</TableHead>
                <TableHead className="text-right">Gross</TableHead>
                <TableHead className="text-right">Commission</TableHead>
                <TableHead className="text-right">TCS</TableHead>
                <TableHead className="text-right">Party net</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="py-2 font-medium tabular-nums">
                    {r.networkOrderId}
                    <span className="ml-1 text-xs text-muted-foreground">· {r.partyCount} parties</span>
                  </TableCell>
                  <TableCell className="py-2 text-right tabular-nums">{formatInr(r.grossMinor, r.currency)}</TableCell>
                  <TableCell className="py-2 text-right tabular-nums text-muted-foreground">
                    {formatInr(r.commissionMinor, r.currency)}
                  </TableCell>
                  <TableCell className="py-2 text-right tabular-nums text-muted-foreground">
                    {formatInr(r.tcsMinor, r.currency)}
                  </TableCell>
                  <TableCell className="py-2 text-right font-medium tabular-nums">
                    {formatInr(r.partyNetMinor, r.currency)}
                  </TableCell>
                  <TableCell className="py-2">
                    <StatusBadge status={r.status} />
                  </TableCell>
                  <TableCell className="py-2 text-muted-foreground">
                    <TimeSince value={r.createdAt} />
                  </TableCell>
                  <TableCell className="py-2 text-right">
                    <div className="flex justify-end gap-1.5">
                      <Button variant="outline" size="sm" onClick={() => onView(r.id)}>
                        View
                      </Button>
                      {r.status === "CREATED" && (
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={actionM.isPending}
                          onClick={() => actionM.mutate({ id: r.id, action: "fulfill" })}
                        >
                          Fulfill
                        </Button>
                      )}
                      {r.status === "FULFILLED" && (
                        <Button
                          size="sm"
                          disabled={actionM.isPending}
                          onClick={() => actionM.mutate({ id: r.id, action: "settle" })}
                        >
                          Settle
                        </Button>
                      )}
                      {(r.status === "CREATED" || r.status === "FULFILLED" || r.status === "SETTLED") && (
                        <AlertDialog>
                          <AlertDialogTrigger
                            render={
                              <Button variant="outline" size="sm">
                                Cancel
                              </Button>
                            }
                          />
                          <AlertDialogContent>
                            <AlertDialogHeader>
                              <AlertDialogTitle>Cancel this order?</AlertDialogTitle>
                              <AlertDialogDescription>
                                A reversing ledger entry will offset order {r.networkOrderId}. This
                                cannot be undone.
                              </AlertDialogDescription>
                            </AlertDialogHeader>
                            <AlertDialogFooter>
                              <AlertDialogCancel>Keep order</AlertDialogCancel>
                              <AlertDialogAction
                                variant="destructive"
                                onClick={() => actionM.mutate({ id: r.id, action: "cancel" })}
                              >
                                Cancel order
                              </AlertDialogAction>
                            </AlertDialogFooter>
                          </AlertDialogContent>
                        </AlertDialog>
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
    </>
  );
}

type LineDraft = { partyRef: string; role: PartyRole; gross: string };

function CreateOrderSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const [networkOrderId, setNetworkOrderId] = useState("");
  const [buyerApp, setBuyerApp] = useState("");
  const [sellerApp, setSellerApp] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [lines, setLines] = useState<LineDraft[]>([{ partyRef: "", role: "SELLER", gross: "" }]);

  const reset = () => {
    setNetworkOrderId("");
    setBuyerApp("");
    setSellerApp("");
    setCurrency("INR");
    setLines([{ partyRef: "", role: "SELLER", gross: "" }]);
  };

  const createM = useMutation({
    mutationFn: (body: {
      networkOrderId: string;
      buyerApp: string;
      sellerApp: string;
      currency: string;
      lines: { partyRef: string; role: PartyRole; grossMinor: number }[];
    }) => api<OrderView>("/v1/ondc/orders", { method: "POST", body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ondc-orders"] });
      onOpenChange(false);
      reset();
    },
  });

  const parsed = lines.map((l) => ({ partyRef: l.partyRef, role: l.role, minor: rupeesToMinor(l.gross) }));
  const validLines = parsed.filter((l) => l.partyRef.trim() !== "" && l.minor !== null && l.minor > 0);
  const canSubmit =
    networkOrderId.trim() !== "" &&
    buyerApp.trim() !== "" &&
    sellerApp.trim() !== "" &&
    validLines.length === lines.length &&
    lines.length > 0;
  const totalMinor = parsed.reduce((s, l) => s + (l.minor ?? 0), 0);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="sm:max-w-lg">
        <form
          className="flex h-full flex-col"
          onSubmit={(e) => {
            e.preventDefault();
            if (canSubmit) {
              createM.mutate({
                networkOrderId: networkOrderId.trim(),
                buyerApp: buyerApp.trim(),
                sellerApp: sellerApp.trim(),
                currency,
                lines: validLines.map((l) => ({
                  partyRef: l.partyRef.trim(),
                  role: l.role,
                  grossMinor: l.minor as number,
                })),
              });
            }
          }}
        >
          <SheetHeader>
            <SheetTitle>Create ONDC order</SheetTitle>
            <SheetDescription>
              Hold a collected buyer payment across its network parties. Commission, GST, and statutory
              TCS (§52) are computed per line; funds settle post-fulfilment.
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 space-y-4 overflow-y-auto px-4">
            <Field>
              <FieldLabel htmlFor="o-net">Network order id</FieldLabel>
              <Input
                id="o-net"
                value={networkOrderId}
                onChange={(e) => setNetworkOrderId(e.target.value)}
                placeholder="2024-ONDC-000123"
                autoFocus
                required
              />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel htmlFor="o-buyer">Buyer app</FieldLabel>
                <Input id="o-buyer" value={buyerApp} onChange={(e) => setBuyerApp(e.target.value)} placeholder="buyer.example.com" required />
              </Field>
              <Field>
                <FieldLabel htmlFor="o-seller">Seller app</FieldLabel>
                <Input id="o-seller" value={sellerApp} onChange={(e) => setSellerApp(e.target.value)} placeholder="seller.example.com" required />
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="o-currency">Currency</FieldLabel>
              <Select value={currency} onValueChange={(v) => setCurrency(v as string)}>
                <SelectTrigger id="o-currency">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="INR">INR</SelectItem>
                </SelectContent>
              </Select>
            </Field>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <p className="text-sm font-medium">Party lines</p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setLines((prev) => [...prev, { partyRef: "", role: "SELLER", gross: "" }])}
                >
                  <PlusIcon /> Add party
                </Button>
              </div>
              <div className="space-y-2">
                {lines.map((line, idx) => (
                  <div key={idx} className="flex items-end gap-2 rounded-lg border p-2">
                    <div className="flex-1">
                      <label className="mb-1 block text-xs text-muted-foreground">Party ref</label>
                      <Input
                        value={line.partyRef}
                        onChange={(e) =>
                          setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, partyRef: e.target.value } : l)))
                        }
                        placeholder="seller_ACME"
                      />
                    </div>
                    <div className="w-32">
                      <label className="mb-1 block text-xs text-muted-foreground">Role</label>
                      <Select
                        value={line.role}
                        onValueChange={(v) =>
                          setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, role: v as PartyRole } : l)))
                        }
                      >
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {ROLES.map((r) => (
                            <SelectItem key={r} value={r}>
                              {r.charAt(0) + r.slice(1).toLowerCase()}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="w-28">
                      <label className="mb-1 block text-xs text-muted-foreground">Gross (₹)</label>
                      <Input
                        inputMode="decimal"
                        value={line.gross}
                        onChange={(e) =>
                          setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, gross: e.target.value } : l)))
                        }
                        placeholder="1000.00"
                      />
                    </div>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label="Remove party"
                      disabled={lines.length === 1}
                      onClick={() => setLines((prev) => prev.filter((_, i) => i !== idx))}
                    >
                      <Trash2Icon />
                    </Button>
                  </div>
                ))}
              </div>
            </div>

            <div className="flex items-center justify-between rounded-md bg-muted/50 p-3 text-sm">
              <span className="text-muted-foreground">Total gross</span>
              <span className="font-semibold tabular-nums">{formatInr(totalMinor, currency)}</span>
            </div>

            {createM.isError && <p className="text-sm text-destructive">{errMsg(createM.error)}</p>}
          </div>
          <SheetFooter>
            <div className="flex justify-end gap-2">
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
              <Button type="submit" disabled={!canSubmit || createM.isPending}>
                {createM.isPending ? "Creating…" : "Create order"}
              </Button>
            </div>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function OrderDetail({ orderId }: { orderId: string }) {
  const detailQ = useQuery({
    queryKey: ["ondc-order", orderId],
    queryFn: () => api<OrderView>(`/v1/ondc/orders/${orderId}`),
  });

  const order = detailQ.data?.order;
  const lines = detailQ.data?.lines ?? [];
  const currency = order?.currency ?? "INR";

  return (
    <div className="flex h-full flex-col">
      <SheetHeader>
        <SheetTitle>{order ? order.networkOrderId : `Order ${orderId.slice(0, 8)}`}</SheetTitle>
        <SheetDescription>
          {order
            ? `${lines.length} parties · gross ${formatInr(order.grossMinor, currency)}`
            : "Loading order…"}
        </SheetDescription>
      </SheetHeader>
      <div className="flex-1 space-y-4 overflow-y-auto px-4">
        <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error} skeletonRows={4}>
          {order && (
            <>
              <div>
                <StatusBadge status={order.status} />
              </div>
              <div className="overflow-hidden rounded-lg border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Party</TableHead>
                      <TableHead>Role</TableHead>
                      <TableHead className="text-right">Gross</TableHead>
                      <TableHead className="text-right">Commission</TableHead>
                      <TableHead className="text-right">GST</TableHead>
                      <TableHead className="text-right">TCS</TableHead>
                      <TableHead className="text-right">Net</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {lines.map((it, i) => (
                      <TableRow key={`${it.partyRef}-${i}`}>
                        <TableCell className="py-2 font-medium tabular-nums">{it.partyRef}</TableCell>
                        <TableCell className="py-2 text-muted-foreground">{it.role}</TableCell>
                        <TableCell className="py-2 text-right tabular-nums">{formatInr(it.grossMinor, currency)}</TableCell>
                        <TableCell className="py-2 text-right tabular-nums text-muted-foreground">{formatInr(it.commissionMinor, currency)}</TableCell>
                        <TableCell className="py-2 text-right tabular-nums text-muted-foreground">{formatInr(it.commissionGstMinor, currency)}</TableCell>
                        <TableCell className="py-2 text-right tabular-nums text-muted-foreground">{formatInr(it.tcsMinor, currency)}</TableCell>
                        <TableCell className="py-2 text-right font-medium tabular-nums">{formatInr(it.netMinor, currency)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <Separator />
              <dl className="grid grid-cols-2 gap-1.5 text-sm">
                <dt className="text-muted-foreground">Total commission</dt>
                <dd className="text-right tabular-nums">{formatInr(order.commissionMinor, currency)}</dd>
                <dt className="text-muted-foreground">Commission GST</dt>
                <dd className="text-right tabular-nums">{formatInr(order.commissionGstMinor, currency)}</dd>
                <dt className="text-muted-foreground">TCS (§52)</dt>
                <dd className="text-right tabular-nums">{formatInr(order.tcsMinor, currency)}</dd>
                <dt className="font-medium">Party net</dt>
                <dd className="text-right font-semibold tabular-nums">{formatInr(order.partyNetMinor, currency)}</dd>
              </dl>
            </>
          )}
        </DataState>
      </div>
    </div>
  );
}
