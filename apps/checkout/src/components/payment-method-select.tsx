"use client";

import { cn } from "@qeetrix/ui";
import type { ReactElement, SVGProps } from "react";
import { useTranslation } from "react-i18next";

// The four rails the checkout API accepts on POST /v1/checkout/{code}/pay.
export const PAYMENT_METHODS = ["UPI", "CARD", "NET_BANKING", "WALLET"] as const;
export type PaymentMethod = (typeof PAYMENT_METHODS)[number];

type PaymentMethodSelectProps = {
  value: PaymentMethod;
  onChange: (method: PaymentMethod) => void;
  disabled?: boolean;
};

/**
 * A segmented radio-card grid for choosing the payment rail. Mirrors the hosted
 * login's "grid of selectable buttons" pattern (see its social-provider list),
 * but as a single-select radiogroup: exactly one method is active, and the
 * active card picks up the brand ring/tint from the @qeetrix/ui tokens.
 */
export function PaymentMethodSelect({ value, onChange, disabled }: PaymentMethodSelectProps) {
  const { t } = useTranslation("checkout");
  return (
    <div className="space-y-2">
      <span className="text-sm font-medium">{t("method.label")}</span>
      <div role="radiogroup" aria-label={t("method.label")} className="grid grid-cols-2 gap-2.5">
        {PAYMENT_METHODS.map((method) => {
          const active = method === value;
          const Icon = METHOD_ICONS[method];
          return (
            <button
              key={method}
              type="button"
              role="radio"
              aria-checked={active}
              disabled={disabled}
              onClick={() => onChange(method)}
              className={cn(
                "group relative flex items-center gap-2.5 rounded-xl border px-3.5 py-3 text-sm font-medium transition-all duration-200",
                "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none",
                "disabled:cursor-not-allowed disabled:opacity-60",
                active
                  ? "border-primary bg-primary/5 ring-2 ring-primary/25 shadow-sm"
                  : "border-border/70 hover:border-border hover:bg-muted/50",
              )}
            >
              <span
                className={cn(
                  "grid size-8 shrink-0 place-items-center rounded-lg transition-colors",
                  active ? "bg-primary/10 text-primary" : "bg-muted text-muted-foreground",
                )}
              >
                <Icon className="size-[1.05rem]" />
              </span>
              {t(`method.${method}`)}
              {active && (
                <span className="absolute inset-e-2.5 top-2.5 grid size-4 place-items-center rounded-full bg-primary text-primary-foreground">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" className="size-2.5" aria-hidden>
                    <path d="M20 6 9 17l-5-5" />
                  </svg>
                </span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}

// Minimal inline stroke icons (currentColor), kept self-contained so the
// checkout carries no extra icon dependency — the same rationale as the hosted
// login keeping its provider marks local.
const METHOD_ICONS: Record<PaymentMethod, (props: SVGProps<SVGSVGElement>) => ReactElement> = {
  UPI: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden {...props}>
      <rect x="5" y="2" width="14" height="20" rx="2.5" />
      <path d="M9 18h6" />
    </svg>
  ),
  CARD: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden {...props}>
      <rect x="2" y="5" width="20" height="14" rx="2.5" />
      <path d="M2 10h20" />
    </svg>
  ),
  NET_BANKING: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden {...props}>
      <path d="M3 10l9-6 9 6" />
      <path d="M4 10v9M20 10v9M9 10v9M15 10v9" />
      <path d="M2 21h20" />
    </svg>
  ),
  WALLET: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden {...props}>
      <path d="M3 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      <path d="M16 12h3" />
    </svg>
  ),
};
