import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@qeetrix/ui";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
  BanknoteIcon,
  CheckCircleIcon,
  RepeatIcon,
  TrendingUpIcon,
} from "lucide-react";

import { PageHeader } from "@/components/page-header";
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

function isoDaysAgo(days: number): string {
  return new Date(Date.now() - days * 86_400_000).toISOString();
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
  value: string | number;
  sub?: string;
  iconClass?: string;
}) {
  return (
    <Card>
      <CardContent className="flex items-center gap-4 pt-6">
        <div className={`grid size-10 shrink-0 place-items-center rounded-lg [&_svg]:size-5 ${iconClass}`}>
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
    queryFn: () => api<CashFlowForecast>("/v1/analytics/cash-flow-forecast", { query: { horizonDays: 30, windowDays: 30 } }),
    staleTime: 60_000,
  });

  const tpvTotal = (tpvQ.data ?? []).reduce((s, b) => s + b.totalMinor, 0);
  const tpvCount = (tpvQ.data ?? []).reduce((s, b) => s + b.txCount, 0);
  const rate = rateQ.data?.ratePercent ?? 0;
  const maxBucket = Math.max(1, ...(tpvQ.data ?? []).map((b) => b.totalMinor));

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Real-time overview of payments, billing, and money movement." />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          icon={<BanknoteIcon />}
          title="TPV (30 days)"
          value={formatInr(tpvTotal)}
          sub={`${tpvCount.toLocaleString("en-IN")} captured payments`}
        />
        <StatCard
          icon={<CheckCircleIcon />}
          title="Success rate (30d)"
          value={`${rate.toFixed(1)}%`}
          sub={`${(rateQ.data?.captured ?? 0).toLocaleString("en-IN")} captured / ${(rateQ.data?.failed ?? 0).toLocaleString("en-IN")} failed`}
          iconClass="bg-green-500/10 text-green-600 dark:text-green-400"
        />
        <StatCard
          icon={<RepeatIcon />}
          title="MRR"
          value={formatInr(arrQ.data?.mrrMinor ?? 0)}
          sub={`${formatInr(arrQ.data?.arrMinor ?? 0)} ARR`}
          iconClass="bg-blue-500/10 text-blue-600 dark:text-blue-400"
        />
        <StatCard
          icon={<TrendingUpIcon />}
          title="Projected balance (30d)"
          value={formatInr(forecastQ.data?.projectedEndBalanceMinor ?? 0)}
          sub={`${formatInr(forecastQ.data?.avgDailyNetMinor ?? 0)}/day net`}
          iconClass="bg-violet-500/10 text-violet-600 dark:text-violet-400"
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Total Payment Volume</CardTitle>
            <CardDescription>Captured volume per day, last 30 days</CardDescription>
          </CardHeader>
          <CardContent>
            {tpvQ.isLoading ? (
              <div className="space-y-2">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div key={i} className="h-6 animate-pulse rounded bg-muted" />
                ))}
              </div>
            ) : (tpvQ.data ?? []).length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">
                No captured payments in the last 30 days.
              </p>
            ) : (
              <div className="space-y-1.5">
                {(tpvQ.data ?? []).map((b) => {
                  const pct = Math.round((b.totalMinor / maxBucket) * 100);
                  return (
                    <div key={b.period} className="flex items-center gap-3">
                      <span className="w-20 shrink-0 text-xs text-muted-foreground tabular-nums">
                        {new Date(b.period).toLocaleDateString("en-IN", { day: "2-digit", month: "short" })}
                      </span>
                      <div className="h-3 flex-1 overflow-hidden rounded-full bg-muted">
                        <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${pct}%` }} />
                      </div>
                      <span className="w-24 shrink-0 text-right text-xs font-medium tabular-nums">
                        {formatInr(b.totalMinor)}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Cash-Flow Forecast</CardTitle>
            <CardDescription>30-day settlement projection</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Starting balance</span>
                <span className="font-medium tabular-nums">{formatInr(forecastQ.data?.startingBalanceMinor ?? 0)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Avg daily net</span>
                <span className="font-medium tabular-nums">{formatInr(forecastQ.data?.avgDailyNetMinor ?? 0)}</span>
              </div>
              <div className="flex justify-between border-t pt-2">
                <span className="text-muted-foreground">Projected (30d)</span>
                <span className="font-semibold tabular-nums">{formatInr(forecastQ.data?.projectedEndBalanceMinor ?? 0)}</span>
              </div>
            </div>
            {forecastQ.data?.recommendation && (
              <p className="rounded-md bg-muted/50 p-3 text-xs text-muted-foreground">
                {forecastQ.data.recommendation}
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
