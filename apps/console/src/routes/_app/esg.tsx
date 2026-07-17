import {
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
  CloudIcon,
  LeafIcon,
  PlusIcon,
  ScaleIcon,
  SproutIcon,
} from "lucide-react";
import { useEffect, useState } from "react";

import { ListToolbar } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/esg")({
  component: EsgPage,
});

const METHODS = ["UPI", "CARD", "NET_BANKING", "WALLET"] as const;
type Method = (typeof METHODS)[number];

type CarbonRecord = {
  id: string;
  transactionRef: string;
  method: Method;
  amountMinor: number;
  gramsCo2: number;
  createdAt: string;
};

type Offset = {
  id: string;
  gramsCo2Offset: number;
  costMinor: number;
  currency: string;
  ledgerEntryId: string | null;
  note: string | null;
  createdAt: string;
};

type Summary = {
  recordCount: number;
  totalGramsCo2: number;
  totalGramsOffset: number;
  netGramsCo2: number;
};

function formatGrams(g: number): string {
  const abs = Math.abs(g);
  if (abs >= 1_000_000) return `${(g / 1_000_000).toLocaleString("en-IN", { maximumFractionDigits: 2 })} t`;
  if (abs >= 1000) return `${(g / 1000).toLocaleString("en-IN", { maximumFractionDigits: 2 })} kg`;
  return `${g.toLocaleString("en-IN")} g`;
}

function methodLabel(m: Method): string {
  return m === "NET_BANKING" ? "Net Banking" : m.charAt(0) + m.slice(1).toLowerCase();
}

function errMsg(e: unknown): string {
  return e instanceof ApiError ? e.message : e instanceof Error ? e.message : "Something went wrong.";
}

function StatCard({
  icon,
  title,
  value,
  sub,
  iconClass = "bg-primary/10 text-primary",
}: {
  icon: React.ReactNode;
  title: string;
  value: string;
  sub?: string;
  iconClass?: string;
}) {
  return (
    <Card>
      <CardContent className="flex items-center gap-4 pt-6">
        <div className={cn("grid size-10 shrink-0 place-items-center rounded-lg [&_svg]:size-5", iconClass)}>
          {icon}
        </div>
        <div className="min-w-0">
          <p className="truncate text-2xl font-semibold tabular-nums">{value}</p>
          <p className="text-sm text-muted-foreground">{title}</p>
          {sub && <p className="text-xs text-muted-foreground">{sub}</p>}
        </div>
      </CardContent>
    </Card>
  );
}

