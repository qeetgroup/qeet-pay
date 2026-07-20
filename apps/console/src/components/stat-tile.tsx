import { Skeleton, Sparkline, cn } from "@qeetrix/ui";
import { Link } from "@tanstack/react-router";
import { ArrowDownRightIcon, ArrowUpRightIcon } from "lucide-react";
import type { ComponentType, ReactNode } from "react";

// ── Shared KPI tiles ────────────────────────────────────────────────────────
// The dashboard/analytics/detail metric tile used across the console. A single
// premium tile so every headline number reads the same: a tinted icon chip, a
// large tabular value, an optional trend delta and a hint or inline sparkline.

export type StatTone = "brand" | "success" | "info" | "warning" | "danger" | "neutral";

const TONE_CHIP: Record<StatTone, string> = {
  brand: "bg-primary/10 text-primary",
  success: "bg-success/10 text-success",
  info: "bg-info/10 text-info",
  warning: "bg-warning/10 text-warning",
  danger: "bg-destructive/10 text-destructive",
  neutral: "bg-muted text-muted-foreground",
};

const TONE_SPARK: Record<StatTone, string> = {
  brand: "text-primary",
  success: "text-success",
  info: "text-info",
  warning: "text-warning",
  danger: "text-destructive",
  neutral: "text-muted-foreground",
};

export type Trend = "up" | "down" | "neutral";

type KpiTileProps = {
  label: ReactNode;
  value: ReactNode;
  icon?: ComponentType<{ className?: string }>;
  tone?: StatTone;
  /** Change indicator, e.g. "+12.4%". Coloured + arrowed by `trend`. */
  delta?: ReactNode;
  trend?: Trend;
  /** Supporting line under the value (mutually complements `delta`). */
  hint?: ReactNode;
  /** A tiny inline trend series drawn at the foot of the tile. */
  spark?: number[];
  /** Makes the whole tile a navigable card with a hover lift. */
  to?: string;
  loading?: boolean;
  className?: string;
};

const TREND_COLOR: Record<Trend, string> = {
  up: "text-success",
  down: "text-destructive",
  neutral: "text-muted-foreground",
};

export function KpiTile({
  label,
  value,
  icon: Icon,
  tone = "brand",
  delta,
  trend = "neutral",
  hint,
  spark,
  to,
  loading,
  className,
}: KpiTileProps) {
  const TrendIcon = trend === "up" ? ArrowUpRightIcon : trend === "down" ? ArrowDownRightIcon : null;

  const inner = (
    <>
      <div className="flex items-start justify-between gap-3">
        <span className="text-sm font-medium text-muted-foreground">{label}</span>
        {Icon && (
          <span
            className={cn(
              "grid size-9 shrink-0 place-items-center rounded-lg [&_svg]:size-[1.05rem]",
              TONE_CHIP[tone],
            )}
          >
            <Icon />
          </span>
        )}
      </div>

      <div className="mt-2">
        {loading ? (
          <Skeleton className="h-8 w-28" />
        ) : (
          <span className="block truncate font-heading text-[1.7rem] leading-none font-semibold tracking-tight tabular-nums text-foreground">
            {value}
          </span>
        )}
      </div>

      {(delta != null || hint) && !loading && (
        <div className="mt-2 flex items-center gap-2 text-xs">
          {delta != null && (
            <span className={cn("inline-flex items-center gap-0.5 font-medium tabular-nums", TREND_COLOR[trend])}>
              {TrendIcon && <TrendIcon className="size-3.5" />}
              {delta}
            </span>
          )}
          {hint && <span className="truncate text-muted-foreground">{hint}</span>}
        </div>
      )}

      {spark && spark.length > 1 && !loading && (
        <Sparkline data={spark} type="area" height={30} className={cn("mt-3 w-full", TONE_SPARK[tone])} />
      )}
    </>
  );

  const base =
    "group/kpi flex flex-col rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10 shadow-rest";

  if (to) {
    return (
      <Link
        to={to as never}
        className={cn(
          base,
          "transition-shadow hover:shadow-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
          className,
        )}
      >
        {inner}
      </Link>
    );
  }

  return <div className={cn(base, className)}>{inner}</div>;
}

/** Responsive KPI grid with a staggered entrance. Defaults to 4-up on desktop. */
export function KpiRow({
  children,
  cols = 4,
  className,
}: {
  children: ReactNode;
  cols?: 2 | 3 | 4;
  className?: string;
}) {
  const grid =
    cols === 2
      ? "sm:grid-cols-2"
      : cols === 3
        ? "sm:grid-cols-2 lg:grid-cols-3"
        : "sm:grid-cols-2 lg:grid-cols-4";
  return <div className={cn("qp-stagger grid grid-cols-1 gap-4", grid, className)}>{children}</div>;
}
