"use client";

import { cn } from "@qeetrix/ui";
import { QeetLogoOnDark } from "@qeetrix/ui/brand";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

import { BRAND } from "@/lib/branding";

type CheckoutShellProps = {
  /** The merchant collecting this payment; surfaced on the brand panel. */
  merchantName?: string | null;
  children: ReactNode;
  className?: string;
};

/**
 * The premium split-screen frame for every hosted-checkout screen: a branded
 * Qeet Pay gradient panel on the left (≥lg) and a centered content column on
 * the right. Collapses to a single centered column on small screens, where the
 * <CheckoutCard> carries the brand.
 */
export function CheckoutShell({ merchantName, children, className }: CheckoutShellProps) {
  const { t } = useTranslation("common");
  return (
    <div className={cn("relative grid min-h-dvh lg:grid-cols-[1.05fr_1fr]", className)}>
      <BrandPanel merchantName={merchantName} />
      <div className="flex min-h-dvh flex-col items-center justify-center px-6 py-10 sm:px-10">
        <div className="pay-rise flex w-full flex-1 flex-col items-center justify-center">
          {children}
        </div>
        {/* Mobile trust footer — the brand panel is hidden below lg. */}
        <p className="pay-tabular mt-8 text-center text-xs text-muted-foreground lg:hidden">
          {t("brand.trust")}
        </p>
      </div>
    </div>
  );
}

function BrandPanel({ merchantName }: { merchantName?: string | null }) {
  const { t } = useTranslation("common");
  const features = [
    t("brand.features.upi"),
    t("brand.features.instant"),
    t("brand.features.gst"),
    t("brand.features.secure"),
  ];
  return (
    <div className="pay-brand-panel relative hidden overflow-hidden lg:flex lg:flex-col lg:justify-between lg:p-12">
      <span className="pay-blob" aria-hidden />

      <div className="relative z-10 flex items-center gap-3">
        <QeetLogoOnDark size={34} title={null} />
        <span className="pay-title text-lg font-semibold tracking-tight">{BRAND.name}</span>
      </div>

      <div className="relative z-10 space-y-6">
        {merchantName ? (
          <div className="inline-flex flex-col gap-1 rounded-xl border border-white/15 bg-white/10 px-4 py-3 backdrop-blur-sm">
            <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-white/60">
              {t("brand.payingTo")}
            </span>
            <span className="text-lg font-semibold">{merchantName}</span>
          </div>
        ) : null}
        <h2 className="pay-title max-w-md text-4xl font-semibold leading-[1.1]">
          {t("brand.headline")}
        </h2>
        <p className="max-w-md text-base leading-relaxed text-white/80">{t("brand.subhead")}</p>
        <ul className="grid max-w-md grid-cols-2 gap-x-5 gap-y-3 pt-1 text-sm text-white/85">
          {features.map((f) => (
            <li key={f} className="flex items-center gap-2">
              <CheckDot />
              {f}
            </li>
          ))}
        </ul>
      </div>

      <div className="relative z-10 flex items-center gap-2 text-xs text-white/60">
        <LockIcon />
        <span>{t("brand.trust")}</span>
      </div>
    </div>
  );
}

function CheckDot() {
  return (
    <span className="grid size-4 shrink-0 place-items-center rounded-full bg-white/20" aria-hidden>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" className="size-2.5">
        <path d="M20 6 9 17l-5-5" />
      </svg>
    </span>
  );
}

function LockIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="size-3.5" aria-hidden>
      <rect x="4" y="11" width="16" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  );
}
