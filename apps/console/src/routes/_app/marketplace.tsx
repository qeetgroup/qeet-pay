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
} from "@qeetrix/ui";
import {
  type UseQueryResult,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
  PlusIcon,
  SplitIcon,
  StoreIcon,
  Trash2Icon,
  UsersIcon,
} from "lucide-react";
import { useState } from "react";

import { ListToolbar } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatBps, formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/marketplace")({
  component: MarketplacePage,
});

type Seller = {
  id: string;
  sellerRef: string;
  name: string;
  gstin: string | null;
  pan: string | null;
  commissionBps: number;
  status: "ACTIVE" | "SUSPENDED";
  createdAt: string;
};

type SplitSummary = {
  id: string;
  currency: string;
  grossMinor: number;
  commissionMinor: number;
  commissionGstMinor: number;
  tcsMinor: number;
  tdsMinor: number;
  sellerNetMinor: number;
  itemCount: number;
  status: "POSTED" | "CANCELLED";
  ledgerEntryId: string;
  createdAt: string;
};

type SplitItem = {
  sellerRef: string;
  grossMinor: number;
  commissionMinor: number;
  commissionGstMinor: number;
  tcsMinor: number;
  tdsMinor: number;
  netMinor: number;
};

type SplitView = { split: SplitSummary; items: SplitItem[] };

function errMsg(e: unknown): string {
  return e instanceof ApiError ? e.message : e instanceof Error ? e.message : "Something went wrong.";
}

function MarketplacePage() {
  const [registerOpen, setRegisterOpen] = useState(false);
  const [splitOpen, setSplitOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);

  const sellersQ = useQuery({
    queryKey: ["marketplace-sellers"],
    queryFn: () => api<Seller[]>("/v1/marketplace/sellers"),
    staleTime: 15_000,
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Register marketplace sellers and split collected payments across them with commission, GST, and statutory TCS/TDS." />

      <SellersSection
        sellersQ={sellersQ}
        onRegister={() => setRegisterOpen(true)}
      />

      <SplitsSection
        sellers={sellersQ.data ?? []}
        onCreate={() => setSplitOpen(true)}
        onView={setDetailId}
      />

      <RegisterSellerSheet open={registerOpen} onOpenChange={setRegisterOpen} />
      <CreateSplitSheet open={splitOpen} onOpenChange={setSplitOpen} sellers={sellersQ.data ?? []} />

      <Sheet open={detailId !== null} onOpenChange={(o) => !o && setDetailId(null)}>
        <SheetContent side="right" className="sm:max-w-xl">
          {detailId && <SplitDetail splitId={detailId} />}
        </SheetContent>
      </Sheet>
    </div>
  );
}

// ── Sellers ────────────────────────────────────────────────────────────────

function SellersSection({
  sellersQ,
  onRegister,
}: {
  sellersQ: UseQueryResult<Seller[]>;
  onRegister: () => void;
}) {
  const qc = useQueryClient();
  const rows = sellersQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (r) => [r.sellerRef, r.name, r.gstin, r.pan],
    filterFields: { status: (r) => r.status },
  });

  const statusM = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "suspend" | "activate" }) =>
      api<Seller>(`/v1/marketplace/sellers/${id}/${action}`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["marketplace-sellers"] }),
  });

  return (
    <Card className="py-0">
      <div className="flex items-center justify-between border-b p-3">
        <h2 className="flex items-center gap-2 text-sm font-medium">
          <StoreIcon className="size-4 text-muted-foreground" /> Sellers
        </h2>
        <Button size="sm" onClick={onRegister}>
          <PlusIcon /> Register seller
        </Button>
      </div>

      <ListToolbar
        search={lv.search}
        onSearchChange={lv.setSearch}
        searchPlaceholder="Search seller ref, name, GSTIN…"
        filters={[
          {
            id: "status",
            label: "Status",
            value: lv.filters.status ?? "",
            options: [
              { label: "Active", value: "ACTIVE" },
              { label: "Suspended", value: "SUSPENDED" },
            ],
            onChange: (v) => lv.setFilter("status", v),
          },
        ]}
        hasActiveFilters={lv.hasActiveFilters}
        onClear={lv.clear}
        exportDisabled={lv.view.length === 0}
        onExport={(fmt) =>
          fmt === "csv"
            ? exportToCsv("marketplace-sellers", lv.view, [
                { header: "Seller Ref", value: (r) => r.sellerRef },
                { header: "Name", value: (r) => r.name },
                { header: "GSTIN", value: (r) => r.gstin },
                { header: "PAN", value: (r) => r.pan },
                { header: "Commission (bps)", value: (r) => r.commissionBps },
                { header: "Status", value: (r) => r.status },
              ])
            : exportToJson("marketplace-sellers", lv.view)
        }
      />

      <CardContent className="p-0">
        <DataState
          isLoading={sellersQ.isLoading}
          isError={sellersQ.isError}
          error={sellersQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={UsersIcon}
          emptyTitle="No sellers"
          emptyDescription="Register a seller before splitting a collected payment across the marketplace."
          skeletonRows={4}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Seller Ref</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>GSTIN</TableHead>
                <TableHead className="text-right">Commission</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="py-2 font-medium tabular-nums">{r.sellerRef}</TableCell>
                  <TableCell className="py-2">{r.name}</TableCell>
                  <TableCell className="py-2 tabular-nums text-muted-foreground">{r.gstin ?? "—"}</TableCell>
                  <TableCell className="py-2 text-right tabular-nums">{formatBps(r.commissionBps)}</TableCell>
                  <TableCell className="py-2">
                    {r.status === "ACTIVE" ? (
                      <Badge variant="success">Active</Badge>
                    ) : (
                      <Badge variant="muted">Suspended</Badge>
                    )}
                  </TableCell>
                  <TableCell className="py-2 text-right">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={statusM.isPending}
                      onClick={() =>
                        statusM.mutate({
                          id: r.id,
                          action: r.status === "ACTIVE" ? "suspend" : "activate",
                        })
                      }
                    >
                      {r.status === "ACTIVE" ? "Suspend" : "Activate"}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </CardContent>
    </Card>
  );
}

function RegisterSellerSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const [sellerRef, setSellerRef] = useState("");
  const [name, setName] = useState("");
  const [gstin, setGstin] = useState("");
  const [pan, setPan] = useState("");
  const [commissionPct, setCommissionPct] = useState("");

  const registerM = useMutation({
    mutationFn: (body: {
      sellerRef: string;
      name: string;
      gstin?: string;
      pan?: string;
      commissionBps: number;
    }) => api<Seller>("/v1/marketplace/sellers", { method: "POST", body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["marketplace-sellers"] });
      onOpenChange(false);
      setSellerRef("");
      setName("");
      setGstin("");
      setPan("");
      setCommissionPct("");
    },
  });

  const pct = Number(commissionPct);
  const commissionBps = Number.isFinite(pct) && pct >= 0 ? Math.round(pct * 100) : 0;
  const canSubmit = sellerRef.trim() !== "" && name.trim() !== "";

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="sm:max-w-md">
        <form
          className="flex h-full flex-col"
          onSubmit={(e) => {
            e.preventDefault();
            if (canSubmit) {
              registerM.mutate({
                sellerRef: sellerRef.trim(),
                name: name.trim(),
                gstin: gstin.trim() || undefined,
                pan: pan.trim() || undefined,
                commissionBps,
              });
            }
          }}
        >
          <SheetHeader>
            <SheetTitle>Register seller</SheetTitle>
            <SheetDescription>
              Sellers are the payees in a split settlement. Commission is charged against their gross
              share; GSTIN/PAN drive statutory TCS/TDS.
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 overflow-y-auto px-4">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="s-ref">Seller reference</FieldLabel>
                <Input id="s-ref" value={sellerRef} onChange={(e) => setSellerRef(e.target.value)} placeholder="seller_ACME" autoFocus required />
              </Field>
              <Field>
                <FieldLabel htmlFor="s-name">Legal name</FieldLabel>
                <Input id="s-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Acme Retail Pvt Ltd" required />
              </Field>
              <div className="grid grid-cols-2 gap-3">
                <Field>
                  <FieldLabel htmlFor="s-gstin">GSTIN</FieldLabel>
                  <Input id="s-gstin" value={gstin} onChange={(e) => setGstin(e.target.value)} placeholder="Optional" />
                </Field>
                <Field>
                  <FieldLabel htmlFor="s-pan">PAN</FieldLabel>
                  <Input id="s-pan" value={pan} onChange={(e) => setPan(e.target.value)} placeholder="Optional" />
                </Field>
              </div>
              <Field>
                <FieldLabel htmlFor="s-commission">Commission (%)</FieldLabel>
                <Input
                  id="s-commission"
                  inputMode="decimal"
                  value={commissionPct}
                  onChange={(e) => setCommissionPct(e.target.value)}
                  placeholder="2.5"
                />
                <FieldDescription>
                  {commissionPct ? `= ${commissionBps} bps` : "Default commission applied to this seller's gross share."}
                </FieldDescription>
              </Field>
            </FieldGroup>
            {registerM.isError && <p className="mt-3 text-sm text-destructive">{errMsg(registerM.error)}</p>}
          </div>
          <SheetFooter>
            <div className="flex justify-end gap-2">
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
              <Button type="submit" disabled={!canSubmit || registerM.isPending}>
                {registerM.isPending ? "Registering…" : "Register seller"}
              </Button>
            </div>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}

