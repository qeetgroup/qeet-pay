import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  DataState,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  cn,
} from "@qeetrix/ui";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { LandmarkIcon, TrendingUpIcon, WalletIcon } from "lucide-react";
import { useState } from "react";
import { Area, AreaChart, CartesianGrid, ReferenceLine, XAxis, YAxis } from "recharts";

import { buildChartConfig, inrCompact } from "@/components/chart-kit";
import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import { KpiRow, KpiTile } from "@/components/stat-tile";
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
  const chartData = points.map((p) => ({
    label: new Date(p.date).toLocaleDateString("en-IN", { day: "2-digit", month: "short" }),
    balance: p.projectedBalanceMinor / 100,
  }));
  const hasNegative = points.some((p) => p.projectedBalanceMinor < 0);
  const healthy = (data?.avgDailyNetMinor ?? 0) > 0 && (data?.projectedEndBalanceMinor ?? 0) >= 0;
  const config = buildChartConfig([{ key: "balance", label: "Projected balance" }]);

  return (
    <div className="flex min-w-0 flex-col gap-5">
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

      <KpiRow cols={3}>
        <KpiTile
          label="Starting balance"
          value={formatInr(data?.startingBalanceMinor ?? 0)}
          icon={WalletIcon}
          tone="brand"
          hint="Current settlement balance"
          loading={forecastQ.isLoading}
        />
        <KpiTile
          label="Avg daily net"
          value={formatInr(data?.avgDailyNetMinor ?? 0)}
          icon={TrendingUpIcon}
          tone={(data?.avgDailyNetMinor ?? 0) >= 0 ? "success" : "danger"}
          hint={`Trailing ${windowDays}-day window`}
          loading={forecastQ.isLoading}
        />
        <KpiTile
          label={`Projected · ${horizonDays}d`}
          value={formatInr(data?.projectedEndBalanceMinor ?? 0)}
          icon={LandmarkIcon}
          tone={(data?.projectedEndBalanceMinor ?? 0) >= 0 ? "info" : "danger"}
          hint="Balance at end of horizon"
          loading={forecastQ.isLoading}
        />
      </KpiRow>

      {data?.recommendation && (
        <div
          className={cn(
            "rounded-xl border p-4 text-sm",
            healthy
              ? "border-success/25 bg-success/5"
              : "border-warning/25 bg-warning/5",
          )}
        >
          <p className={cn("font-medium", healthy ? "text-success" : "text-warning")}>Recommendation</p>
          <p className="mt-1 leading-relaxed text-foreground/80">{data.recommendation}</p>
        </div>
      )}

      <SectionCard
        title="Projected settlement balance"
        description={`Day-by-day balance over the next ${horizonDays} days`}
      >
        <DataState
          isLoading={forecastQ.isLoading}
          isError={forecastQ.isError}
          error={forecastQ.error}
          isEmpty={chartData.length === 0}
          emptyIcon={TrendingUpIcon}
          emptyTitle="No projection available"
          emptyDescription="There is no ledger or payment history to project from yet."
          skeletonRows={8}
        >
          <ChartContainer config={config} className="h-80 w-full">
            <AreaChart data={chartData} margin={{ left: 4, right: 8, top: 8, bottom: 0 }}>
              <defs>
                <linearGradient id="fillCashflow" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--color-balance)" stopOpacity={0.26} />
                  <stop offset="100%" stopColor="var(--color-balance)" stopOpacity={0.02} />
                </linearGradient>
              </defs>
              <CartesianGrid vertical={false} strokeDasharray="3 3" />
              <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={10} minTickGap={32} />
              <YAxis
                tickLine={false}
                axisLine={false}
                width={56}
                tickFormatter={(v) => inrCompact(Number(v) * 100)}
              />
              {hasNegative && <ReferenceLine y={0} stroke="var(--color-destructive)" strokeDasharray="4 4" />}
              <ChartTooltip
                cursor={{ stroke: "var(--color-border)", strokeWidth: 1 }}
                content={
                  <ChartTooltipContent
                    indicator="dot"
                    formatter={(value) => (
                      <span className="font-medium tabular-nums">{formatInr(Number(value) * 100)}</span>
                    )}
                  />
                }
              />
              <Area
                dataKey="balance"
                type="monotone"
                stroke="var(--color-balance)"
                strokeWidth={2}
                fill="url(#fillCashflow)"
              />
            </AreaChart>
          </ChartContainer>
        </DataState>
      </SectionCard>
    </div>
  );
}