function EsgPage() {
  const [footprintOpen, setFootprintOpen] = useState(false);
  const [offsetOpen, setOffsetOpen] = useState(false);

  const summaryQ = useQuery({
    queryKey: ["esg-summary"],
    queryFn: () => api<Summary>("/v1/esg/summary"),
    staleTime: 15_000,
  });
  const recordsQ = useQuery({
    queryKey: ["esg-records"],
    queryFn: () => api<CarbonRecord[]>("/v1/esg/footprints"),
    staleTime: 15_000,
  });

  const summary = summaryQ.data;
  const net = summary?.netGramsCo2 ?? 0;

  const rows = recordsQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (r) => [r.transactionRef, r.method],
    filterFields: { method: (r) => r.method },
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Estimate the carbon footprint of payments and retire verified offsets to reach net zero."
        actions={
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setOffsetOpen(true)}>
              <SproutIcon /> Purchase offset
            </Button>
            <Button onClick={() => setFootprintOpen(true)}>
              <PlusIcon /> Record footprint
            </Button>
          </div>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <StatCard
          icon={<CloudIcon />}
          title="Total CO₂ emitted"
          value={formatGrams(summary?.totalGramsCo2 ?? 0)}
          sub={`${(summary?.recordCount ?? 0).toLocaleString("en-IN")} transactions measured`}
          iconClass="bg-slate-500/10 text-slate-600 dark:text-slate-300"
        />
        <StatCard
          icon={<SproutIcon />}
          title="CO₂ offset"
          value={formatGrams(summary?.totalGramsOffset ?? 0)}
          iconClass="bg-green-500/10 text-green-600 dark:text-green-400"
        />
        <StatCard
          icon={<ScaleIcon />}
          title="Net CO₂"
          value={formatGrams(net)}
          sub={net <= 0 ? "Net zero achieved" : "Remaining to offset"}
          iconClass={
            net <= 0
              ? "bg-green-500/10 text-green-600 dark:text-green-400"
              : "bg-amber-500/10 text-amber-600 dark:text-amber-400"
          }
        />
      </div>

      <Card className="py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search transaction ref…"
          filters={[
            {
              id: "method",
              label: "Method",
              value: lv.filters.method ?? "",
              options: METHODS.map((m) => ({ label: methodLabel(m), value: m })),
              onChange: (v) => lv.setFilter("method", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          exportDisabled={lv.view.length === 0}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("esg-footprints", lv.view, [
                  { header: "Transaction Ref", value: (r) => r.transactionRef },
                  { header: "Method", value: (r) => r.method },
                  { header: "Amount (minor)", value: (r) => r.amountMinor },
                  { header: "Grams CO2", value: (r) => r.gramsCo2 },
                  { header: "Created", value: (r) => r.createdAt },
                ])
              : exportToJson("esg-footprints", lv.view)
          }
        />

        <CardContent className="p-0">
          <DataState
            isLoading={recordsQ.isLoading}
            isError={recordsQ.isError}
            error={recordsQ.error}
            isEmpty={lv.view.length === 0}
            emptyIcon={LeafIcon}
            emptyTitle="No footprint records"
            emptyDescription="Record a transaction's estimated carbon footprint to start tracking emissions."
            skeletonRows={6}
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Transaction Ref</TableHead>
                  <TableHead>Method</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
                  <TableHead className="text-right">CO₂</TableHead>
                  <TableHead>Recorded</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className={cn(lv.density === "compact" ? "py-1.5" : "py-3", "font-medium tabular-nums")}>
                      {r.transactionRef}
                    </TableCell>
                    <TableCell className={cn(lv.density === "compact" ? "py-1.5" : "py-3")}>
                      <Badge variant="outline">{methodLabel(r.method)}</Badge>
                    </TableCell>
                    <TableCell className={cn(lv.density === "compact" ? "py-1.5" : "py-3", "text-right tabular-nums")}>
                      {formatInr(r.amountMinor)}
                    </TableCell>
                    <TableCell className={cn(lv.density === "compact" ? "py-1.5" : "py-3", "text-right tabular-nums text-muted-foreground")}>
                      {formatGrams(r.gramsCo2)}
                    </TableCell>
                    <TableCell className={cn(lv.density === "compact" ? "py-1.5" : "py-3", "text-muted-foreground")}>
                      <TimeSince value={r.createdAt} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </CardContent>
      </Card>

      <FootprintSheet open={footprintOpen} onOpenChange={setFootprintOpen} />
      <OffsetSheet open={offsetOpen} onOpenChange={setOffsetOpen} defaultGrams={Math.max(0, net)} />
    </div>
  );
}

function FootprintSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const [transactionRef, setTransactionRef] = useState("");
  const [method, setMethod] = useState<Method>("UPI");
  const [amount, setAmount] = useState("");

  const recordM = useMutation({
    mutationFn: (body: { transactionRef: string; method: Method; amountMinor: number }) =>
      api<CarbonRecord>("/v1/esg/footprints", { method: "POST", body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["esg-records"] });
      qc.invalidateQueries({ queryKey: ["esg-summary"] });
      onOpenChange(false);
      setTransactionRef("");
      setMethod("UPI");
      setAmount("");
    },
  });

  const minor = rupeesToMinor(amount);
  const canSubmit = transactionRef.trim() !== "" && minor !== null;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="sm:max-w-md">
        <form
          className="flex h-full flex-col"
          onSubmit={(e) => {
            e.preventDefault();
            if (canSubmit && minor !== null) {
              recordM.mutate({ transactionRef: transactionRef.trim(), method, amountMinor: minor });
            }
          }}
        >
          <SheetHeader>
            <SheetTitle>Record footprint</SheetTitle>
            <SheetDescription>
              Estimate the CO₂ of a transaction from its payment method and amount.
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 overflow-y-auto px-4">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="f-ref">Transaction reference</FieldLabel>
                <Input id="f-ref" value={transactionRef} onChange={(e) => setTransactionRef(e.target.value)} placeholder="pay_9fZ3…" autoFocus required />
              </Field>
              <Field>
                <FieldLabel htmlFor="f-method">Method</FieldLabel>
                <Select value={method} onValueChange={(v) => setMethod(v as Method)}>
                  <SelectTrigger id="f-method">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {METHODS.map((m) => (
                      <SelectItem key={m} value={m}>
                        {methodLabel(m)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="f-amount">Amount (₹)</FieldLabel>
                <Input id="f-amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="2500.00" />
              </Field>
            </FieldGroup>
            {recordM.isError && <p className="mt-3 text-sm text-destructive">{errMsg(recordM.error)}</p>}
          </div>
          <SheetFooter>
            <div className="flex justify-end gap-2">
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
              <Button type="submit" disabled={!canSubmit || recordM.isPending}>
                {recordM.isPending ? "Recording…" : "Record footprint"}
              </Button>
            </div>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function OffsetSheet({
  open,
  onOpenChange,
  defaultGrams,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  defaultGrams: number;
}) {
  const qc = useQueryClient();
  const [grams, setGrams] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [pricePerTonne, setPricePerTonne] = useState("");

  // Prefill grams-to-offset with the outstanding net footprint when opened.
  useEffect(() => {
    if (open) setGrams(defaultGrams > 0 ? String(defaultGrams) : "");
  }, [open, defaultGrams]);

  const offsetM = useMutation({
    mutationFn: (body: { gramsToOffset: number; currency: string; pricePerTonneMinor: number }) =>
      api<Offset>("/v1/esg/offsets", { method: "POST", body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["esg-summary"] });
      onOpenChange(false);
      setGrams("");
      setPricePerTonne("");
    },
  });

  const gramsNum = Number(grams);
  const priceMinor = rupeesToMinor(pricePerTonne);
  const validGrams = Number.isFinite(gramsNum) && gramsNum > 0;
  const canSubmit = validGrams && priceMinor !== null && priceMinor > 0;
  const estCostMinor =
    validGrams && priceMinor !== null ? Math.round((gramsNum / 1_000_000) * priceMinor) : 0;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="sm:max-w-md">
        <form
          className="flex h-full flex-col"
          onSubmit={(e) => {
            e.preventDefault();
            if (canSubmit && priceMinor !== null) {
              offsetM.mutate({ gramsToOffset: Math.round(gramsNum), currency, pricePerTonneMinor: priceMinor });
            }
          }}
        >
          <SheetHeader>
            <SheetTitle>Purchase carbon offset</SheetTitle>
            <SheetDescription>
              Retire verified offsets against your emissions. Cost = grams ÷ 1,000,000 × price per tonne.
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 space-y-4 overflow-y-auto px-4">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="o-grams">Grams to offset</FieldLabel>
                <Input id="o-grams" inputMode="numeric" value={grams} onChange={(e) => setGrams(e.target.value)} placeholder="150000" autoFocus />
                <FieldDescription>
                  {defaultGrams > 0 ? `Prefilled with your outstanding net footprint (${formatGrams(defaultGrams)}).` : "Number of grams of CO₂ to retire."}
                </FieldDescription>
              </Field>
              <div className="grid grid-cols-2 gap-3">
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
                <Field>
                  <FieldLabel htmlFor="o-price">Price / tonne (₹)</FieldLabel>
                  <Input id="o-price" inputMode="decimal" value={pricePerTonne} onChange={(e) => setPricePerTonne(e.target.value)} placeholder="1200.00" />
                </Field>
              </div>
            </FieldGroup>

            <div className="flex items-center justify-between rounded-md bg-muted/50 p-3 text-sm">
              <span className="text-muted-foreground">Estimated cost</span>
              <span className="font-semibold tabular-nums">{formatInr(estCostMinor, currency)}</span>
            </div>

            {offsetM.isError && <p className="text-sm text-destructive">{errMsg(offsetM.error)}</p>}
          </div>
          <SheetFooter>
            <div className="flex justify-end gap-2">
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
              <Button type="submit" disabled={!canSubmit || offsetM.isPending}>
                {offsetM.isPending ? "Purchasing…" : "Purchase offset"}
              </Button>
            </div>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}
