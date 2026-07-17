import { cn } from "@qeetrix/ui";
import { ArrowRightIcon, CheckIcon, MinusIcon, XIcon } from "lucide-react";
import type { Metadata } from "next";

import { ArrowCta } from "@/components/marketing/blocks/arrow-cta";
import { Eyebrow } from "@/components/marketing/blocks/eyebrow";
import { LogoLockup } from "@/components/marketing/blocks/logo-wall";
import { ButtonLink } from "@/components/marketing/button-link";
import { features } from "@/components/marketing/data/features";
import { homeTiers } from "@/components/marketing/data/pricing";
import { CodeBlock, Tok } from "@/components/marketing/effects/code-block";
import { Marquee } from "@/components/marketing/effects/marquee";
import { NumberTicker } from "@/components/marketing/effects/number-ticker";
import { SpotlightCard } from "@/components/marketing/effects/spotlight-card";
import { FaqAccordion } from "@/components/marketing/faq-accordion";
import { Reveal, Stagger, StaggerItem } from "@/components/marketing/motion";
import { PageHero } from "@/components/marketing/page-hero";
import { PricingTiers } from "@/components/marketing/pricing-tiers";
import { Section, SectionHeader } from "@/components/marketing/section";
import { StickyCtaBar } from "@/components/marketing/sticky-cta-bar";
import { FaqJsonLd, ProductJsonLd } from "@/components/marketing/structured-data";
import { DOCS_URL, SIGN_UP_URL } from "@/lib/links";

export const metadata: Metadata = {
  title: { absolute: "Qeet Pay — One API for payments, billing & GST" },
  description:
    "Qeet Pay is India's unified payments platform: UPI, cards, NACH & net-banking acceptance, payouts, subscription billing, and GST-compliant IRN e-invoicing — one API, one dashboard, one reconciliation.",
  alternates: { canonical: "/" },
};

const rails = [
  "UPI",
  "RuPay",
  "Visa",
  "Mastercard",
  "Amex",
  "e-NACH",
  "IMPS",
  "NEFT",
  "RTGS",
  "PhonePe",
  "Paytm",
  "Virtual Accounts",
];

type Stat = { value: number; decimals?: number; prefix?: string; suffix?: string; label: string };

const stats: Stat[] = [
  { value: 409, prefix: "$", suffix: "B", label: "India payments market in 2025 — heading to $958B by 2030" },
  { value: 21.63, decimals: 2, suffix: "B", label: "UPI transactions in December 2025 alone — the world's largest real-time rail" },
  { value: 1.27, decimals: 2, suffix: "B", label: "UPI AutoPay mandates live (Nov 2025) — 10x growth since Jan 2024" },
  { value: 100, prefix: "<", suffix: "ms", label: "Fraud scoring latency across 100+ signals per transaction" },
];

type Mark = true | false | "partial";

const compareColumns = ["Razorpay", "Chargebee", "Stripe"] as const;

const compareRows: { feature: string; cells: [Mark, Mark, Mark]; qeetpay: Mark }[] = [
  { feature: "UPI + NACH acceptance", cells: [true, false, false], qeetpay: true },
  { feature: "Subscription billing", cells: ["partial", true, true], qeetpay: true },
  { feature: "GST e-invoicing (IRN)", cells: [false, false, false], qeetpay: true },
  { feature: "GSTR-1 auto-filing", cells: [false, false, false], qeetpay: true },
  { feature: "Multi-GSTIN billing", cells: [false, false, false], qeetpay: true },
  { feature: "AI dunning (UPI-aware)", cells: [false, "partial", false], qeetpay: true },
  { feature: "WhatsApp-native invoicing", cells: [false, false, false], qeetpay: true },
  { feature: "Payment orchestration", cells: [false, false, false], qeetpay: true },
  { feature: "Marketplace GST split", cells: [false, false, false], qeetpay: true },
  { feature: "Embedded lending (AA)", cells: ["partial", false, false], qeetpay: true },
  { feature: "Combined e-invoice + UPI QR", cells: [false, false, false], qeetpay: true },
];

function CellMark({ value }: { value: Mark }) {
  if (value === true)
    return (
      <span className="mx-auto inline-flex size-6 items-center justify-center rounded-full bg-emerald-500/15 text-emerald-700 dark:text-emerald-400">
        <CheckIcon className="size-3.5" aria-hidden />
        <span className="sr-only">Yes</span>
      </span>
    );
  if (value === "partial")
    return (
      <span className="mx-auto inline-flex size-6 items-center justify-center rounded-full bg-amber-500/15 text-amber-700 dark:text-amber-400">
        <MinusIcon className="size-3.5" aria-hidden />
        <span className="sr-only">Partial</span>
      </span>
    );
  return (
    <span className="mx-auto inline-flex size-6 items-center justify-center rounded-full bg-muted text-muted-foreground">
      <XIcon className="size-3.5" aria-hidden />
      <span className="sr-only">No</span>
    </span>
  );
}