// ── Splits ───────────────────────────────────────────────────────────────

function SplitsSection({
  sellers,
  onCreate,
  onView,
}: {
  sellers: Seller[];
  onCreate: () => void;
  onView: (id: string) => void;
}) {
  const qc = useQueryClient();
  const splitsQ = useQuery({
    queryKey: ["marketplace-splits"],
    queryFn: () => api<SplitSummary[]>("/v1/marketplace/splits"),
    staleTime: 15_000,
  });

  const rows = splitsQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (r) => [r.id, r.currency, r.status],
    filterFields: { status: (r) => r.status },
  });

  const cancelM = useMutation({
    mutationFn: (id: string) => api<SplitView>(`/v1/marketplace/splits/${id}/cancel`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["marketplace-splits"] }),
  });

  return (
    <Card className="py-0">
      <div className="flex items-center justify-between border-b p-3">
        <h2 className="flex items-center gap-2 text-sm font-medium">
          <SplitIcon className="size-4 text-muted-foreground" /> Splits
        </h2>
        <Button size="sm" onClick={onCreate} disabled={sellers.filter((s) => s.status === "ACTIVE").length === 0}>
          <PlusIcon /> Create split
        </Button>
      </div>

      <ListToolbar
        search={lv.search}
        onSearchChange={lv.setSearch}
        searchPlaceholder="Search split id…"
        filters={[
          {
            id: "status",
            label: "Status",
            value: lv.filters.status ?? "",
            options: [
              { label: "Posted", value: "POSTED" },
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
            ? exportToCsv("marketplace-splits", lv.view, [
                { header: "Split", value: (r) => r.id },
                { header: "Currency", value: (r) => r.currency },
                { header: "Gross (minor)", value: (r) => r.grossMinor },
                { header: "Commission (minor)", value: (r) => r.commissionMinor },
                { header: "TCS (minor)", value: (r) => r.tcsMinor },
                { header: "TDS (minor)", value: (r) => r.tdsMinor },
                { header: "Seller Net (minor)", value: (r) => r.sellerNetMinor },
                { header: "Status", value: (r) => r.status },
              ])
            : exportToJson("marketplace-splits", lv.view)
        }
      />

      <CardContent className="p-0">
        <DataState
          isLoading={splitsQ.isLoading}
          isError={splitsQ.isError}
          error={splitsQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={SplitIcon}
          emptyTitle="No splits"
          emptyDescription="Create a split to distribute a collected payment across your sellers."
          skeletonRows={4}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Split</TableHead>
                <TableHead className="text-right">Gross</TableHead>
                <TableHead className="text-right">Commission</TableHead>
                <TableHead className="text-right">TCS</TableHead>
                <TableHead className="text-right">TDS</TableHead>
                <TableHead className="text-right">Seller net</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="py-2 font-medium tabular-nums">
                    {r.id.slice(0, 8)}
                    <span className="ml-1 text-xs text-muted-foreground">· {r.itemCount} sellers</span>
                  </TableCell>
                  <TableCell className="py-2 text-right tabular-nums">{formatInr(r.grossMinor, r.currency)}</TableCell>
                  <TableCell className="py-2 text-right tabular-nums text-muted-foreground">
                    {formatInr(r.commissionMinor, r.currency)}
                  </TableCell>
                  <TableCell className="py-2 text-right tabular-nums text-muted-foreground">
                    {formatInr(r.tcsMinor, r.currency)}
                  </TableCell>
                  <TableCell className="py-2 text-right tabular-nums text-muted-foreground">
                    {formatInr(r.tdsMinor, r.currency)}
                  </TableCell>
                  <TableCell className="py-2 text-right font-medium tabular-nums">
                    {formatInr(r.sellerNetMinor, r.currency)}
                  </TableCell>
                  <TableCell className="py-2">
                    {r.status === "POSTED" ? (
                      <Badge variant="success">Posted</Badge>
                    ) : (
                      <Badge variant="muted">Cancelled</Badge>
                    )}
                  </TableCell>
                  <TableCell className="py-2 text-muted-foreground">
                    <TimeSince value={r.createdAt} />
                  </TableCell>
                  <TableCell className="py-2 text-right">
                    <div className="flex justify-end gap-1.5">
                      <Button variant="outline" size="sm" onClick={() => onView(r.id)}>
                        View
                      </Button>
                      {r.status === "POSTED" && (
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
                              <AlertDialogTitle>Cancel this split?</AlertDialogTitle>
                              <AlertDialogDescription>
                                A reversing ledger entry will offset split {r.id.slice(0, 8)}. This
                                cannot be undone.
                              </AlertDialogDescription>
                            </AlertDialogHeader>
                            <AlertDialogFooter>
                              <AlertDialogCancel>Keep split</AlertDialogCancel>
                              <AlertDialogAction variant="destructive" onClick={() => cancelM.mutate(r.id)}>
                                Cancel split
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
  );
}

type LineDraft = { sellerRef: string; gross: string };

function CreateSplitSheet({
  open,
  onOpenChange,
  sellers,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  sellers: Seller[];
}) {
  const qc = useQueryClient();
  const activeSellers = sellers.filter((s) => s.status === "ACTIVE");
  const [currency, setCurrency] = useState("INR");
  const [sourceRef, setSourceRef] = useState("");
  const [lines, setLines] = useState<LineDraft[]>([{ sellerRef: "", gross: "" }]);

  const reset = () => {
    setCurrency("INR");
    setSourceRef("");
    setLines([{ sellerRef: "", gross: "" }]);
  };

  const createM = useMutation({
    mutationFn: (body: {
      currency: string;
      sourceRef?: string;
      lines: { sellerRef: string; grossMinor: number }[];
    }) => api<SplitView>("/v1/marketplace/splits", { method: "POST", body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["marketplace-splits"] });
      onOpenChange(false);
      reset();
    },
  });

  const parsed = lines.map((l) => ({ sellerRef: l.sellerRef, minor: rupeesToMinor(l.gross) }));
  const validLines = parsed.filter((l) => l.sellerRef !== "" && l.minor !== null && l.minor > 0);
  const canSubmit = validLines.length === lines.length && lines.length > 0;
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
                currency,
                sourceRef: sourceRef.trim() || undefined,
                lines: validLines.map((l) => ({ sellerRef: l.sellerRef, grossMinor: l.minor as number })),
              });
            }
          }}
        >
          <SheetHeader>
            <SheetTitle>Create split</SheetTitle>
            <SheetDescription>
              Distribute a collected payment across sellers. Commission, GST, and statutory TCS/TDS are
              computed per line.
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 space-y-4 overflow-y-auto px-4">
            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel htmlFor="sp-currency">Currency</FieldLabel>
                <Select value={currency} onValueChange={(v) => setCurrency(v as string)}>
                  <SelectTrigger id="sp-currency">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="INR">INR</SelectItem>
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="sp-source">Source reference</FieldLabel>
                <Input id="sp-source" value={sourceRef} onChange={(e) => setSourceRef(e.target.value)} placeholder="Optional" />
              </Field>
            </div>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <p className="text-sm font-medium">Line items</p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setLines((prev) => [...prev, { sellerRef: "", gross: "" }])}
                >
                  <PlusIcon /> Add line
                </Button>
              </div>
              <div className="space-y-2">
                {lines.map((line, idx) => (
                  <div key={idx} className="flex items-end gap-2 rounded-lg border p-2">
                    <div className="flex-1">
                      <label className="mb-1 block text-xs text-muted-foreground">Seller</label>
                      <Select
                        value={line.sellerRef}
                        onValueChange={(v) =>
                          setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, sellerRef: v as string } : l)))
                        }
                      >
                        <SelectTrigger>
                          <SelectValue placeholder="Select seller" />
                        </SelectTrigger>
                        <SelectContent>
                          {activeSellers.map((s) => (
                            <SelectItem key={s.id} value={s.sellerRef}>
                              {s.name} ({s.sellerRef})
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="w-32">
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
                      aria-label="Remove line"
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
                {createM.isPending ? "Creating…" : "Create split"}
              </Button>
            </div>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function SplitDetail({ splitId }: { splitId: string }) {
  const detailQ = useQuery({
    queryKey: ["marketplace-split", splitId],
    queryFn: () => api<SplitView>(`/v1/marketplace/splits/${splitId}`),
  });

  const split = detailQ.data?.split;
  const items = detailQ.data?.items ?? [];
  const currency = split?.currency ?? "INR";

  return (
    <div className="flex h-full flex-col">
      <SheetHeader>
        <SheetTitle>Split {splitId.slice(0, 8)}</SheetTitle>
        <SheetDescription>
          {split ? `${items.length} sellers · gross ${formatInr(split.grossMinor, currency)}` : "Loading split…"}
        </SheetDescription>
      </SheetHeader>
      <div className="flex-1 space-y-4 overflow-y-auto px-4">
        <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error} skeletonRows={4}>
          {split && (
            <>
              <div>
                {split.status === "POSTED" ? (
                  <Badge variant="success">Posted</Badge>
                ) : (
                  <Badge variant="muted">Cancelled</Badge>
                )}
              </div>
              <div className="overflow-hidden rounded-lg border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Seller</TableHead>
                      <TableHead className="text-right">Gross</TableHead>
                      <TableHead className="text-right">Commission</TableHead>
                      <TableHead className="text-right">GST</TableHead>
                      <TableHead className="text-right">TCS</TableHead>
                      <TableHead className="text-right">TDS</TableHead>
                      <TableHead className="text-right">Net</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((it, i) => (
                      <TableRow key={`${it.sellerRef}-${i}`}>
                        <TableCell className="py-2 font-medium tabular-nums">{it.sellerRef}</TableCell>
                        <TableCell className="py-2 text-right tabular-nums">{formatInr(it.grossMinor, currency)}</TableCell>
                        <TableCell className="py-2 text-right tabular-nums text-muted-foreground">{formatInr(it.commissionMinor, currency)}</TableCell>
                        <TableCell className="py-2 text-right tabular-nums text-muted-foreground">{formatInr(it.commissionGstMinor, currency)}</TableCell>
                        <TableCell className="py-2 text-right tabular-nums text-muted-foreground">{formatInr(it.tcsMinor, currency)}</TableCell>
                        <TableCell className="py-2 text-right tabular-nums text-muted-foreground">{formatInr(it.tdsMinor, currency)}</TableCell>
                        <TableCell className="py-2 text-right font-medium tabular-nums">{formatInr(it.netMinor, currency)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <Separator />
              <dl className="grid grid-cols-2 gap-1.5 text-sm">
                <dt className="text-muted-foreground">Total commission</dt>
                <dd className="text-right tabular-nums">{formatInr(split.commissionMinor, currency)}</dd>
                <dt className="text-muted-foreground">Commission GST</dt>
                <dd className="text-right tabular-nums">{formatInr(split.commissionGstMinor, currency)}</dd>
                <dt className="text-muted-foreground">TCS (§52)</dt>
                <dd className="text-right tabular-nums">{formatInr(split.tcsMinor, currency)}</dd>
                <dt className="text-muted-foreground">TDS (§194-O)</dt>
                <dd className="text-right tabular-nums">{formatInr(split.tdsMinor, currency)}</dd>
                <dt className="font-medium">Seller net</dt>
                <dd className="text-right font-semibold tabular-nums">{formatInr(split.sellerNetMinor, currency)}</dd>
              </dl>
            </>
          )}
        </DataState>
      </div>
    </div>
  );
}
