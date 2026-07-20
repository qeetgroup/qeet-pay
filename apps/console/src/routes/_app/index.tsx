import {
  Badge,
  Button,
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  DataState,
  cn,
} from "@qeetrix/ui";
import { useQuery } from "@tanstack/react-query";
import { Link, createFileRoute } from "@tanstack/react-router";
import {
  ArrowRightIcon,
  BanknoteIcon,
  CheckCircle2Icon,
  CreditCardIcon,
  LinkIcon,
  PlusIcon,
  ReceiptIcon,
  RepeatIcon,
  TrendingUpIcon,
  WalletIcon,
} from "lucide-react";
import { Area, AreaChart, CartesianGrid, Cell, Pie, PieChart, XAxis, YAxis } from "recharts";

import { buildChartConfig, inrCompact } from "@/components/chart-kit";
import { PageHeader } from "@/components/page-header";
import { KpiRow, KpiTile } from "@/components/stat-tile";
import { SectionCard } from "@/components/section-card";
import { api } from "@/lib/api";
import { formatInr } from "@/lib/money";

export const Route = createFileRoute("/_app/")({ component: DashboardPage });

type TpvBucket = { period: string; totalMinor: number; txCount: number };
type SuccessRate = { captured: number; failed: number; ratePercent: number };
type Arr = { mrrMinor: number; arrMinor: number };
type ForecastPoint = { day: number; date: string; projectedBalanceMinor: number };
type CashFlowForecast = {
  startingBalanceMinor: number;
  avgDailyNetMinor: number;
  projectedEndBalanceMinor: number;
  recommendation: string;
  points: ForecastPoint[];
};
type PaymentView = {
  id: string;
  amountMinor: number;
  currency: string;
  method: string;
  status: string;
};

function isoDaysAgo(days: number): string {
  return new Date(Date.now() - days * 86_400_000).toISOString();
}
function dayLabel(iso: string): string {
  return new Date(iso).toLocaleDateString("en-IN", { day: "2-digit", month: "short" });
}
function pctDelta(recent: number, prior: number): { delta: string; trend: "up" | "down" | "neutral" } | null {
  if (prior <= 0) return null;
  const pct = ((recent - prior) / prior) * 100;
  return {
    delta: `${pct >= 0 ? "+" : ""}${pct.toFixed(1)}%`,
    trend: pct > 0.05 ? "up" : pct < -0.05 ? "down" : "neutral",
  };
}

const PAYMENT_TONE: Record<string, "success" | "warning" | "destructive" | "muted" | "secondary"> = {
  CAPTURED: "success",
  AUTHORIZED: "warning",
  FAILED: "destructive",
  CANCELLED: "muted",
  CREATED: "secondary",
};

