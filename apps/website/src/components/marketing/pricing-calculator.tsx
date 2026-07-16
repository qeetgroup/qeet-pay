"use client";

import { Slider } from "@qeetrix/ui";
import { useMemo, useState } from "react";

import { ButtonLink } from "@/components/marketing/button-link";
import { SIGN_UP_URL } from "@/lib/links";

// Pricing knobs — kept aligned with the static tier cards / README. Hard-coded
// so the marketing site stays deployable without reaching the backend; if
// pricing ever moves server-side, swap to a fetched config.
const L = 100_000; // ₹1 lakh
const CR = 10_000_000; // ₹1 crore

const FREE_CAP = 10 * L; // ₹10 lakh / month
const STARTER_CAP = 1 * CR; // ₹1 crore
const GROWTH_CAP = 10 * CR; // ₹10 crore
const SCALE_CAP = 100 * CR; // ₹100 crore

// Illustrative blended processing rate for a UPI-heavy Indian merchant mix
// (~60% UPI @ 0.15%, ~25% cards @ ~1.75%, ~15% NACH/net-banking).
const BLENDED_RATE = 0.0057;

// Log-scale slider — a linear scale crushes the low end into a sliver.
const MIN_TPV = 1 * L; // ₹1 lakh
const MAX_TPV = 500 * CR; // ₹500 crore
const EXP = Math.log10(MAX_TPV / MIN_TPV);

function sliderToTpv(s: number) {
  return MIN_TPV * 10 ** ((s / 100) * EXP);
}
function tpvToSlider(t: number) {
  if (t <= MIN_TPV) return 0;
  if (t >= MAX_TPV) return 100;
  return (Math.log10(t / MIN_TPV) / EXP) * 100;
}

// Round to two significant figures so the read-out doesn't flicker digits.
function roundFriendly(n: number) {
  if (n < 100) return Math.round(n);
  const order = Math.floor(Math.log10(n));
  const step = 10 ** Math.max(0, order - 1);
  return Math.round(n / step) * step;
}

/** Compact ₹ read-out using Indian lakh/crore units. */
function formatInrCompact(n: number) {
  if (n >= CR) {
    const v = n / CR;
    return `₹${v % 1 === 0 ? v.toLocaleString("en-IN") : v.toFixed(1)} Cr`;
  }
  if (n >= L) {
    const v = n / L;
    return `₹${v % 1 === 0 ? v.toLocaleString("en-IN") : v.toFixed(1)} L`;
  }
  return `₹${Math.round(n).toLocaleString("en-IN")}`;
}

function formatInr(n: number) {
  return `₹${Math.round(n).toLocaleString("en-IN")}`;
}

interface ComputedPlan {
  name: "Free" | "Starter" | "Growth" | "Scale" | "Enterprise";
  blurb: string;
  platformFee: number | null; // null = custom
  cta: { label: string; href: string };
}

function computePlan(tpv: number): ComputedPlan {
  if (tpv <= FREE_CAP)
    return {
      name: "Free",
      blurb: "Your volume fits the free tier — no monthly platform fee.",
      platformFee: 0,
      cta: { label: "Start free", href: SIGN_UP_URL },
    };
  if (tpv <= STARTER_CAP)
    return {
      name: "Starter",
      blurb: "Mandates, bulk payouts, and WhatsApp invoicing.",
      platformFee: 2_999,
      cta: { label: "Start free trial", href: `${SIGN_UP_URL}?plan=starter` },
    };
  if (tpv <= GROWTH_CAP)
    return {
      name: "Growth",
      blurb: "IRN e-invoicing, GSTR-1 auto-filing, AI dunning & orchestration.",
      platformFee: 9_999,
      cta: { label: "Start free trial", href: `${SIGN_UP_URL}?plan=growth` },
    };
  if (tpv <= SCALE_CAP)
    return {
      name: "Scale",
      blurb: "Marketplace splits, virtual accounts, and embedded lending.",
      platformFee: 29_999,
      cta: { label: "Talk to sales", href: "/contact" },
    };
  return {
    name: "Enterprise",
    blurb: "We'll size a contract to your volume and compliance needs.",
    platformFee: null,
    cta: { label: "Talk to sales", href: "/contact" },
  };
}

