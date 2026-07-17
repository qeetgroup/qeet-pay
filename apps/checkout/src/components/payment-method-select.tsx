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
    <div className="space-y-1.5">
      <span className="text-sm font-medium">{t("method.label")}</span>
      <div role="radiogroup" aria-label={t("method.label")} className="grid grid-cols-2 gap-2">
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
                "flex items-center gap-2.5 rounded-lg border px-3 py-2.5 text-sm font-medium transition",
                "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none",
                "disabled:cursor-not-allowed disabled:opacity-60",
                active
                  ? "border-primary ring-primary/30 bg-primary/5 ring-2"
                  : "border-border/60 hover:bg-muted/50",
              )}
            >
              <Icon className={cn("size-5 shrink-0", active ? "text-primary" : "text-muted-foreground")} />
              {t(`method.${method}`)}
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
