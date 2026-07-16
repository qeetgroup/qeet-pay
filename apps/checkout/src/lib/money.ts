/**
 * Money helpers for the Qeet Pay hosted checkout. Every amount on the wire is an
 * integer in minor units (paise) — the backend ledger and the checkout API both
 * speak `amountMinor`. Never do float math on money; format only, and convert a
 * user-typed rupee string to paise exactly once, at the boundary.
 */

/** Formats integer minor units as a localized INR amount, e.g. 236000 → "₹2,360.00". */
export function formatMoney(minor: number | null | undefined, currency = "INR"): string {
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

/** Parses a rupee string (e.g. "2360.50") into integer paise. Returns null when not parseable. */
export function toMinor(rupees: string): number | null {
  const n = Number(rupees);
  if (!Number.isFinite(n) || n < 0) return null;
  return Math.round(n * 100);
}
