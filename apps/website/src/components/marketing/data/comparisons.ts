import type { ComparisonData } from "@/components/marketing/comparison-page";

/**
 * Competitive comparison data — one object per competitor, rendered by the
 * shared `ComparisonPage`. Rows are grouped by `section`. Sourced from the Qeet
 * Pay README "What makes it different" table plus public product information;
 * where Qeet Pay is still building a capability, the cell says so honestly.
 */

export const razorpay: ComparisonData = {
  competitor: "Razorpay",
  competitorBlurb:
    "Razorpay is India's best-known payment gateway. It handles acceptance and payouts well — but billing, GST e-invoicing and dunning live in separate tools you have to stitch together.",
  pitch: {
    headline: "One platform instead of Razorpay + Chargebee + ClearTax",
    subhead:
      "Teams switch to Qeet Pay to collapse payments, subscription billing and GST e-invoicing into a single API, one dashboard and one reconciliation — without giving up UPI-first acceptance.",
    bullets: [
      "GST e-invoicing with IRN and GSTR-1 auto-filing built in — not a third-party add-on",
      "AI dunning that classifies UPI failures and adapts channel + timing for 3x recovery",
      "Payment orchestration with automatic failover across providers, including Razorpay itself",
    ],
  },
  factsQeetpay: [
    { label: "UPI fee", value: "0.15%" },
    { label: "GST e-invoicing", value: "Built in" },
    { label: "Subscription billing", value: "Native" },
    { label: "Orchestration", value: "Yes" },
  ],
  factsCompetitor: [
    { label: "UPI fee", value: "~2% MDR" },
    { label: "GST e-invoicing", value: "Not offered" },
    { label: "Subscription billing", value: "Partial" },
    { label: "Orchestration", value: "Not offered" },
  ],
  rows: [
    { section: "Payments & acceptance", feature: "UPI (collect, QR, AutoPay)", qeetpay: true, competitor: true },
    { section: "Payments & acceptance", feature: "Cards with 3DS 2.0 + CoFT tokens", qeetpay: true, competitor: true },
    { section: "Payments & acceptance", feature: "e-NACH & physical NACH", qeetpay: true, competitor: true },
    { section: "Payments & acceptance", feature: "Virtual accounts (auto-reconcile)", qeetpay: true, competitor: true },
    { section: "Payouts", feature: "UPI/IMPS/NEFT/RTGS payouts", qeetpay: true, competitor: true },
    { section: "Payouts", feature: "Bulk payouts with maker-checker", qeetpay: true, competitor: true },
    { section: "Billing & subscriptions", feature: "Full subscription lifecycle", qeetpay: true, competitor: "partial", note: "Razorpay's subscriptions cover recurring charges but not usage-based or hybrid pricing." },
    { section: "Billing & subscriptions", feature: "Usage-based & hybrid pricing", qeetpay: true, competitor: false },
    { section: "GST & compliance", feature: "GST invoices (CGST/SGST/IGST)", qeetpay: true, competitor: "partial" },
    { section: "GST & compliance", feature: "IRN e-invoicing via IRP", qeetpay: true, competitor: false },
    { section: "GST & compliance", feature: "GSTR-1 auto-filing", qeetpay: true, competitor: false },
    { section: "GST & compliance", feature: "Combined e-invoice + UPI QR", qeetpay: true, competitor: false },
    { section: "GST & compliance", feature: "Multi-GSTIN billing", qeetpay: true, competitor: false },
    { section: "Intelligence & orchestration", feature: "AI dunning (UPI-failure aware)", qeetpay: true, competitor: false },
    { section: "Intelligence & orchestration", feature: "Payment orchestration & failover", qeetpay: true, competitor: false },
    { section: "Intelligence & orchestration", feature: "Explainable-AI fraud scoring", qeetpay: true, competitor: "partial" },
    { section: "Embedded finance", feature: "Embedded lending via Account Aggregator", qeetpay: true, competitor: "partial" },
    { section: "Embedded finance", feature: "Marketplace GST split settlements", qeetpay: true, competitor: false },
    { section: "India-native", feature: "WhatsApp-native invoicing & bot", qeetpay: true, competitor: false },
    { section: "India-native", feature: "Native subscription analytics", qeetpay: true, competitor: false },
  ],
  cta: {
    headline: "Move to one platform — keep your UPI-first stack",
    subhead: "White-glove migration from Razorpay, locked pricing for two years, and GSTR-1 filing set up end-to-end.",
  },
};