function DashboardPage() {
  const from = isoDaysAgo(30);
  const to = new Date().toISOString();

  const tpvQ = useQuery({
    queryKey: ["dash-tpv"],
    queryFn: () => api<TpvBucket[]>("/v1/analytics/tpv", { query: { from, to, granularity: "DAY" } }),
    staleTime: 30_000,
  });
  const rateQ = useQuery({
    queryKey: ["dash-success-rate"],
    queryFn: () => api<SuccessRate>("/v1/analytics/success-rate", { query: { from, to } }),
    staleTime: 30_000,
  });
  const arrQ = useQuery({
    queryKey: ["dash-arr"],
    queryFn: () => api<Arr>("/v1/analytics/arr"),
    staleTime: 60_000,
  });
  const forecastQ = useQuery({
    queryKey: ["dash-forecast"],
    queryFn: () =>
      api<CashFlowForecast>("/v1/analytics/cash-flow-forecast", {
        query: { horizonDays: 30, windowDays: 30 },
      }),
    staleTime: 60_000,
  });
  const paymentsQ = useQuery({
    queryKey: ["dash-payments"],
    queryFn: () => api<PaymentView[]>("/v1/payments"),
    staleTime: 15_000,
  });

  const buckets = tpvQ.data ?? [];
  const tpvTotal = buckets.reduce((s, b) => s + b.totalMinor, 0);
  const tpvCount = buckets.reduce((s, b) => s + b.txCount, 0);
  const tpvSpark = buckets.map((b) => b.totalMinor / 100);
  const half = Math.floor(buckets.length / 2);
  const tpvDelta = pctDelta(
    buckets.slice(half).reduce((s, b) => s + b.totalMinor, 0),
    buckets.slice(0, half).reduce((s, b) => s + b.totalMinor, 0),
  );

  const rate = rateQ.data?.ratePercent ?? 0;
  const captured = rateQ.data?.captured ?? 0;
  const failed = rateQ.data?.failed ?? 0;

  const forecast = forecastQ.data;
  const forecastData = (forecast?.points ?? []).map((p) => ({
    label: dayLabel(p.date),
    balance: p.projectedBalanceMinor / 100,
  }));

  const tpvData = buckets.map((b) => ({ label: dayLabel(b.period), volume: b.totalMinor / 100 }));
  const tpvConfig = buildChartConfig([{ key: "volume", label: "Volume" }]);
  const forecastConfig = buildChartConfig([{ key: "balance", label: "Balance" }]);
  const acceptanceConfig = {
    captured: { label: "Captured", color: "var(--color-success)" },
    failed: { label: "Failed", color: "var(--color-destructive)" },
  };
  const acceptanceData = [
    { name: "captured", value: captured },
    { name: "failed", value: failed },
  ];

  const recentPayments = (paymentsQ.data ?? []).slice(0, 6);

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <PageHeader
        description="Real-time overview of payments, recurring revenue, and money movement across your account."
        actions={
          <>
            <Button variant="outline" size="sm" render={<Link to={"/payouts" as never} />}>
              <BanknoteIcon /> New payout
            </Button>
            <Button size="sm" render={<Link to={"/payments" as never} />}>
              <PlusIcon /> Create payment
            </Button>
          </>
        }
      />

      <KpiRow>
        <KpiTile
          label="TPV · 30 days"
          value={formatInr(tpvTotal)}
          icon={BanknoteIcon}
          tone="brand"
          spark={tpvSpark}
          delta={tpvDelta?.delta}
          trend={tpvDelta?.trend}
          hint={`${tpvCount.toLocaleString("en-IN")} payments`}
          loading={tpvQ.isLoading}
          to="/analytics"
        />
        <KpiTile
          label="Success rate · 30d"
          value={`${rate.toFixed(1)}%`}
          icon={CheckCircle2Icon}
          tone="success"
          hint={`${captured.toLocaleString("en-IN")} captured · ${failed.toLocaleString("en-IN")} failed`}
          loading={rateQ.isLoading}
        />
        <KpiTile
          label="MRR"
          value={formatInr(arrQ.data?.mrrMinor ?? 0)}
          icon={RepeatIcon}
          tone="info"
          hint={`${formatInr(arrQ.data?.arrMinor ?? 0)} ARR`}
          loading={arrQ.isLoading}
          to="/subscriptions"
        />
        <KpiTile
          label="Projected balance · 30d"
          value={formatInr(forecast?.projectedEndBalanceMinor ?? 0)}
          icon={WalletIcon}
          tone={(forecast?.projectedEndBalanceMinor ?? 0) >= 0 ? "brand" : "danger"}
          hint={`${formatInr(forecast?.avgDailyNetMinor ?? 0)}/day net`}
          loading={forecastQ.isLoading}
          to="/cash-flow"
        />
      </KpiRow>

      <div className="grid gap-4 lg:grid-cols-3">
        <SectionCard
          className="lg:col-span-2"
          title="Total payment volume"
          description="Captured volume per day, last 30 days"
        >
          <DataState
            isLoading={tpvQ.isLoading}
            isError={tpvQ.isError}
            error={tpvQ.error}
            isEmpty={tpvData.length === 0}
            emptyIcon={BanknoteIcon}
            emptyTitle="No captured payments"
            emptyDescription="Captured volume from the last 30 days will chart here."
            skeletonRows={5}
          >
            <ChartContainer config={tpvConfig} className="h-62 w-full">
              <AreaChart data={tpvData} margin={{ left: 4, right: 8, top: 8, bottom: 0 }}>
                <defs>
                  <linearGradient id="fillVolume" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-volume)" stopOpacity={0.28} />
                    <stop offset="100%" stopColor="var(--color-volume)" stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid vertical={false} strokeDasharray="3 3" />
                <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={10} minTickGap={28} />
                <YAxis
                  tickLine={false}
                  axisLine={false}
                  width={52}
                  tickFormatter={(v) => inrCompact(Number(v) * 100)}
                />
                <ChartTooltip
                  cursor={{ stroke: "var(--color-border)", strokeWidth: 1 }}
                  content={
                    <ChartTooltipContent
                      indicator="dot"
                      formatter={(value) => (
                        <span className="font-medium tabular-nums">
                          {formatInr(Number(value) * 100)}
                        </span>
                      )}
                    />
                  }
                />
                <Area
                  dataKey="volume"
                  type="monotone"
                  stroke="var(--color-volume)"
                  strokeWidth={2}
                  fill="url(#fillVolume)"
                />
              </AreaChart>
            </ChartContainer>
          </DataState>
        </SectionCard>

        <SectionCard title="Acceptance" description="Captured vs. failed, last 30 days">
          <DataState
            isLoading={rateQ.isLoading}
            isError={rateQ.isError}
            error={rateQ.error}
            isEmpty={captured + failed === 0}
            emptyIcon={CheckCircle2Icon}
            emptyTitle="No attempts yet"
            emptyDescription="Acceptance quality appears once payments are attempted."
            skeletonRows={4}
          >
            <div className="flex flex-col items-center">
              <div className="relative">
                <ChartContainer config={acceptanceConfig} className="aspect-square h-42">
                  <PieChart>
                    <ChartTooltip content={<ChartTooltipContent nameKey="name" hideLabel />} />
                    <Pie
                      data={acceptanceData}
                      dataKey="value"
                      nameKey="name"
                      innerRadius={54}
                      outerRadius={78}
                      strokeWidth={2}
                      paddingAngle={2}
                    >
                      {acceptanceData.map((entry) => (
                        <Cell key={entry.name} fill={`var(--color-${entry.name})`} />
                      ))}
                    </Pie>
                  </PieChart>
                </ChartContainer>
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                  <span className="font-heading text-2xl font-semibold tabular-nums">
                    {rate.toFixed(1)}%
                  </span>
                  <span className="text-[11px] text-muted-foreground">success</span>
                </div>
              </div>
              <div className="mt-4 w-full space-y-2 text-sm">
                <LegendRow color="var(--color-success)" label="Captured" value={captured} />
                <LegendRow color="var(--color-destructive)" label="Failed" value={failed} />
              </div>
            </div>
          </DataState>
        </SectionCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <SectionCard
          className="lg:col-span-2"
          title="Cash-flow forecast"
          description={`30-day settlement projection${forecast ? ` · ${formatInr(forecast.projectedEndBalanceMinor)} at horizon` : ""}`}
        >
          <DataState
            isLoading={forecastQ.isLoading}
            isError={forecastQ.isError}
            error={forecastQ.error}
            isEmpty={forecastData.length === 0}
            emptyIcon={TrendingUpIcon}
            emptyTitle="No projection yet"
            emptyDescription="A projection appears once there is ledger and payment history."
            skeletonRows={5}
          >
            <ChartContainer config={forecastConfig} className="h-50 w-full">
              <AreaChart data={forecastData} margin={{ left: 4, right: 8, top: 8, bottom: 0 }}>
                <defs>
                  <linearGradient id="fillBalance" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-balance)" stopOpacity={0.24} />
                    <stop offset="100%" stopColor="var(--color-balance)" stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid vertical={false} strokeDasharray="3 3" />
                <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={10} minTickGap={28} />
                <YAxis
                  tickLine={false}
                  axisLine={false}
                  width={52}
                  tickFormatter={(v) => inrCompact(Number(v) * 100)}
                />
                <ChartTooltip
                  cursor={{ stroke: "var(--color-border)", strokeWidth: 1 }}
                  content={
                    <ChartTooltipContent
                      indicator="dot"
                      formatter={(value) => (
                        <span className="font-medium tabular-nums">
                          {formatInr(Number(value) * 100)}
                        </span>
                      )}
                    />
                  }
                />
                <Area
                  dataKey="balance"
                  type="monotone"
                  stroke="var(--color-balance)"
                  strokeWidth={2}
                  fill="url(#fillBalance)"
                />
              </AreaChart>
            </ChartContainer>
            {forecast?.recommendation && (
              <p className="mt-3 rounded-lg bg-muted/60 p-3 text-xs leading-relaxed text-muted-foreground">
                {forecast.recommendation}
              </p>
            )}
          </DataState>
        </SectionCard>

        <SectionCard title="Quick actions" description="Jump into a common task">
          <div className="grid grid-cols-1 gap-2">
            <QuickAction to="/payments" icon={CreditCardIcon} label="Accept a payment" />
            <QuickAction to="/payment-links" icon={LinkIcon} label="Create a payment link" />
            <QuickAction to="/payouts" icon={BanknoteIcon} label="Send a payout" />
            <QuickAction to="/gst-invoices" icon={ReceiptIcon} label="Issue a GST invoice" />
          </div>
        </SectionCard>
      </div>

      <SectionCard
        title="Recent payments"
        description="The latest activity on your account"
        action={
          <Button variant="ghost" size="sm" render={<Link to={"/payments" as never} />}>
            View all <ArrowRightIcon />
          </Button>
        }
      >
        <DataState
          isLoading={paymentsQ.isLoading}
          isError={paymentsQ.isError}
          error={paymentsQ.error}
          isEmpty={recentPayments.length === 0}
          emptyIcon={CreditCardIcon}
          emptyTitle="No payments yet"
          emptyDescription="Payments you accept will appear here."
          skeletonRows={4}
        >
          <ul className="divide-y divide-border/60">
            {recentPayments.map((p) => (
              <li key={p.id} className="flex items-center gap-3 py-2.5 first:pt-0 last:pb-0">
                <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
                  <CreditCardIcon className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-mono text-xs text-muted-foreground">{p.id}</p>
                  <p className="text-xs text-muted-foreground">{p.method.replace("_", " ")}</p>
                </div>
                <span className="text-sm font-medium tabular-nums">
                  {formatInr(p.amountMinor, p.currency)}
                </span>
                <Badge variant={PAYMENT_TONE[p.status] ?? "secondary"}>{p.status}</Badge>
              </li>
            ))}
          </ul>
        </DataState>
      </SectionCard>
    </div>
  );
}

function LegendRow({ color, label, value }: { color: string; label: string; value: number }) {
  return (
    <div className="flex items-center justify-between">
      <span className="flex items-center gap-2 text-muted-foreground">
        <span className="size-2.5 rounded-[3px]" style={{ background: color }} aria-hidden />
        {label}
      </span>
      <span className="font-medium tabular-nums">{value.toLocaleString("en-IN")}</span>
    </div>
  );
}

function QuickAction({
  to,
  icon: Icon,
  label,
}: {
  to: string;
  icon: React.ComponentType<{ className?: string }>;
  label: string;
}) {
  return (
    <Link
      to={to as never}
      className={cn(
        "group/qa flex items-center gap-3 rounded-lg border border-border/60 px-3 py-2.5 text-sm font-medium transition-colors",
        "hover:border-border hover:bg-muted/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
      )}
    >
      <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary [&_svg]:size-4">
        <Icon />
      </span>
      {label}
      <ArrowRightIcon className="ms-auto size-4 text-muted-foreground transition-transform group-hover/qa:translate-x-0.5" />
    </Link>
  );
}
