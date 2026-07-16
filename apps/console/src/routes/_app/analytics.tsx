import {
  Badge,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
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
import {
  BanknoteIcon,
  CheckCircleIcon,
  ReceiptIcon,
  RepeatIcon,
  TrendingUpIcon,
} from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/page-header";
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
  const maxBucket = Math.max(1, ...tpv.map((b) => b.totalMinor));
  const rate = rateQ.data?.ratePercent ?? 0;
  const captured = rateQ.data?.captured ?? 0;
  const failed = rateQ.data?.failed ?? 0;
  const mrr = mrrQ.data ?? [];

  return (
    <div className="flex min-w-0 flex-col gap-4">
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

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          icon={<BanknoteIcon />}
          title="Total payment volume"
          value={formatInr(tpvTotal)}
          sub={`${tpvCount.toLocaleString("en-IN")} captured payments`}
        />
        <StatCard
          icon={<CheckCircleIcon />}
          title="Success rate"
          value={`${rate.toFixed(1)}%`}
          sub={`${captured.toLocaleString("en-IN")} captured / ${failed.toLocaleString("en-IN")} failed`}
          iconClass="bg-green-500/10 text-green-600 dark:text-green-400"
        />
        <StatCard
          icon={<RepeatIcon />}
          title="MRR"
          value={formatInr(arrQ.data?.mrrMinor ?? 0)}
          sub="Monthly recurring revenue"
          iconClass="bg-blue-500/10 text-blue-600 dark:text-blue-400"
        />
        <StatCard
          icon={<TrendingUpIcon />}
          title="ARR"
          value={formatInr(arrQ.data?.arrMinor ?? 0)}
          sub="Annual run-rate"
          iconClass="bg-violet-500/10 text-violet-600 dark:text-violet-400"
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Total Payment Volume</CardTitle>
            <CardDescription>Captured volume per period for the selected range</CardDescription>
          </CardHeader>
          <CardContent>
            <DataState
              isLoading={tpvQ.isLoading}
              isError={tpvQ.isError}
              error={tpvQ.error}
              isEmpty={tpv.length === 0}
              emptyIcon={BanknoteIcon}
              emptyTitle="No captured payments"
              emptyDescription="Nothing was captured in the selected date range."
              skeletonRows={6}
            >
              <div className="space-y-1.5">
                {tpv.map((b) => {
                  const pct = Math.round((b.totalMinor / maxBucket) * 100);
                  return (
                    <div key={b.period} className="flex items-center gap-3">
                      <span className="w-20 shrink-0 text-xs text-muted-foreground tabular-nums">
                        {periodLabel(b.period, granularity)}
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
            </DataState>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Acceptance</CardTitle>
            <CardDescription>Captured vs. failed for the range</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <p className="text-3xl font-semibold tabular-nums">{rate.toFixed(1)}%</p>
              <p className="text-sm text-muted-foreground">success rate</p>
            </div>
            <div className="flex h-3 overflow-hidden rounded-full bg-muted">
              <div
                className="h-full bg-green-500 transition-all"
                style={{ width: `${captured + failed === 0 ? 0 : (captured / (captured + failed)) * 100}%` }}
              />
            </div>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Captured</span>
                <span className="font-medium tabular-nums">{captured.toLocaleString("en-IN")}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Failed</span>
                <span className="font-medium tabular-nums">{failed.toLocaleString("en-IN")}</span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>MRR Waterfall</CardTitle>
          <CardDescription>New, expansion, contraction, churn and reactivation per month</CardDescription>
        </CardHeader>
        <CardContent>
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
                        <Badge variant={r.netChange >= 0 ? "success" : "destructive"}>{formatInr(r.netChange)}</Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </DataState>
        </CardContent>
      </Card>
    </div>
  );
}