const faqs = [
  {
    q: "Is Qeet Pay a payment gateway, a billing tool, or a GST platform?",
    a: "All three, on one API. Acceptance (UPI, cards, NACH, net banking, wallets), subscription billing with usage metering, and GST-compliant invoicing with IRN e-invoicing share a single ledger, dashboard and reconciliation — so you stop stitching Razorpay + Chargebee + ClearTax together.",
  },
  {
    q: "What does it actually cost?",
    a: "There's a free tier up to ₹10 lakh TPV/month, then flat platform fees per tier. Processing is transparent pass-through: UPI 0.15% (with 0% MDR per the government mandate), cards ~1.75%, NACH ₹8/debit and net banking ₹5/transaction. IRN e-invoicing carries no per-invoice fee from Growth up.",
  },
  {
    q: "Do you really handle GST e-invoicing and GSTR-1 filing?",
    a: "Yes. Invoices auto-calculate CGST/SGST/IGST with place-of-supply rules, generate an IRN via the IRP with a signed QR, and file GSTR-1 through the GSTN API. Our combined QR carries both the IRP-validated e-invoice data and a UPI payment intent, so a buyer scans once to verify and pay.",
  },
  {
    q: "Which providers sit behind the orchestration layer?",
    a: "Razorpay, Cashfree, PayU, PhonePe and Stripe India today. Qeet Pay routes each payment to the best provider by cost, authorization rate and health, and fails over automatically on a 5xx — transparently to your customer.",
  },
  {
    q: "How does Qeet Pay handle multi-tenancy and data residency?",
    a: "Every merchant is isolated at the database layer with Postgres Row-Level Security — cross-tenant access is architecturally impossible. Data resides in AWS ap-south-1 (Mumbai) with DPDP-aligned consent, erasure and breach notification.",
  },
  {
    q: "Can I migrate from Razorpay or Chargebee?",
    a: "Yes — design partners get white-glove migration support, GSTR-1 auto-filing set up end-to-end, and locked pricing for two years. Talk to sales and we'll scope the move.",
  },
];