export const chargebee: ComparisonData = {
  competitor: "Chargebee",
  competitorBlurb:
    "Chargebee is a mature subscription-billing platform — but it isn't a payment gateway. You still bolt on a PG for UPI/NACH and a separate tool for GST e-invoicing.",
  pitch: {
    headline: "Billing that already knows how India gets paid",
    subhead:
      "Qeet Pay pairs a full subscription-billing engine with native UPI, NACH and card acceptance and GST e-invoicing — so recurring revenue, collection and compliance live in one place.",
    bullets: [
      "UPI AutoPay & e-NACH mandates are first-class, not a gateway you integrate separately",
      "IRN e-invoicing and GSTR-1 auto-filing ship with billing — no ClearTax bolt-on",
      "AI dunning tuned to UPI failure codes recovers involuntary churn Indian retries miss",
    ],
  },
  factsQeetpay: [
    { label: "Payment gateway", value: "Native" },
    { label: "UPI AutoPay / NACH", value: "Yes" },
    { label: "GST e-invoicing", value: "Built in" },
    { label: "Setup", value: "One vendor" },
  ],
  factsCompetitor: [
    { label: "Payment gateway", value: "Bring your own" },
    { label: "UPI AutoPay / NACH", value: "Via PG" },
    { label: "GST e-invoicing", value: "Add-on" },
    { label: "Setup", value: "Multi-vendor" },
  ],
  rows: [
    { section: "Payments & acceptance", feature: "Native UPI / cards / net banking", qeetpay: true, competitor: false, note: "Chargebee orchestrates a payment gateway you bring; it doesn't acquire payments itself." },
    { section: "Payments & acceptance", feature: "e-NACH & UPI AutoPay mandates", qeetpay: true, competitor: "partial" },
    { section: "Payouts", feature: "UPI/IMPS/NEFT/RTGS payouts", qeetpay: true, competitor: false },
    { section: "Billing & subscriptions", feature: "Full subscription lifecycle", qeetpay: true, competitor: true },
    { section: "Billing & subscriptions", feature: "Usage-based & hybrid pricing", qeetpay: true, competitor: true },
    { section: "Billing & subscriptions", feature: "Proration, trials & add-ons", qeetpay: true, competitor: true },
    { section: "GST & compliance", feature: "GST invoices (CGST/SGST/IGST)", qeetpay: true, competitor: "partial" },
    { section: "GST & compliance", feature: "IRN e-invoicing via IRP", qeetpay: true, competitor: false },
    { section: "GST & compliance", feature: "GSTR-1 auto-filing", qeetpay: true, competitor: false },
    { section: "GST & compliance", feature: "Combined e-invoice + UPI QR", qeetpay: true, competitor: false },
    { section: "Intelligence & orchestration", feature: "AI dunning (UPI-failure aware)", qeetpay: true, competitor: "partial" },
    { section: "Intelligence & orchestration", feature: "Payment orchestration & failover", qeetpay: true, competitor: "partial" },
    { section: "Embedded finance", feature: "Embedded lending via Account Aggregator", qeetpay: true, competitor: false },
    { section: "India-native", feature: "WhatsApp-native invoicing & bot", qeetpay: true, competitor: false },
    { section: "India-native", feature: "Native subscription analytics", qeetpay: true, competitor: "partial" },
  ],
  cta: {
    headline: "Billing and collection, finally under one roof",
    subhead: "Migrate your subscriptions from Chargebee and get UPI-first acceptance and GST e-invoicing in the same move.",
  },
};

