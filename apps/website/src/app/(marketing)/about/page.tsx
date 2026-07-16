import { ArrowRightIcon } from "lucide-react";
import type { Metadata } from "next";

import { ArrowCta } from "@/components/marketing/blocks/arrow-cta";
import { Eyebrow } from "@/components/marketing/blocks/eyebrow";
import { ButtonLink } from "@/components/marketing/button-link";
import { NumberTicker } from "@/components/marketing/effects/number-ticker";
import { Reveal, Stagger, StaggerItem } from "@/components/marketing/motion";
import { PageHero } from "@/components/marketing/page-hero";
import { Section, SectionHeader } from "@/components/marketing/section";
import { BreadcrumbJsonLd } from "@/components/marketing/structured-data";
import { SIGN_UP_URL } from "@/lib/links";

export const metadata: Metadata = {
  title: "About",
  description:
    "Why Qeet Pay exists: Indian businesses stitch Razorpay + Chargebee + ClearTax across four dashboards and four reconciliations. Qeet Pay unifies payments, billing and GST e-invoicing into one API.",
  alternates: { canonical: "/about" },
};

type Stat = { value: number; decimals?: number; prefix?: string; suffix?: string; label: string };

const marketStats: Stat[] = [
  { value: 409, prefix: "$", suffix: "B", label: "India payments market in 2025" },
  { value: 21.63, decimals: 2, suffix: "B", label: "UPI transactions in Dec 2025" },
  { value: 51.2, decimals: 1, prefix: "$", suffix: "B", label: "India fintech market in 2025" },
  { value: 2, prefix: "₹", suffix: " Cr", label: "E-invoicing threshold from Oct 2025" },
];

const phases = [
  {
    name: "Phase 1 — now",
    model: "Sub-merchant under a licensed PA",
    body: "Payment collection, subscription billing, GST invoicing and payouts — everything except direct acquiring. The standard path early Razorpay and Cashfree took too.",
  },
  {
    name: "Phase 2",
    model: "Own RBI PA license",
    body: "Direct acquiring, own nodal account, T+1 settlement, and PA-CB for international — plus ML routing, IRN e-invoicing at scale and AA-powered embedded lending.",
  },
  {
    name: "Phase 3",
    model: "PA-CB + NBFC co-lending + PPI",
    body: "Cross-border payments, our own embedded lending, a stored-value wallet, virtual card issuing, and ESG/carbon reporting.",
  },
];

export default function AboutPage() {
  return (
    <>
      <BreadcrumbJsonLd
        items={[
          { name: "Home", url: "/" },
          { name: "About", url: "/about" },
        ]}
      />

      <PageHero
        eyebrow="About"
        title="Why Qeet Pay"
        titleAccent="exists"
        subtitle="Every business that collects money in India ends up running four vendors, four dashboards and four reconciliations. We think that's one too many — actually, three."
      />

      {/* The why */}
      <Section>
        <div className="mx-auto max-w-3xl">
          <Reveal className="flex flex-col gap-6 text-lg leading-relaxed text-muted-foreground">
            <p>
              Every Qeet product needs to collect money, disburse money, and generate GST invoices.
              Without Qeet Pay, each one independently wires up Razorpay for payments, Chargebee for
              billing, and ClearTax for GST — tripling vendor overhead, splitting financial data
              across systems, and rebuilding reconciliation for every product.
            </p>
            <p>
              Indian SaaS companies, marketplaces and enterprises are doing exactly the same thing.
              No platform today combines UPI + NACH + cards + subscription billing + GST e-invoicing
              with IRN + GSTR-1 auto-filing + AI dunning + marketplace GST split + WhatsApp-native
              invoicing + embedded lending via Account Aggregator — in a single API.
            </p>
            <p className="border-l-2 border-brand pl-5 text-foreground">
              Qeet Pay is that platform: one API, one dashboard, one reconciliation — India-first,
              on a shared double-entry ledger.
            </p>
          </Reveal>
        </div>
      </Section>

      {/* Market */}
      <Section muted aria-label="Market">
        <SectionHeader
          eyebrow="The opportunity"
          title="India's payments market is"
          titleAccent="the largest and fastest-growing"
          subtitle="UPI is the world's biggest real-time payment system, and the October 2025 e-invoicing threshold pulls millions of SMBs into IRN — right as Qeet Pay ships it."
        />
        <Stagger staggerDelay={0.1} className="mt-14 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {marketStats.map((s) => (
            <StaggerItem key={s.label} className="h-full">
              <div className="flex h-full flex-col rounded-2xl border border-border/60 bg-background p-6 text-center">
                <span className="font-display text-4xl font-semibold tracking-tight text-gradient-brand sm:text-5xl">
                  <NumberTicker
                    value={s.value}
                    decimals={s.decimals ?? 0}
                    prefix={s.prefix}
                    suffix={s.suffix}
                  />
                </span>
                <p className="mt-3 text-sm text-muted-foreground text-balance">{s.label}</p>
              </div>
            </StaggerItem>
          ))}
        </Stagger>
      </Section>

      {/* Build path */}
      <Section aria-label="Regulatory build path">
        <SectionHeader
          eyebrow="The path"
          title="A regulated build,"
          titleAccent="the proven way"
          subtitle="Most Indian fintechs launched as sub-merchants before earning their own PA license. Qeet Pay follows the same disciplined path."
        />
        <Stagger staggerDelay={0.1} className="mt-14 grid gap-6 lg:grid-cols-3">
          {phases.map((p, i) => (
            <StaggerItem key={p.name} className="h-full">
              <div className="flex h-full flex-col rounded-2xl border border-border/60 bg-card p-6">
                <span className="font-display text-3xl font-semibold text-gradient-brand">
                  0{i + 1}
                </span>
                <h3 className="mt-3 font-display text-lg font-semibold tracking-tight">{p.name}</h3>
                <p className="mt-1 text-sm font-medium text-brand-text">{p.model}</p>
                <p className="mt-3 text-sm text-muted-foreground">{p.body}</p>
              </div>
            </StaggerItem>
          ))}
        </Stagger>
      </Section>

      {/* CTA */}
      <Section muted>
        <Reveal>
          <div className="relative overflow-hidden rounded-3xl bg-(image:--brand-gradient) p-px shadow-2xl shadow-brand/20">
            <div className="relative flex flex-col items-center gap-6 rounded-[calc(1.5rem-1px)] bg-background/95 px-6 py-16 text-center backdrop-blur sm:px-12">
              <Eyebrow>Design partners</Eyebrow>
              <h2 className="max-w-2xl font-display text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
                Help shape Qeet Pay
              </h2>
              <p className="max-w-xl text-muted-foreground text-balance">
                If GST pain, UPI AutoPay churn, multi-vendor fragmentation or settlement complexity
                sound familiar, become a design partner — direct founder access and locked pricing.
              </p>
              <div className="flex flex-col items-center gap-3 sm:flex-row">
                <ArrowCta href="/contact">Become a design partner</ArrowCta>
                <ButtonLink variant="outline" size="lg" href={SIGN_UP_URL} className="h-11 px-5">
                  Start free <ArrowRightIcon className="size-4" />
                </ButtonLink>
              </div>
            </div>
          </div>
        </Reveal>
      </Section>
    </>
  );
}
