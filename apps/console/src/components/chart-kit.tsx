import type { ChartConfig } from "@qeetrix/ui";

// Shared chart helpers so every chart in the console draws from one system:
// the brand-anchored, colourblind-validated categorical palette (--chart-1..5,
// defined in styles.css) assigned in fixed order, and Indian-format money
// tick/label formatters. Follows the dataviz method — colour by entity, never
// cycled; one axis; recessive grid.

export const CHART_VARS = [
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
] as const;

type SeriesDef = { key: string; label: string; color?: string };

/**
 * Build a Recharts ChartConfig from an ordered list of series. Colours default
 * to the categorical palette in the order given (entity-stable), so a series is
 * never repainted when a sibling is filtered out.
 */
export function buildChartConfig(series: SeriesDef[]): ChartConfig {
  const config: ChartConfig = {};
  series.forEach((s, i) => {
    config[s.key] = { label: s.label, color: s.color ?? CHART_VARS[i % CHART_VARS.length] };
  });
  return config;
}

/** ₹ compact, Indian grouping — for axis ticks and dense labels (e.g. "₹2.4Cr"). */
export function inrCompact(minor: number | null | undefined): string {
  const value = (minor ?? 0) / 100;
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      notation: "compact",
      maximumFractionDigits: 1,
    }).format(value);
  } catch {
    return `₹${Math.round(value).toLocaleString("en-IN")}`;
  }
}

/** Compact count, e.g. 12500 → "12.5K". */
export function countCompact(n: number | null | undefined): string {
  return new Intl.NumberFormat("en-IN", { notation: "compact", maximumFractionDigits: 1 }).format(
    n ?? 0,
  );
}