export const stripe: ComparisonData = {
  competitor: "Stripe",
  competitorBlurb:
    "Stripe is the developer's global standard for cards and billing. In India, though, it lacks GST e-invoicing, GSTR filing and the UPI-native depth Indian businesses need.",
  pitch: {
    headline: "Stripe-grade DX, built for Indian compliance",
    subhead:
      "Qeet Pay gives you a clean, well-documented API and a full billing engine — plus the UPI, NACH and GST machinery Stripe leaves to you in India.",
    bullets: [
      "GST e-invoicing with IRN, GSTR-1 auto-filing and multi-GSTIN — none of which Stripe handles",
      "Deep UPI support: AutoPay, UPI Circle, RuPay credit on UPI and virtual accounts",
      "India data residency (ap-south-1) with DPDP-aligned consent and erasure",
    ],
  },
  factsQeetpay: [
    { label: "UPI depth", value: "Native" },
    { label: "GST e-invoicing", value: "Built in" },
    { label: "Data residency", value: "ap-south-1" },
    { label: "GSTR filing", value: "Automated" },
  ],
  factsCompetitor: [
    { label: "UPI depth", value: "Basic" },
    { label: "GST e-invoicing", value: "Not offered" },
    { label: "Data residency", value: "Global" },
    { label: "GSTR filing", value: "Not offered" },
  ],
  rows: [
    { section: "Payments & acceptance", feature: "UPI (collect, QR, AutoPay)", qeetpay: true, competitor: "partial", note: "Stripe supports UPI in India but not AutoPay, UPI Circle or RuPay-credit-on-UPI." },
    { section: "Payments & acceptance", feature: "Cards with 3DS 2.0 + CoFT tokens", qeetpay: true, competitor: true },
    { section: "Payments & acceptance", feature: "e-NACH & physical NACH", qeetpay: true, competitor: "partial" },
    { section: "Payments & acceptance", feature: "Virtual accounts (auto-reconcile)", qeetpay: true, competitor: false },
    { section: "Payouts", feature: "Domestic India payouts (IMPS/NEFT)", qeetpay: true, competitor: "partial" },
    { section: "Billing & subscriptions", feature: "Full subscription lifecycle", qeetpay: true, competitor: true },
    { section: "Billing & subscriptions", feature: "Usage-based & hybrid pricing", qeetpay: true, competitor: true },
    { section: "GST & compliance", feature: "GST invoices (CGST/SGST/IGST)", qeetpay: true, competitor: "partial" },
    { section: "GST & compliance", feature: "IRN e-invoicing via IRP", qeetpay: true, competitor: false },
    { section: "GST & compliance", feature: "GSTR-1 auto-filing", qeetpay: true, competitor: false },
    { section: "GST & compliance", feature: "Combined e-invoice + UPI QR", qeetpay: true, competitor: false },
    { section: "Intelligence & orchestration", feature: "AI dunning (UPI-failure aware)", qeetpay: true, competitor: "partial" },
    { section: "Intelligence & orchestration", feature: "Payment orchestration & failover", qeetpay: true, competitor: false },
    { section: "Embedded finance", feature: "Embedded lending via Account Aggregator", qeetpay: true, competitor: false },
    { section: "India-native", feature: "Carbon footprint tracking", qeetpay: true, competitor: "partial" },
    { section: "India-native", feature: "WhatsApp-native invoicing & bot", qeetpay: true, competitor: false },
  ],
  cta: {
    headline: "Keep the developer experience, gain Indian compliance",
    subhead: "Migrate from Stripe and get GST e-invoicing, GSTR filing and UPI-native acceptance out of the box.",
  },
};

/** Registry for the /compare index + per-competitor routes. */
export const comparisons = { razorpay, chargebee, stripe } as const;

export type CompetitorSlug = keyof typeof comparisons;
