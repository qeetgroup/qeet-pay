import {
  BanknoteIcon,
  CreditCardIcon,
  FileTextIcon,
  LandmarkIcon,
  LineChartIcon,
  type LucideIcon,
  RepeatIcon,
  RouteIcon,
  ShieldCheckIcon,
} from "lucide-react";

/**
 * The eight Qeet Pay product pillars — the single source of truth for the
 * homepage feature grid and the deeper `/product` overview. Copy is sourced
 * from the Qeet Pay README "What it covers" section. Each pillar carries an
 * `id` so the footer's in-page anchors (`/product#billing`, `#gst`,
 * `#orchestration`) resolve to a real section on the product page.
 */
export type Feature = {
  /** Stable anchor id (used by /product section links). */
  id: string;
  icon: LucideIcon;
  /** Short pillar name. */
  title: string;
  /** One-line summary for the homepage grid. */
  blurb: string;
  /** Deeper capability bullets for the /product page. */
  points: string[];
};

export const features: Feature[] = [
  {
    id: "acceptance",
    icon: CreditCardIcon,
    title: "Payment acceptance",
    blurb: "UPI, cards, net banking, wallets, e-NACH and virtual accounts — every way India pays, one integration.",
    points: [
      "UPI: collect/intent, QR, UPI 2.0 one-time mandates, AutoPay, UPI Circle & RuPay-credit-on-UPI",
      "Cards: Visa, Mastercard, RuPay & Amex with 3DS 2.0 and RBI CoFT network tokenization",
      "Net banking across 72+ Indian banks, plus Paytm, PhonePe, Mobikwik, Amazon Pay wallets",
      "e-NACH (Aadhaar OTP) + physical NACH for high-value B2B recurring",
      "Virtual accounts — a unique IFSC + account per customer for auto-reconciled B2B collection",
    ],
  },
  {
    id: "payouts",
    icon: BanknoteIcon,
    title: "Payouts & disbursements",
    blurb: "Instant UPI/IMPS, NEFT and RTGS payouts — single or bulk, with maker-checker approval.",
    points: [
      "UPI & IMPS (instant), NEFT and RTGS out of the same balance",
      "Bulk payouts via API or CSV upload with a maker-checker approval workflow",
      "Salary disbursement integrated with Qeet People (EWA, PF, ESI, PT)",
      "Refunds: full, partial, instant (IMPS) or standard",
    ],
  },
  {
    id: "billing",
    icon: RepeatIcon,
    title: "Subscription billing",
    blurb: "Every pricing model, the full subscription lifecycle, and usage metering at high throughput.",
    points: [
      "Flat-rate, per-unit, tiered, volume, stairstep, usage-based, committed + overage and hybrid pricing",
      "Full lifecycle: create, upgrade, downgrade, pause, cancel, reactivate — with proration",
      "High-throughput usage metering with a real-time customer usage widget",
      "Free trials, freemium, add-ons and one-time charges",
    ],
  },
  {
    id: "gst",
    icon: FileTextIcon,
    title: "GST invoicing & e-invoicing",
    blurb: "GST-compliant invoices, IRN e-invoicing, GSTR-1 auto-filing and a combined e-invoice + UPI QR.",
    points: [
      "CGST/SGST/IGST auto-calculation with place-of-supply rules and a 10,000+ HSN/SAC database",
      "IRN e-invoicing via the IRP — signed QR embedded on the PDF",
      "Combined QR (exclusive): IRP-validated e-invoice data + UPI payment intent — scan once to verify and pay",
      "GSTR-1 auto-filing and GSTR-2B (ITC) reconciliation via the GSTN API",
      "TDS/TCS tracking, multi-GSTIN, credit/debit notes and export invoices",
    ],
  },
  {
    id: "orchestration",
    icon: RouteIcon,
    title: "Payment orchestration",
    blurb: "Route every payment to the best provider by cost, auth rate and health — with automatic failover.",
    points: [
      "Rule-based routing by method, BIN, amount and geography",
      "ML-based routing for a +5–11 percentage-point auth-rate lift over a single provider",
      "Automatic failover: a provider 5xx retries instantly on a secondary, transparent to the customer",
      "Razorpay, Cashfree, PayU, PhonePe and Stripe India behind one API",
    ],
  },
  {
    id: "embedded-finance",
    icon: LandmarkIcon,
    title: "Embedded finance",
    blurb: "Lending, BNPL, virtual cards, insurance and escrow — financial products inside your product.",
    points: [
      "Embedded lending: AA-underwritten working-capital advances up to ₹25L, repaid from daily settlement",
      "BNPL at checkout via Credit Line on UPI (August 2025 RBI guidelines)",
      "Virtual cards for employee expense and customer wallet use",
      "Digital escrow with conditional release on delivery/milestone confirmation",
      "Embedded insurance: payment protection, fraud and subscription-interruption cover",
    ],
  },
  {
    id: "fraud",
    icon: ShieldCheckIcon,
    title: "Fraud detection & XAI",
    blurb: "Real-time scoring under 100ms with explainable, India-native fraud intelligence.",
    points: [
      "100+ signals — device, IP, velocity, behavioural biometrics and UPI-handle history — scored in < 100ms",
      "India-specific patterns: UPI collect-request scams, screen monitoring and fake payment screenshots",
      "Explainable AI: the top-5 SHAP signals per blocked transaction, in plain English",
      "Automated chargeback response with an evidence package compiled and submitted for you",
    ],
  },
  {
    id: "analytics",
    icon: LineChartIcon,
    title: "Native analytics",
    blurb: "MRR waterfall, churn, dunning funnel and AI cash-flow forecasting — no BI tool required.",
    points: [
      "MRR waterfall — new / expansion / contraction / churned / reactivation",
      "ARR, logo & revenue churn, NRR, LTV, ARPU and cohort retention",
      "Dunning funnel: failures → recovery attempts → recovered, with channel effectiveness",
      "AI cash-flow forecast: a 30-day projected balance with working-capital recommendations",
    ],
  },
];
