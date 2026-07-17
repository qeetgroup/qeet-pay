import { ArrowRightIcon } from "lucide-react";
import type { Metadata } from "next";

import { ArrowCta } from "@/components/marketing/blocks/arrow-cta";
import { Eyebrow } from "@/components/marketing/blocks/eyebrow";
import { ButtonLink } from "@/components/marketing/button-link";
import { processingFees, tiers } from "@/components/marketing/data/pricing";
import { FaqAccordion } from "@/components/marketing/faq-accordion";
import { PageHero } from "@/components/marketing/page-hero";
import { PricingCalculator } from "@/components/marketing/pricing-calculator";
import { PricingTiers } from "@/components/marketing/pricing-tiers";
import { Reveal, Stagger, StaggerItem } from "@/components/marketing/motion";
import { Section, SectionHeader } from "@/components/marketing/section";
import {
  BreadcrumbJsonLd,
  FaqJsonLd,
  ProductJsonLd,
} from "@/components/marketing/structured-data";
import { SIGN_UP_URL } from "@/lib/links";

export const metadata: Metadata = {
  title: "Pricing",
  description:
    "Start free up to ₹10 lakh TPV/month, then a flat platform fee per tier. Transparent pass-through processing: UPI 0.15% with zero MDR, cards ~1.75%, NACH ₹8, net banking ₹5. No per-invoice fee on IRN e-invoicing.",
  alternates: { canonical: "/pricing" },
};

const pricingFaqs = [
  {
    q: "What counts toward my TPV?",
    a: "TPV is your total processing volume — the gross value of payments you collect in a month. Payouts and refunds don't add to it. Your tier is chosen by the TPV band you expect; you can move up or down any time.",
  },
  {
    q: "Is UPI really zero MDR?",
    a: "Yes. There's a 0% merchant discount rate on UPI per the government mandate. Qeet Pay charges a flat 0.15% platform fee on UPI volume — nothing more.",
  },
  {
    q: "Do e-invoicing and GSTR-1 filing cost extra?",
    a: "No. IRN e-invoicing carries zero per-invoice fee and is included from the Growth tier up, along with GSTR-1 auto-filing and multi-GSTIN support.",
  },
  {
    q: "What happens when I exceed my tier's TPV?",
    a: "You won't be cut off — we'll flag it and help you move to the right tier. Above ₹100 crore TPV, volume discounts apply; talk to sales for an Enterprise contract.",
  },
];

export default function PricingPage() {
  return (
    <>
      <ProductJsonLd />
      <FaqJsonLd items={pricingFaqs} />
      <BreadcrumbJsonLd
        items={[
          { name: "Home", url: "/" },
          { name: "Pricing", url: "/pricing" },
        ]}
      />

      <PageHero
        eyebrow="Pricing"
        title="Simple, transparent pricing —"
        titleAccent="zero MDR on UPI"
        subtitle="A generous free tier, a flat platform fee per plan, and pass-through processing with no surprises. No per-invoice fee on IRN e-invoicing."
        cta={
          <>
            <ArrowCta href={SIGN_UP_URL}>Start free</ArrowCta>
            <ButtonLink variant="outline" size="lg" href="/contact" className="h-11 px-5">
              Talk to sales
            </ButtonLink>
          </>
        }
      />

      {/* Tier cards */}
      <Section>
        <PricingTiers tiers={tiers} />
        <p className="mt-8 text-center text-sm text-muted-foreground">
          All plans include the full API, sandbox and test keys. Prices in INR, billed monthly;
          Enterprise is an annual contract.
        </p>
      </Section>

      {/* Processing fees */}
      <Section muted id="processing-fees">
        <SectionHeader
          eyebrow="Processing fees"
          title="Transparent,"
          titleAccent="pass-through pricing"
          subtitle="You pay the network's cost plus a small Qeet Pay fee — nothing hidden."
        />
        <Reveal className="mx-auto mt-12 max-w-3xl overflow-hidden rounded-2xl border border-border/60 bg-background">
          <table className="w-full text-sm">
            <caption className="sr-only">Qeet Pay processing fees by payment method</caption>
            <thead>
              <tr className="border-b border-border/60 bg-muted/40">
                <th scope="col" className="px-5 py-3 text-left font-medium">
                  Method
                </th>
                <th scope="col" className="px-5 py-3 text-left font-medium">
                  Fee
                </th>
                <th scope="col" className="px-5 py-3 text-left font-medium">
                  Details
                </th>
              </tr>
            </thead>
            <tbody>
              {processingFees.map((f) => (
                <tr key={f.method} className="border-t border-border/60">
                  <th scope="row" className="px-5 py-4 text-left font-medium">
                    {f.method}
                  </th>
                  <td className="px-5 py-4">
                    <span className="font-display text-lg font-semibold text-gradient-brand">
                      {f.fee}
                    </span>
                  </td>
                  <td className="px-5 py-4 text-muted-foreground">{f.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Reveal>
        <Stagger
          staggerDelay={0.08}
          className="mx-auto mt-6 grid max-w-3xl gap-3 sm:grid-cols-2"
        >
          {[
            "Zero MDR on UPI per the government mandate",
            "No per-invoice fee on IRN / e-invoicing (Growth+)",
          ].map((note) => (
            <StaggerItem key={note}>
              <div className="rounded-xl border border-border/60 bg-background px-4 py-3 text-sm text-muted-foreground">
                {note}
              </div>
            </StaggerItem>
          ))}
        </Stagger>
      </Section>

      {/* Interactive estimator */}
      <PricingCalculator />

      {/* Pricing FAQ */}
      <Section aria-label="Pricing questions">
        <SectionHeader eyebrow="FAQ" title="Pricing," titleAccent="explained" />
        <div className="mx-auto mt-4 max-w-3xl">
          <FaqAccordion items={pricingFaqs} />
        </div>
      </Section>

      {/* CTA */}
      <Section muted>
        <Reveal>
          <div className="relative overflow-hidden rounded-3xl bg-(image:--brand-gradient) p-px shadow-2xl shadow-brand/20">
            <div className="relative flex flex-col items-center gap-6 rounded-[calc(1.5rem-1px)] bg-background/95 px-6 py-16 text-center backdrop-blur sm:px-12">
              <Eyebrow>Free to start</Eyebrow>
              <h2 className="max-w-2xl font-display text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
                Go live free up to ₹10 lakh TPV a month
              </h2>
              <p className="max-w-xl text-muted-foreground text-balance">
                No credit card, no platform fee, full API access. Upgrade when your volume grows.
              </p>
              <div className="flex flex-col items-center gap-3 sm:flex-row">
                <ArrowCta href={SIGN_UP_URL}>Start free</ArrowCta>
                <ButtonLink variant="outline" size="lg" href="/compare" className="h-11 px-5">
                  Compare vs Razorpay <ArrowRightIcon className="size-4" />
                </ButtonLink>
              </div>
            </div>
          </div>
        </Reveal>
      </Section>
    </>
  );
}