export default function HomePage() {
  return (
    <>
      <ProductJsonLd />
      <FaqJsonLd items={faqs} />

      <PageHero
        eyebrow="Payments · Billing · GST"
        title="One API for payments, billing"
        titleAccent="& GST — built for India"
        subtitle="Accept UPI, cards, NACH and net banking, pay out instantly, bill subscriptions, and issue GST-compliant IRN e-invoices — from a single integration, one dashboard, one reconciliation."
        cta={
          <>
            <ArrowCta href={SIGN_UP_URL}>Start free</ArrowCta>
            <ButtonLink variant="outline" size="lg" href={DOCS_URL} className="h-11 px-5">
              Read the docs
            </ButtonLink>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">
          Free up to <span className="text-brand-text">₹10 lakh TPV / month</span> · zero MDR on UPI
          · no per-invoice fee on IRN
        </p>
      </PageHero>

      {/* Rails strip */}
      <section aria-label="Supported payment rails" className="border-b border-border/60 bg-muted/20">
        <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
          <p className="text-center text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
            One integration, every Indian rail
          </p>
          <div className="relative mt-8 mask-[linear-gradient(90deg,transparent,black_12%,black_88%,transparent)]">
            <Marquee pauseOnHover duration={45}>
              {rails.map((name) => (
                <LogoLockup key={name} name={name} className="mx-2" />
              ))}
            </Marquee>
          </div>
        </div>
      </section>

      {/* Pillars — feature sections */}
      <Section id="features">
        <SectionHeader
          eyebrow="The platform"
          title="Everything money touches,"
          titleAccent="in one place"
          subtitle="Eight product pillars on a shared double-entry ledger — so payments, billing and compliance never drift out of sync."
        />
        <Stagger staggerDelay={0.06} className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {features.map((f) => {
            const Icon = f.icon;
            return (
              <StaggerItem key={f.id} className="h-full">
                <SpotlightCard className="h-full rounded-2xl">
                  <div className="flex h-full flex-col rounded-2xl border border-border/60 bg-card p-6">
                    <span className="inline-flex size-11 items-center justify-center rounded-xl bg-brand/12 text-brand">
                      <Icon className="size-5.5" aria-hidden />
                    </span>
                    <h3 className="mt-4 font-display text-lg font-semibold tracking-tight">
                      {f.title}
                    </h3>
                    <p className="mt-2 text-sm text-muted-foreground">{f.blurb}</p>
                  </div>
                </SpotlightCard>
              </StaggerItem>
            );
          })}
        </Stagger>
      </Section>

      {/* Developer highlight */}
      <Section muted>
        <div className="grid items-center gap-12 lg:grid-cols-2">
          <Reveal className="flex flex-col items-start">
            <Eyebrow>For developers</Eyebrow>
            <h2 className="mt-5 font-display text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
              Collect a payment and issue its GST e-invoice in one call
            </h2>
            <p className="mt-4 max-w-xl text-muted-foreground sm:text-lg">
              A clean, idempotent REST API with first-party SDKs for Node, Go, Python and Java, a{" "}
              <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-sm">qp</code> CLI, and a
              full sandbox. Money is always integer paise — never floats.
            </p>
            <ul className="mt-6 flex flex-col gap-3 text-sm">
              {[
                "Combined e-invoice + UPI QR generated with the payment",
                "Sandbox adapters by default — no live keys needed to build",
                "Webhooks with a transactional outbox, so events never drop",
              ].map((point) => (
                <li key={point} className="flex items-start gap-2.5">
                  <span className="mt-0.5 inline-flex size-5 shrink-0 items-center justify-center rounded-full bg-brand/15 text-brand">
                    <CheckIcon className="size-3" aria-hidden />
                  </span>
                  <span className="text-muted-foreground">{point}</span>
                </li>
              ))}
            </ul>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <ButtonLink href={DOCS_URL} className="h-11 px-5">
                Explore the API <ArrowRightIcon className="size-4" />
              </ButtonLink>
              <ButtonLink variant="outline" href="/product" className="h-11 px-5">
                See all features
              </ButtonLink>
            </div>
          </Reveal>

          <Reveal delay={0.1}>
            <CodeBlock filename="checkout.ts" className="min-h-88">
              <div>
                <Tok.k>import</Tok.k> {"{ "}
                <Tok.v>QeetPay</Tok.v>
                {" }"} <Tok.k>from</Tok.k> <Tok.s>&quot;@qeet-pay/node&quot;</Tok.s>
                <Tok.punct>;</Tok.punct>
              </div>
              <div className="mt-3">
                <Tok.k>const</Tok.k> <Tok.v>qp</Tok.v> <Tok.punct>=</Tok.punct> <Tok.k>new</Tok.k>{" "}
                <Tok.f>QeetPay</Tok.f>
                <Tok.punct>(</Tok.punct>
                <Tok.v>process</Tok.v>
                <Tok.punct>.</Tok.punct>
                <Tok.v>env</Tok.v>
                <Tok.punct>.</Tok.punct>
                <Tok.v>QEETPAY_KEY</Tok.v>
                <Tok.punct>);</Tok.punct>
              </div>
              <div className="mt-4">
                <Tok.c>// One call: collect over UPI + issue a GST e-invoice</Tok.c>
              </div>
              <div>
                <Tok.k>const</Tok.k> <Tok.v>link</Tok.v> <Tok.punct>=</Tok.punct> <Tok.k>await</Tok.k>{" "}
                <Tok.v>qp</Tok.v>
                <Tok.punct>.</Tok.punct>
                <Tok.v>paymentLinks</Tok.v>
                <Tok.punct>.</Tok.punct>
                <Tok.f>create</Tok.f>
                <Tok.punct>({"{"}</Tok.punct>
              </div>
              <div className="pl-4">
                <Tok.p>amountMinor</Tok.p>
                <Tok.punct>:</Tok.punct> <Tok.n>49900</Tok.n>
                <Tok.punct>,</Tok.punct> <Tok.c>{"// ₹499.00 — always paise"}</Tok.c>
              </div>
              <div className="pl-4">
                <Tok.p>currency</Tok.p>
                <Tok.punct>:</Tok.punct> <Tok.s>&quot;INR&quot;</Tok.s>
                <Tok.punct>,</Tok.punct>
              </div>
              <div className="pl-4">
                <Tok.p>methods</Tok.p>
                <Tok.punct>: [</Tok.punct>
                <Tok.s>&quot;upi&quot;</Tok.s>
                <Tok.punct>,</Tok.punct> <Tok.s>&quot;card&quot;</Tok.s>
                <Tok.punct>,</Tok.punct> <Tok.s>&quot;netbanking&quot;</Tok.s>
                <Tok.punct>],</Tok.punct>
              </div>
              <div className="pl-4">
                <Tok.p>gst</Tok.p>
                <Tok.punct>: {"{ "}</Tok.punct>
                <Tok.p>eInvoice</Tok.p>
                <Tok.punct>:</Tok.punct> <Tok.n>true</Tok.n>
                <Tok.punct>,</Tok.punct> <Tok.p>combinedQr</Tok.p>
                <Tok.punct>:</Tok.punct> <Tok.n>true</Tok.n>
                <Tok.punct>{" }"},</Tok.punct>
              </div>
              <div>
                <Tok.punct>{"});"}</Tok.punct>
              </div>
            </CodeBlock>
          </Reveal>
        </div>
      </Section>

      {/* Comparison */}
      <Section>
        <SectionHeader
          eyebrow="Why teams switch"
          title="No single competitor"
          titleAccent="covers all of this"
          subtitle="UPI + NACH, subscription billing, GST e-invoicing, AI dunning and orchestration — one platform instead of four vendors and four reconciliations."
        />
        <Reveal className="mx-auto mt-12 max-w-4xl overflow-x-auto rounded-2xl border border-border/60 bg-background">
          <table className="w-full min-w-2xl text-sm">
            <caption className="sr-only">
              Qeet Pay compared with Razorpay, Chargebee and Stripe
            </caption>
            <thead>
              <tr className="border-b border-border/60 bg-muted/40">
                <th scope="col" className="px-4 py-3 text-left font-medium">
                  Capability
                </th>
                {compareColumns.map((c) => (
                  <th key={c} scope="col" className="px-3 py-3 text-center font-medium">
                    {c}
                  </th>
                ))}
                <th scope="col" className="px-3 py-3 text-center">
                  <span className="text-gradient-brand font-semibold">Qeet Pay</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {compareRows.map((row) => (
                <tr key={row.feature} className="border-t border-border/60">
                  <th scope="row" className="px-4 py-3 text-left font-medium">
                    {row.feature}
                  </th>
                  {row.cells.map((cell, i) => (
                    <td key={compareColumns[i]} className="px-3 py-3 text-center">
                      <CellMark value={cell} />
                    </td>
                  ))}
                  <td className="bg-brand/5 px-3 py-3 text-center">
                    <CellMark value={row.qeetpay} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Reveal>
        <div className="mt-8 flex justify-center">
          <ButtonLink variant="outline" href="/compare">
            See the full comparison <ArrowRightIcon className="size-4" />
          </ButtonLink>
        </div>
      </Section>

      {/* Stats band */}
      <Section muted aria-label="Market opportunity">
        <SectionHeader
          eyebrow="The market"
          title="Built for the world's"
          titleAccent="fastest-growing payments market"
        />
        <Stagger staggerDelay={0.1} className="mt-14 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {stats.map((s) => (
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

      {/* Pricing teaser */}
      <Section id="pricing">
        <SectionHeader
          eyebrow="Pricing"
          title="Transparent pricing,"
          titleAccent="zero MDR on UPI"
          subtitle="Start free, then a flat platform fee per tier with pass-through processing. No per-invoice fee on IRN e-invoicing."
        />
        <div className="mt-14">
          <PricingTiers tiers={homeTiers} />
        </div>
        <div className="mt-10 flex justify-center">
          <ButtonLink href="/pricing" size="lg" className="h-11 px-5">
            See all plans & processing fees <ArrowRightIcon className="size-4" />
          </ButtonLink>
        </div>
      </Section>

      {/* FAQ */}
      <Section muted aria-label="Frequently asked questions">
        <SectionHeader eyebrow="FAQ" title="Questions," titleAccent="answered" />
        <div className="mx-auto mt-4 max-w-3xl">
          <FaqAccordion items={faqs} />
        </div>
      </Section>

      {/* Final CTA */}
      <Section>
        <Reveal>
          <div className="relative overflow-hidden rounded-3xl bg-(image:--brand-gradient) p-px shadow-2xl shadow-brand/20">
            <div
              className={cn(
                "relative flex flex-col items-center gap-6 rounded-[calc(1.5rem-1px)] bg-background/95 px-6 py-16 text-center backdrop-blur sm:px-12",
              )}
            >
              <Eyebrow>Go live this week</Eyebrow>
              <h2 className="max-w-2xl font-display text-3xl font-semibold tracking-tight text-balance sm:text-4xl lg:text-[2.85rem] lg:leading-[1.05]">
                One integration for everything money touches
              </h2>
              <p className="max-w-xl text-muted-foreground text-balance">
                Free up to ₹10 lakh TPV a month. Sandbox keys in seconds, GST e-invoicing included,
                and white-glove migration when you&apos;re ready to scale.
              </p>
              <div className="flex flex-col items-center gap-3 sm:flex-row">
                <ArrowCta href={SIGN_UP_URL}>Start free</ArrowCta>
                <ButtonLink variant="outline" size="lg" href="/contact" className="h-11 px-5">
                  Talk to sales
                </ButtonLink>
              </div>
            </div>
          </div>
        </Reveal>
      </Section>

      <StickyCtaBar />
    </>
  );
}
