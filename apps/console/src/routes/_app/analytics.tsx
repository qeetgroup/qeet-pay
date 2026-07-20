import {
  Badge,
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  DataState,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@qeetrix/ui";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { BanknoteIcon, CheckCircle2Icon, ReceiptIcon, RepeatIcon, TrendingUpIcon } from "lucide-react";
import { useState } from "react";
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts";

import { buildChartConfig, inrCompact } from "@/components/chart-kit";
import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import { KpiRow, KpiTile } from "@/components/stat-tile";
import { api } from "@/lib/api";
import { formatInr } from "@/lib/money";

export const Route = createFileRoute("/_app/analytics")({ component: AnalyticsPage });

type Granularity = "DAY" | "WEEK" | "MONTH";

type TpvBucket = { period: string; totalMinor: number; txCount: number };
type SuccessRate = { captured: number; failed: number; ratePercent: number };
type Arr = { mrrMinor: number; arrMinor: number };
type MrrWaterfallRow = {
  period: string;
  newMrr: number;
  expansion: number;
  contraction: number;
  churn: number;
  reactivation: number;
  netChange: number;
};

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
function daysAgoIso(n: number): string {
  return new Date(Date.now() - n * 86_400_000).toISOString().slice(0, 10);
}
function periodLabel(iso: string, granularity: Granularity): string {
  const d = new Date(iso);
  if (granularity === "MONTH") return d.toLocaleDateString("en-IN", { month: "short", year: "2-digit" });
  return d.toLocaleDateString("en-IN", { day: "2-digit", month: "short" });
}

function AnalyticsPage() {
  const [fromDate, setFromDate] = useState(daysAgoIso(30));
  const [toDate, setToDate] = useState(todayIso());
  const [granularity, setGranularity] = useState<Granularity>("DAY");

  const rangeReady = Boolean(fromDate && toDate);
  const range = () => ({
    from: new Date(`${fromDate}T00:00:00`).toISOString(),
    to: new Date(`${toDate}T23:59:59.999`).toISOString(),
  });

  const tpvQ = useQuery({
    queryKey: ["analytics-tpv", fromDate, toDate, granularity],
    queryFn: () => api<TpvBucket[]>("/v1/analytics/tpv", { query: { ...range(), granularity } }),
    enabled: rangeReady,
    staleTime: 30_000,
  });
  const rateQ = useQuery({
    queryKey: ["analytics-success-rate", fromDate, toDate],
    queryFn: () => api<SuccessRate>("/v1/analytics/success-rate", { query: range() }),
    enabled: rangeReady,
    staleTime: 30_000,
  });
  const mrrQ = useQuery({
    queryKey: ["analytics-mrr", fromDate, toDate],
    queryFn: () => api<MrrWaterfallRow[]>("/v1/analytics/mrr", { query: range() }),
    enabled: rangeReady,
    staleTime: 30_000,
  });
  const arrQ = useQuery({
    queryKey: ["analytics-arr"],
    queryFn: () => api<Arr>("/v1/analytics/arr"),
    staleTime: 60_000,
  });

  const tpv = tpvQ.data ?? [];
  const tpvTotal = tpv.reduce((s, b) => s + b.totalMinor, 0);
  const tpvCount = tpv.reduce((s, b) => s + b.txCount, 0);
  const rate = rateQ.data?.ratePercent ?? 0;
  const captured = rateQ.data?.captured ?? 0;
  const failed = rateQ.data?.failed ?? 0;
  const mrr = mrrQ.data ?? [];

  const tpvData = tpv.map((b) => ({ label: periodLabel(b.period, granularity), volume: b.totalMinor / 100 }));
  const tpvConfig = buildChartConfig([{ key: "volume", label: "Volume" }]);
  const capturedPct = captured + failed === 0 ? 0 : (captured / (captured + failed)) * 100;

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <PageHeader
        description="Total payment volume, recurring-revenue movement, and acceptance quality over a date range."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Input
              type="date"
              aria-label="From date"
              value={fromDate}
              max={toDate}
              onChange={(e) => setFromDate(e.target.value)}
              className="h-9 w-auto"
            />
            <span className="text-sm text-muted-foreground">to</span>
            <Input
              type="date"
              aria-label="To date"
              value={toDate}
              min={fromDate}
              max={todayIso()}
              onChange={(e) => setToDate(e.target.value)}
              className="h-9 w-auto"
            />
            <Select value={granularity} onValueChange={(v) => v && setGranularity(v as Granularity)}>
              <SelectTrigger size="sm" className="w-auto min-w-28">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="DAY">Daily</SelectItem>
                <SelectItem value="WEEK">Weekly</SelectItem>
                <SelectItem value="MONTH">Monthly</SelectItem>
              </SelectContent>
            </Select>
          </div>
        }
      />

      <KpiRow>
        <KpiTile
          label="Total payment volume"
          value={formatInr(tpvTotal)}
          icon={BanknoteIcon}
          tone="brand"
          spark={tpv.map((b) => b.totalMinor / 100)}
          hint={`${tpvCount.toLocaleString("en-IN")} captured payments`}
          loading={tpvQ.isLoading}
        />
        <KpiTile
          label="Success rate"
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
          hint="Monthly recurring revenue"
          loading={arrQ.isLoading}
        />
        <KpiTile
          label="ARR"
          value={formatInr(arrQ.data?.arrMinor ?? 0)}
          icon={TrendingUpIcon}
          tone="brand"
          hint="Annual run-rate"
          loading={arrQ.isLoading}
        />
      </KpiRow>

      <div className="grid gap-4 lg:grid-cols-3">
        <SectionCard
          className="lg:col-span-2"
          title="Total payment volume"
          description="Captured volume per period for the selected range"
        >
          <DataState
            isLoading={tpvQ.isLoading}
            isError={tpvQ.isError}
            error={tpvQ.error}
            isEmpty={tpvData.length === 0}
            emptyIcon={BanknoteIcon}
            emptyTitle="No captured payments"
            emptyDescription="Nothing was captured in the selected date range."
            skeletonRows={6}
          >
            <ChartContainer config={tpvConfig} className="h-66 w-full">
              <BarChart data={tpvData} margin={{ left: 4, right: 8, top: 8, bottom: 0 }}>
                <CartesianGrid vertical={false} strokeDasharray="3 3" />
                <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={10} minTickGap={24} />
                <YAxis
                  tickLine={false}
                  axisLine={false}
                  width={52}
                  tickFormatter={(v) => inrCompact(Number(v) * 100)}
                />
                <ChartTooltip
                  cursor={{ fill: "var(--color-muted)", opacity: 0.4 }}
                  content={
                    <ChartTooltipContent
                      indicator="dot"
                      formatter={(value) => (
                        <span className="font-medium tabular-nums">{formatInr(Number(value) * 100)}</span>
                      )}
                    />
                  }
                />
                <Bar dataKey="volume" fill="var(--color-volume)" radius={[4, 4, 0, 0]} maxBarSize={40} />
              </BarChart>
            </ChartContainer>
          </DataState>
        </SectionCard>

        <SectionCard title="Acceptance" description="Captured vs. failed for the range">
          <DataState
            isLoading={rateQ.isLoading}
            isError={rateQ.isError}
            error={rateQ.error}
            isEmpty={captured + failed === 0}
            emptyIcon={CheckCircle2Icon}
            emptyTitle="No attempts"
            emptyDescription="No payment attempts in this range."
            skeletonRows={4}
          >
            <div className="space-y-4">
              <div>
                <p className="font-heading text-3xl font-semibold tabular-nums">{rate.toFixed(1)}%</p>
                <p className="text-sm text-muted-foreground">success rate</p>
              </div>
              <div className="flex h-2.5 gap-0.5 overflow-hidden rounded-full">
                <div className="h-full bg-success transition-all" style={{ width: `${capturedPct}%` }} />
                <div className="h-full flex-1 bg-destructive/70" />
              </div>
              <div className="space-y-2 text-sm">
                <div className="flex items-center justify-between">
                  <span className="flex items-center gap-2 text-muted-foreground">
                    <span className="size-2.5 rounded-[3px] bg-success" aria-hidden /> Captured
                  </span>
                  <span className="font-medium tabular-nums">{captured.toLocaleString("en-IN")}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="flex items-center gap-2 text-muted-foreground">
                    <span className="size-2.5 rounded-[3px] bg-destructive" aria-hidden /> Failed
                  </span>
                  <span className="font-medium tabular-nums">{failed.toLocaleString("en-IN")}</span>
                </div>
              </div>
            </div>
          </DataState>
        </SectionCard>
      </div>

      <SectionCard
        title="MRR waterfall"
        description="New, expansion, contraction, churn and reactivation per month"
        flush
      >
        <DataState
          isLoading={mrrQ.isLoading}
          isError={mrrQ.isError}
          error={mrrQ.error}
          isEmpty={mrr.length === 0}
          emptyIcon={ReceiptIcon}
          emptyTitle="No subscription movement"
          emptyDescription="No MRR-affecting subscription events in this range."
          skeletonRows={4}
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead className="text-right">New</TableHead>
                  <TableHead className="text-right">Expansion</TableHead>
                  <TableHead className="text-right">Contraction</TableHead>
                  <TableHead className="text-right">Churn</TableHead>
                  <TableHead className="text-right">Reactivation</TableHead>
                  <TableHead className="text-right">Net</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {mrr.map((r) => (
                  <TableRow key={r.period}>
                    <TableCell className="font-medium">
                      {new Date(r.period).toLocaleDateString("en-IN", { month: "short", year: "numeric" })}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{formatInr(r.newMrr)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatInr(r.expansion)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatInr(r.contraction)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatInr(r.churn)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatInr(r.reactivation)}</TableCell>
                    <TableCell className="text-right">
                      <Badge variant={r.netChange >= 0 ? "success" : "destructive"}>
                        {formatInr(r.netChange)}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </DataState>
      </SectionCard>
    </div>
  );
}