const PRESETS = [10 * L, 1 * CR, 10 * CR, 100 * CR];

export function PricingCalculator() {
  const [sliderValue, setSliderValue] = useState(() => tpvToSlider(5 * CR));
  const tpv = useMemo(() => roundFriendly(sliderToTpv(sliderValue)), [sliderValue]);
  const plan = computePlan(tpv);
  const processing = tpv * BLENDED_RATE;

  function setTpv(value: number) {
    setSliderValue(tpvToSlider(Math.max(MIN_TPV, Math.min(MAX_TPV, value))));
  }

  return (
    <section className="border-b border-border/60">
      <div className="mx-auto max-w-5xl px-4 py-20 sm:px-6 lg:px-8 lg:py-28">
        <div className="mx-auto max-w-2xl text-center">
          <p className="text-sm font-medium uppercase tracking-widest text-brand-text">
            Estimate your bill
          </p>
          <h2 className="mt-2 font-display text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
            What will Qeet Pay cost you?
          </h2>
          <p className="mt-3 text-muted-foreground">
            Drag to your expected monthly total processing volume (TPV). Zero MDR on UPI, transparent
            pass-through fees, and a flat platform fee per tier — no surprises.
          </p>
        </div>

        <div className="mt-12 grid gap-6 rounded-2xl border border-border/60 bg-background p-6 sm:p-10 lg:grid-cols-[3fr_2fr] lg:gap-10">
          {/* Slider column */}
          <div>
            <span className="text-sm font-medium text-muted-foreground">
              Monthly processing volume
            </span>
            <div className="mt-1 flex items-baseline gap-3">
              <span className="font-display text-4xl font-semibold tracking-tight sm:text-5xl">
                {formatInrCompact(tpv)}
              </span>
              <span className="text-sm text-muted-foreground">TPV / month</span>
            </div>

            <div className="mt-8">
              <Slider
                value={[sliderValue]}
                onValueChange={(values) =>
                  setSliderValue(Array.isArray(values) ? (values[0] ?? 0) : (values as number))
                }
                min={0}
                max={100}
                step={0.5}
                aria-label="Monthly TPV"
              />
              <div className="mt-4 flex justify-between gap-2 text-xs text-muted-foreground">
                {PRESETS.map((p) => (
                  <button
                    key={p}
                    type="button"
                    className="rounded-md px-2 py-1 transition-colors hover:bg-muted hover:text-foreground"
                    onClick={() => setTpv(p)}
                  >
                    {formatInrCompact(p)}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Result column */}
          <div className="rounded-2xl border border-border/60 bg-muted/20 p-6 sm:p-8">
            <div className="flex items-center justify-between">
              <span className="rounded-full bg-brand/15 px-2.5 py-1 text-xs font-medium uppercase tracking-wider text-brand-text">
                {plan.name}
              </span>
              {plan.platformFee !== null && (
                <span className="text-xs text-muted-foreground">INR · billed monthly</span>
              )}
            </div>

            <div className="mt-4 flex items-baseline gap-2">
              {plan.platformFee === null ? (
                <span className="font-display text-4xl font-semibold tracking-tight">Custom</span>
              ) : (
                <>
                  <span className="font-display text-5xl font-semibold tracking-tight">
                    {plan.platformFee === 0 ? "₹0" : formatInr(plan.platformFee)}
                  </span>
                  <span className="text-sm text-muted-foreground">/ mo platform fee</span>
                </>
              )}
            </div>

            <p className="mt-3 text-sm text-muted-foreground">{plan.blurb}</p>

            <p className="mt-4 rounded-lg border border-border/60 bg-background px-3 py-2 text-xs text-muted-foreground">
              + ~{formatInr(processing)} / mo estimated processing{" "}
              <span className="text-foreground">(blended ~0.57%)</span> — zero MDR on the UPI share.
            </p>

            <div className="mt-6">
              <ButtonLink href={plan.cta.href} className="w-full">
                {plan.cta.label}
              </ButtonLink>
            </div>
          </div>
        </div>

        <p className="mt-4 text-center text-xs text-muted-foreground">
          Estimates are illustrative — actual processing cost depends on your payment-method mix.
          Volume discounts apply above ₹100 crore TPV; talk to sales.
        </p>
      </div>
    </section>
  );
}
