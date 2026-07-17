import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  DataState,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@qeetrix/ui";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { LandmarkIcon, TrendingUpIcon, WalletIcon } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/page-header";
import { api } from "@/lib/api";
import { formatInr } from "@/lib/money";

export const Route = createFileRoute("/_app/cash-flow")({ component: CashFlowPage });

type ForecastPoint = { day: number; date: string; projectedBalanceMinor: number };
type CashFlowForecast = {
  startingBalanceMinor: number;
  avgDailyNetMinor: number;
  horizonDays: number;
  projectedEndBalanceMinor: number;
  recommendation: string;
  points: ForecastPoint[];
};

const DAY_OPTIONS = ["7", "14", "30", "60", "90"];

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

function CashFlowPage() {
  const [horizonDays, setHorizonDays] = useState("30");
  const [windowDays, setWindowDays] = useState("30");

  const forecastQ = useQuery({
    queryKey: ["cash-flow-forecast", horizonDays, windowDays],
    queryFn: () =>
      api<CashFlowForecast>("/v1/analytics/cash-flow-forecast", {
        query: { horizonDays: Number(horizonDays), windowDays: Number(windowDays) },
      }),
    staleTime: 60_000,
  });

  const data = forecastQ.data;
  const points = data?.points ?? [];
  const maxAbs = Math.max(1, ...points.map((p) => Math.abs(p.projectedBalanceMinor)));
  const healthy = (data?.avgDailyNetMinor ?? 0) > 0 && (data?.projectedEndBalanceMinor ?? 0) >= 0;

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Settlement-balance projection from the ledger and trailing net payment volume, with a working-capital recommendation."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Select value={horizonDays} onValueChange={(v) => v && setHorizonDays(v)}>
              <SelectTrigger size="sm" className="w-auto min-w-32">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {DAY_OPTIONS.map((d) => (
                  <SelectItem key={d} value={d}>
                    Horizon: {d}d
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={windowDays} onValueChange={(v) => v && setWindowDays(v)}>
              <SelectTrigger size="sm" className="w-auto min-w-32">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {DAY_OPTIONS.map((d) => (
                  <SelectItem key={d} value={d}>
                    Window: {d}d
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        }
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard
          icon={<WalletIcon />}
          title="Starting balance"
          value={formatInr(data?.startingBalanceMinor ?? 0)}
          sub="Current settlement balance"
        />
        <StatCard
          icon={<TrendingUpIcon />}
          title="Avg daily net"
          value={formatInr(data?.avgDailyNetMinor ?? 0)}
          sub={`Trailing ${windowDays}-day window`}
          iconClass={
            (data?.avgDailyNetMinor ?? 0) >= 0
              ? "bg-green-500/10 text-green-600 dark:text-green-400"
              : "bg-red-500/10 text-red-600 dark:text-red-400"
          }
        />
        <StatCard
          icon={<LandmarkIcon />}
          title={`Projected (${horizonDays}d)`}
          value={formatInr(data?.projectedEndBalanceMinor ?? 0)}
          sub="Balance at end of horizon"
          iconClass={
            (data?.projectedEndBalanceMinor ?? 0) >= 0
              ? "bg-violet-500/10 text-violet-600 dark:text-violet-400"
              : "bg-red-500/10 text-red-600 dark:text-red-400"
          }
        />
      </div>

      {data?.recommendation && (
        <div
          className={`rounded-lg border p-4 text-sm ${
            healthy
              ? "border-green-500/20 bg-green-500/5 text-green-700 dark:text-green-300"
              : "border-amber-500/20 bg-amber-500/5 text-amber-700 dark:text-amber-300"
          }`}
        >
          <p className="font-medium">Recommendation</p>
          <p className="mt-1 text-foreground/80">{data.recommendation}</p>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Projected Settlement Balance</CardTitle>
          <CardDescription>Day-by-day balance over the next {horizonDays} days</CardDescription>
        </CardHeader>
        <CardContent>
          <DataState
            isLoading={forecastQ.isLoading}
            isError={forecastQ.isError}
            error={forecastQ.error}
            isEmpty={points.length === 0}
            emptyIcon={TrendingUpIcon}
            emptyTitle="No projection available"
            emptyDescription="There is no ledger or payment history to project from yet."
            skeletonRows={8}
          >
            <div className="max-h-[28rem] space-y-1.5 overflow-y-auto">
              {points.map((p) => {
                const pct = Math.round((Math.abs(p.projectedBalanceMinor) / maxAbs) * 100);
                const negative = p.projectedBalanceMinor < 0;
                return (
                  <div key={p.day} className="flex items-center gap-3">
                    <span className="w-24 shrink-0 text-xs text-muted-foreground tabular-nums">
                      {new Date(p.date).toLocaleDateString("en-IN", { day: "2-digit", month: "short" })}
                    </span>
                    <div className="h-3 flex-1 overflow-hidden rounded-full bg-muted">
                      <div
                        className={`h-full rounded-full transition-all ${negative ? "bg-red-500" : "bg-primary"}`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                    <span
                      className={`w-28 shrink-0 text-right text-xs font-medium tabular-nums ${
                        negative ? "text-red-600 dark:text-red-400" : ""
                      }`}
                    >
                      {formatInr(p.projectedBalanceMinor)}
                    </span>
                  </div>
                );
              })}
            </div>
          </DataState>
        </CardContent>
      </Card>
    </div>
  );
}
