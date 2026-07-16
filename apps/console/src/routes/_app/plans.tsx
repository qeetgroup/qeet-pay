import {
  Badge,
  Button,
  Card,
  DataState,
  Field,
  FieldError,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Separator,
  Sheet,
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
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, createFileRoute } from "@tanstack/react-router";
import { GaugeIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";
import { type CsvColumn, exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/plans")({ component: PlansPage });

// ── Types & options (mirrors billing/BillingController PlanView + enums) ──────

type PlanView = {
  id: string;
  code: string;
  amountMinor: number;
  currency: string;
  interval: string;
  pricingModel: string;
  trialDays: number;
};

const INTERVALS = ["MONTH", "YEAR"] as const;
const PRICING_MODELS = ["FLAT", "PER_UNIT", "TIERED", "VOLUME", "HYBRID"] as const;

const INTERVAL_LABEL: Record<string, string> = { MONTH: "Monthly", YEAR: "Yearly" };
const PRICING_LABEL: Record<string, string> = {
  FLAT: "Flat",
  PER_UNIT: "Per unit",
  TIERED: "Tiered",
  VOLUME: "Volume",
  HYBRID: "Hybrid",
};

const EMPTY_FORM = {
  code: "",
  name: "",
  amount: "",
  currency: "INR",
  interval: "MONTH",
  pricingModel: "FLAT",
  trialDays: "0",
  tiers: "",
  usageMetricKey: "",
};

function PlansPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [amountError, setAmountError] = useState<string | null>(null);

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const plansQ = useQuery({
    queryKey: ["plans"],
    queryFn: () => api<PlanView[]>("/v1/plans"),
    staleTime: 15_000,
  });

  const createM = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      api<PlanView>("/v1/plans", { method: "POST", body }),
    onSuccess: (plan) => {
      qc.invalidateQueries({ queryKey: ["plans"] });
      toast.success(`Plan ${plan.code} created`);
      setOpen(false);
      setForm({ ...EMPTY_FORM });
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Failed to create plan"),
  });

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const amountMinor = rupeesToMinor(form.amount);
    if (amountMinor === null || amountMinor <= 0) {
      setAmountError("Enter a valid amount greater than zero.");
      return;
    }
    setAmountError(null);
    const flat = form.pricingModel === "FLAT";
    createM.mutate({
      code: form.code.trim(),
      name: form.name.trim(),
      amountMinor,
      currency: form.currency.trim() || "INR",
      interval: form.interval,
      pricingModel: form.pricingModel,
      trialDays: Number(form.trialDays) || 0,
      tiers: !flat && form.tiers.trim() ? form.tiers.trim() : undefined,
      usageMetricKey: !flat && form.usageMetricKey.trim() ? form.usageMetricKey.trim() : undefined,
    });
  }

  const lv = useListView(plansQ.data ?? [], {
    searchFields: (p) => [p.code, p.currency, p.interval, p.pricingModel],
    filterFields: {
      interval: (p) => p.interval,
      pricingModel: (p) => p.pricingModel,
    },
    sortFields: {
      code: (p) => p.code,
      amountMinor: (p) => p.amountMinor,
      trialDays: (p) => p.trialDays,
    },
  });

  const columns: CsvColumn<PlanView>[] = [
    { header: "ID", value: (p) => p.id },
    { header: "Code", value: (p) => p.code },
    { header: "Amount (minor)", value: (p) => p.amountMinor },
    { header: "Currency", value: (p) => p.currency },
    { header: "Interval", value: (p) => p.interval },
    { header: "Pricing model", value: (p) => p.pricingModel },
    { header: "Trial days", value: (p) => p.trialDays },
  ];

  const showUsage = form.pricingModel !== "FLAT";

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Recurring pricing plans customers can subscribe to."
        actions={
          <Button onClick={() => setOpen(true)}>
            <PlusIcon /> Create plan
          </Button>
        }
      />

      <Card className="gap-0 overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search plans…"
          filters={[
            {
              id: "interval",
              label: "Interval",
              value: lv.filters.interval ?? "",
              options: INTERVALS.map((i) => ({ label: INTERVAL_LABEL[i], value: i })),
              onChange: (v) => lv.setFilter("interval", v),
            },
            {
              id: "pricingModel",
              label: "Pricing",
              value: lv.filters.pricingModel ?? "",
              options: PRICING_MODELS.map((m) => ({ label: PRICING_LABEL[m], value: m })),
              onChange: (v) => lv.setFilter("pricingModel", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("plans", lv.view, columns)
              : exportToJson("plans", lv.view)
          }
          exportDisabled={lv.view.length === 0}
        >
          <Button size="sm" onClick={() => setOpen(true)}>
            <PlusIcon /> New plan
          </Button>
        </ListToolbar>

        <DataState
          isLoading={plansQ.isLoading}
          isError={plansQ.isError}
          error={plansQ.error}
          isEmpty={lv.view.length === 0}
          empty={
            <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
              <div className="flex size-11 items-center justify-center rounded-full bg-muted text-muted-foreground">
                <GaugeIcon className="size-5" />
              </div>
              <div className="space-y-1">
                <p className="font-heading text-sm font-medium">No plans yet</p>
                <p className="mx-auto max-w-sm text-sm text-muted-foreground">
                  Create a recurring pricing plan, then wire it to a{" "}
                  <Link to={"/subscriptions" as never} className="underline underline-offset-2">
                    subscription
                  </Link>
                  .
                </p>
              </div>
              <Button size="sm" onClick={() => setOpen(true)}>
                <PlusIcon /> Create plan
              </Button>
            </div>
          }
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <SortHeader columnKey="code" sort={lv.sort} onToggle={lv.toggleSort}>
                    Code
                  </SortHeader>
                  <SortHeader columnKey="amountMinor" sort={lv.sort} onToggle={lv.toggleSort}>
                    Amount
                  </SortHeader>
                  <TableHead>Pricing model</TableHead>
                  <SortHeader columnKey="trialDays" sort={lv.sort} onToggle={lv.toggleSort}>
                    Trial
                  </SortHeader>
                  <TableHead>Plan ID</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((p) => (
                  <TableRow key={p.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                    <TableCell className="font-medium">{p.code}</TableCell>
                    <TableCell className="tabular-nums">
                      {formatInr(p.amountMinor, p.currency)}
                      <span className="text-muted-foreground">
                        {" "}
                        / {INTERVAL_LABEL[p.interval]?.toLowerCase() ?? p.interval.toLowerCase()}
                      </span>
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">{PRICING_LABEL[p.pricingModel] ?? p.pricingModel}</Badge>
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {p.trialDays > 0 ? `${p.trialDays} days` : "—"}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">{p.id}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </DataState>
      </Card>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="w-full sm:max-w-md">
          <SheetHeader>
            <SheetTitle>Create plan</SheetTitle>
            <SheetDescription>Define a recurring pricing plan.</SheetDescription>
          </SheetHeader>

          <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
            <div className="flex-1 space-y-4 overflow-y-auto px-4">
              <Field>
                <FieldLabel htmlFor="plan-code">Code</FieldLabel>
                <Input
                  id="plan-code"
                  required
                  value={form.code}
                  onChange={(e) => set("code", e.target.value)}
                  placeholder="pro-monthly"
                />
              </Field>

              <Field>
                <FieldLabel htmlFor="plan-name">Name</FieldLabel>
                <Input
                  id="plan-name"
                  required
                  value={form.name}
                  onChange={(e) => set("name", e.target.value)}
                  placeholder="Pro (Monthly)"
                />
              </Field>

              <div className="grid grid-cols-2 gap-3">
                <Field>
                  <FieldLabel htmlFor="plan-amount">Amount (₹)</FieldLabel>
                  <Input
                    id="plan-amount"
                    required
                    inputMode="decimal"
                    value={form.amount}
                    onChange={(e) => set("amount", e.target.value)}
                    placeholder="2360.00"
                  />
                  {amountError && <FieldError errors={[{ message: amountError }]} />}
                </Field>
                <Field>
                  <FieldLabel htmlFor="plan-currency">Currency</FieldLabel>
                  <Input
                    id="plan-currency"
                    value={form.currency}
                    onChange={(e) => set("currency", e.target.value.toUpperCase())}
                    placeholder="INR"
                  />
                </Field>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <Field>
                  <FieldLabel>Interval</FieldLabel>
                  <Select value={form.interval} onValueChange={(v) => v && set("interval", String(v))}>
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Interval">
                        {(v) => INTERVAL_LABEL[String(v)] ?? "Interval"}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      {INTERVALS.map((i) => (
                        <SelectItem key={i} value={i}>
                          {INTERVAL_LABEL[i]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </Field>
                <Field>
                  <FieldLabel htmlFor="plan-trial">Trial days</FieldLabel>
                  <Input
                    id="plan-trial"
                    inputMode="numeric"
                    value={form.trialDays}
                    onChange={(e) => set("trialDays", e.target.value)}
                    placeholder="0"
                  />
                </Field>
              </div>

              <Field>
                <FieldLabel>Pricing model</FieldLabel>
                <Select
                  value={form.pricingModel}
                  onValueChange={(v) => v && set("pricingModel", String(v))}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Pricing model">
                      {(v) => PRICING_LABEL[String(v)] ?? "Pricing model"}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {PRICING_MODELS.map((m) => (
                      <SelectItem key={m} value={m}>
                        {PRICING_LABEL[m]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>

              {showUsage && (
                <>
                  <Separator />
                  <Field>
                    <FieldLabel htmlFor="plan-metric">Usage metric key</FieldLabel>
                    <Input
                      id="plan-metric"
                      value={form.usageMetricKey}
                      onChange={(e) => set("usageMetricKey", e.target.value)}
                      placeholder="api_calls"
                    />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="plan-tiers">Tiers (JSON)</FieldLabel>
                    <Textarea
                      id="plan-tiers"
                      rows={4}
                      value={form.tiers}
                      onChange={(e) => set("tiers", e.target.value)}
                      placeholder='[{"upTo":1000,"unitPriceMinor":100}]'
                    />
                  </Field>
                </>
              )}
            </div>

            <SheetFooter className="flex-row justify-end gap-2 border-t">
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createM.isPending}>
                {createM.isPending ? "Creating…" : "Create plan"}
              </Button>
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>
    </div>
  );
}
