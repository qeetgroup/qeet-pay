import { cn } from "@qeetrix/ui";
import { CheckIcon } from "lucide-react";
import type { Metadata } from "next";

import { ArrowCta } from "@/components/marketing/blocks/arrow-cta";
import { Eyebrow } from "@/components/marketing/blocks/eyebrow";
import { ButtonLink } from "@/components/marketing/button-link";
import { features } from "@/components/marketing/data/features";
import { Reveal, Stagger, StaggerItem } from "@/components/marketing/motion";
import { PageHero } from "@/components/marketing/page-hero";
import { Section } from "@/components/marketing/section";
import { BreadcrumbJsonLd, ProductJsonLd } from "@/components/marketing/structured-data";
import { DOCS_URL, SIGN_UP_URL } from "@/lib/links";

export const metadata: Metadata = {
  title: "Product",
  description:
    "The whole money stack in one API — payment acceptance, payouts, subscription billing, GST e-invoicing, orchestration, embedded finance, explainable-AI fraud, and native analytics.",
  alternates: { canonical: "/product" },
};

export default function ProductPage() {
  return (
    <>
      <ProductJsonLd />
      <BreadcrumbJsonLd
        items={[
          { name: "Home", url: "/" },
          { name: "Product", url: "/product" },
        ]}
      />

      <PageHero
        eyebrow="Product"
        title="The whole money stack,"
        titleAccent="in one API"
        subtitle="Eight product pillars on a shared, append-only double-entry ledger — so acceptance, billing and compliance are one system, not four integrations."
        cta={
          <>
            <ArrowCta href={SIGN_UP_URL}>Start free</ArrowCta>
            <ButtonLink variant="outline" size="lg" href={DOCS_URL} className="h-11 px-5">
              Read the docs
            </ButtonLink>
          </>
        }
      >
        {/* Quick jump — anchors resolve to the sections below. */}
        <nav aria-label="Product sections" className="flex flex-wrap justify-center gap-2">
          {features.map((f) => (
            <a
              key={f.id}
              href={`#${f.id}`}
              className="rounded-full border border-border/60 bg-background/60 px-3 py-1.5 text-xs font-medium text-muted-foreground backdrop-blur-sm transition-colors hover:border-brand/40 hover:text-foreground focus-ring-brand"
            >
              {f.title}
            </a>
          ))}
        </nav>
      </PageHero>

      {features.map((f, idx) => {
        const Icon = f.icon;
        const flipped = idx % 2 === 1;
        return (
          <Section key={f.id} id={f.id} muted={flipped}>
            <div className="grid gap-10 lg:grid-cols-2 lg:items-center">
              <Reveal
                className={cn("flex flex-col items-start", flipped && "lg:order-2")}
              >
                <span className="inline-flex size-12 items-center justify-center rounded-2xl bg-brand/12 text-brand">
                  <Icon className="size-6" aria-hidden />
                </span>
                <p className="mt-5 text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
                  Pillar {String(idx + 1).padStart(2, "0")}
                </p>
                <h2 className="mt-2 font-display text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
                  {f.title}
                </h2>
                <p className="mt-4 max-w-xl text-muted-foreground sm:text-lg">{f.blurb}</p>
              </Reveal>

              <Stagger
                staggerDelay={0.07}
                className={cn("grid gap-3", flipped && "lg:order-1")}
              >
                {f.points.map((point) => (
                  <StaggerItem key={point}>
                    <div className="flex items-start gap-3 rounded-xl border border-border/60 bg-card p-4">
                      <span className="mt-0.5 inline-flex size-5 shrink-0 items-center justify-center rounded-full bg-brand/15 text-brand">
                        <CheckIcon className="size-3" aria-hidden />
                      </span>
                      <span className="text-sm text-muted-foreground">{point}</span>
                    </div>
                  </StaggerItem>
                ))}
              </Stagger>
            </div>
          </Section>
        );
      })}

      {/* Closing CTA */}
      <Section>
        <Reveal>
          <div className="relative overflow-hidden rounded-3xl bg-(image:--brand-gradient) p-px shadow-2xl shadow-brand/20">
            <div className="relative flex flex-col items-center gap-6 rounded-[calc(1.5rem-1px)] bg-background/95 px-6 py-16 text-center backdrop-blur sm:px-12">
              <Eyebrow>One platform</Eyebrow>
              <h2 className="max-w-2xl font-display text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
                Stop stitching four vendors together
              </h2>
              <p className="max-w-xl text-muted-foreground text-balance">
                Payments, billing and GST on one ledger — start free and go live this week.
              </p>
              <div className="flex flex-col items-center gap-3 sm:flex-row">
                <ArrowCta href={SIGN_UP_URL}>Start free</ArrowCta>
                <ButtonLink variant="outline" size="lg" href="/pricing" className="h-11 px-5">
                  See pricing
                </ButtonLink>
              </div>
            </div>
          </div>
        </Reveal>
      </Section>
    </>
  );
}
