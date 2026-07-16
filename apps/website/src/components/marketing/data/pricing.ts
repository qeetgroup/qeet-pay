import { SIGN_UP_URL } from "@/lib/links";

/**
 * Shared pricing tiers — the single source of truth for both the full
 * `/pricing` page and the condensed homepage Pricing section. Figures are
 * display copy sourced from the Qeet Pay README pricing table.
 */
export type PricingTier = {
  name: string;
  description: string;
  /** Headline price, ₹ (or "Custom"). */
  price: string;
  /** Small period line beside the price. */
  period: string;
  /** Total-processing-volume band for the tier. */
  tpv: string;
  cta: { label: string; href: string };
  featured?: boolean;
  features: string[];
};

export const tiers: PricingTier[] = [
  {
    name: "Free",
    description: "For side projects and early builders.",
    price: "₹0",
    period: "forever",
    tpv: "Up to ₹10 lakh TPV / month",
    cta: { label: "Start free", href: SIGN_UP_URL },
    features: [
      "UPI, cards & net-banking acceptance",
      "Payouts via UPI & IMPS",
      "GST invoices (CGST/SGST/IGST auto-calc)",
      "Flat-rate & per-unit subscriptions",
      "Sandbox + test keys, full API access",
      "Community support",
    ],
  },
  {
    name: "Starter",
    description: "For small teams taking their first payments.",
    price: "₹2,999",
    period: "/ month",
    tpv: "Up to ₹1 crore TPV / month",
    cta: { label: "Start free trial", href: `${SIGN_UP_URL}?plan=starter` },
    features: [
      "Everything in Free",
      "E-NACH mandates + UPI AutoPay",
      "Bulk payouts with maker-checker",
      "WhatsApp invoice delivery",
      "Tiered & volume pricing models",
      "Email support",
    ],
  },
  {
    name: "Growth",
    description: "For scaling SaaS & D2C businesses.",
    price: "₹9,999",
    period: "/ month",
    tpv: "Up to ₹10 crore TPV / month",
    cta: { label: "Start free trial", href: `${SIGN_UP_URL}?plan=growth` },
    featured: true,
    features: [
      "Everything in Starter",
      "IRN e-invoicing — zero per-invoice fee",
      "GSTR-1 auto-filing + multi-GSTIN",
      "AI dunning (UPI-failure aware)",
      "Payment orchestration & failover",
      "Priority support · 99.95% uptime SLA",
    ],
  },
  {
    name: "Scale",
    description: "For marketplaces & high-volume platforms.",
    price: "₹29,999",
    period: "/ month",
    tpv: "Up to ₹100 crore TPV / month",
    cta: { label: "Talk to sales", href: "/contact" },
    features: [
      "Everything in Growth",
      "Marketplace split settlements (GST/TCS/TDS)",
      "Virtual accounts + auto-reconciliation",
      "AI cash-flow forecasting",
      "Embedded lending via Account Aggregator",
      "Dedicated success manager",
    ],
  },
  {
    name: "Enterprise",
    description: "For regulated & cross-border scale.",
    price: "Custom",
    period: "annual contract",
    tpv: "Unlimited TPV",
    cta: { label: "Talk to sales", href: "/contact" },
    features: [
      "Everything in Scale",
      "Digital escrow + cross-border collection",
      "Single-tenant deploy + data residency",
      "Custom routing & own-PA-license path",
      "24/7 phone support · 99.99% uptime SLA",
      "DPDP, PCI-DSS & SOC 2 attestations",
    ],
  },
];

/** Condensed set for the homepage Pricing teaser (links out to /pricing). */
export const homeTiers: PricingTier[] = tiers.filter((t) =>
  ["Free", "Growth", "Enterprise"].includes(t.name),
);

/** Transparent, pass-through processing fees (from the README pricing note). */
export type ProcessingFee = { method: string; fee: string; note: string };

export const processingFees: ProcessingFee[] = [
  { method: "UPI", fee: "0.15%", note: "0% MDR (government mandate) — 0.15% platform fee only" },
  { method: "Cards", fee: "~1.75%", note: "0.25% Qeet Pay + ~1.5% network interchange" },
  { method: "NACH", fee: "₹8", note: "Per successful debit (e-NACH / physical)" },
  { method: "Net banking", fee: "₹5", note: "Per transaction, 72+ banks" },
];
