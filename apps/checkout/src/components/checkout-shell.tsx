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
  return (
    <div className={cn("relative grid min-h-dvh lg:grid-cols-[1.05fr_1fr]", className)}>
      <BrandPanel merchantName={merchantName} />
      <div className="flex min-h-dvh items-center justify-center px-6 py-10 sm:px-10">
        <div className="pay-rise flex w-full justify-center">{children}</div>
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

      <div className="relative z-10 space-y-5">
        {merchantName ? (
          <p className="text-sm font-medium tracking-wide text-white/70 uppercase">
            {t("brand.payingTo")} · {merchantName}
          </p>
        ) : null}
        <h2 className="pay-title max-w-md text-4xl font-semibold leading-[1.1]">
          {t("brand.headline")}
        </h2>
        <p className="max-w-md text-base leading-relaxed text-white/80">{t("brand.subhead")}</p>
        <ul className="flex flex-wrap gap-x-5 gap-y-2 pt-1 text-sm text-white/75">
          {features.map((f) => (
            <li key={f} className="flex items-center gap-1.5">
              <span className="size-1.5 rounded-full bg-white/60" aria-hidden />
              {f}
            </li>
          ))}
        </ul>
      </div>

      <p className="relative z-10 text-xs text-white/55">{t("brand.footer")}</p>
    </div>
  );
}
