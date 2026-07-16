/**
 * Money helpers for the Qeet Pay console. All amounts from the API are integer minor units (paise);
 * the ledger and every domain module use `amount_minor`. Never do float math on money — format only.
 */

/** Formats integer minor units as an INR amount, e.g. 236000 → "₹2,360.00". */
export function formatInr(minor: number | null | undefined, currency = "INR"): string {
  const value = (minor ?? 0) / 100;
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    // Unknown currency code → fall back to a plain grouped number with the code.
    return `${currency} ${value.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
  }
}

/** Formats basis points as a percentage, e.g. 1500 → "15%". */
export function formatBps(bps: number | null | undefined): string {
  return `${((bps ?? 0) / 100).toLocaleString("en-IN", { maximumFractionDigits: 2 })}%`;
}

/** Parses a rupee string (e.g. "2360.50") into integer paise. Returns null when not parseable. */
export function rupeesToMinor(rupees: string): number | null {
  const n = Number(rupees);
  if (!Number.isFinite(n) || n < 0) return null;
  return Math.round(n * 100);
}
