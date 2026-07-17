import { CheckIcon, MailIcon, MessageSquareIcon } from "lucide-react";
import type { Metadata } from "next";

import { ContactForm } from "@/components/marketing/contact-form";
import { Reveal } from "@/components/marketing/motion";
import { PageHero } from "@/components/marketing/page-hero";
import { Section } from "@/components/marketing/section";
import { BreadcrumbJsonLd } from "@/components/marketing/structured-data";
import { DOCS_URL } from "@/lib/links";

export const metadata: Metadata = {
  title: "Contact",
  description:
    "Talk to the Qeet Pay team — sales, technical questions, or becoming a design partner. White-glove migration from Razorpay or Chargebee and locked pricing for two years.",
  alternates: { canonical: "/contact" },
};

const partnerPerks = [
  "Direct access to the founding team for requirements and feedback",
  "White-glove integration and migration from Razorpay / Chargebee",
  "GSTR-1 auto-filing set up end-to-end",
  "Locked pricing for two years and influence over Phase 1 scope",
];

export default function ContactPage() {
  return (
    <>
      <BreadcrumbJsonLd
        items={[
          { name: "Home", url: "/" },
          { name: "Contact", url: "/contact" },
        ]}
      />

      <PageHero
        eyebrow="Contact"
        title="Let's talk"
        titleAccent="payments"
        subtitle="Sales, integration questions, or becoming a design partner — tell us what you're building and we'll route you to the right person within one business day."
      />

      <Section>
        <div className="grid gap-12 lg:grid-cols-2 lg:gap-16">
          {/* Info column */}
          <Reveal className="flex flex-col gap-8">
            <div>
              <h2 className="font-display text-2xl font-semibold tracking-tight">
                Become a design partner
              </h2>
              <p className="mt-3 text-muted-foreground">
                Qeet Pay is in its design-partner phase. If you&apos;re a CTO, CFO or engineering
                lead at an Indian SaaS company, marketplace or enterprise wrestling with GST
                invoicing, UPI AutoPay churn, multi-vendor fragmentation or settlement complexity —
                we&apos;d love to build with you.
              </p>
              <ul className="mt-5 flex flex-col gap-3 text-sm">
                {partnerPerks.map((perk) => (
                  <li key={perk} className="flex items-start gap-2.5">
                    <span className="mt-0.5 inline-flex size-5 shrink-0 items-center justify-center rounded-full bg-brand/15 text-brand">
                      <CheckIcon className="size-3" aria-hidden />
                    </span>
                    <span className="text-muted-foreground">{perk}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="flex flex-col gap-3">
              <a
                href="mailto:partnerships@qeet.in"
                className="group flex items-start gap-3 rounded-2xl border border-border/60 bg-card p-4 transition-colors hover:border-brand/40 focus-ring-brand"
              >
                <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand/12 text-brand">
                  <MailIcon className="size-5" aria-hidden />
                </span>
                <span>
                  <span className="block text-sm font-medium">Partnerships &amp; sales</span>
                  <span className="block text-sm text-muted-foreground group-hover:text-foreground">
                    partnerships@qeet.in
                  </span>
                </span>
              </a>
              <a
                href={DOCS_URL}
                className="group flex items-start gap-3 rounded-2xl border border-border/60 bg-card p-4 transition-colors hover:border-brand/40 focus-ring-brand"
              >
                <span className="inline-flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand/12 text-brand">
                  <MessageSquareIcon className="size-5" aria-hidden />
                </span>
                <span>
                  <span className="block text-sm font-medium">Technical questions</span>
                  <span className="block text-sm text-muted-foreground group-hover:text-foreground">
                    Browse the developer docs
                  </span>
                </span>
              </a>
            </div>
          </Reveal>

          {/* Form column */}
          <Reveal delay={0.1}>
            <ContactForm />
          </Reveal>
        </div>
      </Section>
    </>
  );
}
