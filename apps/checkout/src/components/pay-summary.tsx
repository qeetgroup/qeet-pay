"use client";

import { useTranslation } from "react-i18next";

import { formatMoney } from "@/lib/money";

type PaySummaryProps = {
  /** What the payment is for (the link's description). */
  title: string;
  /** Integer minor units (paise), or null for a payer-entered open amount. */
  amountMinor: number | null;
  currency: string;
  expiresAt?: string | null;
};

/**
 * The headline summary block at the top of the pay form: the payment
 * description and the amount, rendered big in the display face. When the amount
 * is open (payer-entered) it shows a prompt instead of a figure — the form's
 * amount input below then drives it.
 */
export function PaySummary({ title, amountMinor, currency, expiresAt }: PaySummaryProps) {
  const { t } = useTranslation("checkout");
  return (
    <div className="border-border/60 bg-muted/40 space-y-2 rounded-xl border p-5 text-center">
      <p className="text-muted-foreground text-xs font-medium uppercase tracking-[0.12em]">{title}</p>
      <p className="pay-title pay-tabular text-[2.35rem] leading-none font-semibold tracking-tight">
        {amountMinor !== null ? formatMoney(amountMinor, currency) : t("summary.enterAmount")}
      </p>
      {expiresAt ? (
        <p className="text-muted-foreground inline-flex items-center gap-1.5 text-xs">
          <ClockIcon />
          {t("summary.expiresAt", { date: formatDate(expiresAt) })}
        </p>
      ) : null}
    </div>
  );
}

function ClockIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="size-3.5" aria-hidden>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
  );
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  try {
    return new Intl.DateTimeFormat("en-IN", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(d);
  } catch {
    return d.toLocaleString();
  }
}
