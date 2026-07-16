import { ArrowRightIcon, CheckIcon } from "lucide-react";
import type { Metadata } from "next";

import { ArrowCta } from "@/components/marketing/blocks/arrow-cta";
import { Eyebrow } from "@/components/marketing/blocks/eyebrow";
import { BezelCard } from "@/components/marketing/blocks/bezel-card";
import { ButtonLink } from "@/components/marketing/button-link";
import { comparisons } from "@/components/marketing/data/comparisons";
import { Reveal, Stagger, StaggerItem } from "@/components/marketing/motion";
import { PageHero } from "@/components/marketing/page-hero";
import { Section } from "@/components/marketing/section";
import { BreadcrumbJsonLd } from "@/components/marketing/structured-data";
import { SIGN_UP_URL } from "@/lib/links";

export const metadata: Metadata = {
  title: "Compare",
  description:
    "See how Qeet Pay compares with Razorpay, Chargebee and Stripe — one platform for UPI + NACH acceptance, subscription billing, GST e-invoicing, AI dunning and orchestration.",
  alternates: { canonical: "/compare" },
};

const entries = Object.entries(comparisons);

export default function ComparePage() {
  return (
    <>
      <BreadcrumbJsonLd
        items={[
          { name: "Home", url: "/" },
          { name: "Compare", url: "/compare" },
        ]}
      />

      <PageHero
        eyebrow="Compare"
        title="How Qeet Pay compares —"
        titleAccent="honestly"
        subtitle="No single competitor covers UPI + NACH, subscription billing, GST e-invoicing, AI dunning and orchestration. Here's the side-by-side, checked against public docs and what we've actually shipped."
      />

      <Section>
        <Stagger staggerDelay={0.1} className="grid gap-6 lg:grid-cols-3">
          {entries.map(([slug, data]) => (
            <StaggerItem key={slug} className="h-full">
              <BezelCard className="p-6 sm:p-7">
                <p className="text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
                  Qeet Pay vs
                </p>
                <h2 className="mt-1 font-display text-2xl font-semibold tracking-tight">
                  {data.competitor}
                </h2>
                <p className="mt-3 text-sm text-muted-foreground">{data.competitorBlurb}</p>

                <ul className="mt-5 flex flex-col gap-2.5 text-sm">
                  {data.pitch.bullets.map((b) => (
                    <li key={b} className="flex items-start gap-2.5">
                      <span className="mt-0.5 inline-flex size-4 shrink-0 items-center justify-center rounded-full bg-brand/15 text-brand">
                        <CheckIcon className="size-2.5" aria-hidden />
                      </span>
                      <span className="text-muted-foreground">{b}</span>
                    </li>
                  ))}
                </ul>

                <div className="mt-6">
                  <ButtonLink
                    href={`/compare/${slug}`}
                    variant="outline"
                    className="w-full"
                  >
                    See the comparison <ArrowRightIcon className="size-4" />
                  </ButtonLink>
                </div>
              </BezelCard>
            </StaggerItem>
          ))}
        </Stagger>
      </Section>

      {/* CTA */}
      <Section muted>
        <Reveal>
          <div className="relative overflow-hidden rounded-3xl bg-(image:--brand-gradient) p-px shadow-2xl shadow-brand/20">
            <div className="relative flex flex-col items-center gap-6 rounded-[calc(1.5rem-1px)] bg-background/95 px-6 py-16 text-center backdrop-blur sm:px-12">
              <Eyebrow>Make the switch</Eyebrow>
              <h2 className="max-w-2xl font-display text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
                One platform instead of four vendors
              </h2>
              <p className="max-w-xl text-muted-foreground text-balance">
                White-glove migration, GSTR-1 filing set up end-to-end, and locked pricing for two
                years for design partners.
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
    </>
  );
}
