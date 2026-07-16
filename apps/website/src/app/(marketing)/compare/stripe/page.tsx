import type { Metadata } from "next";

import { ComparisonPage } from "@/components/marketing/comparison-page";
import { stripe } from "@/components/marketing/data/comparisons";

export const metadata: Metadata = {
  title: "Qeet Pay vs Stripe",
  description:
    "Qeet Pay vs Stripe — Stripe-grade developer experience with the UPI depth, GST e-invoicing, GSTR filing and India data residency that Indian businesses need.",
  alternates: { canonical: "/compare/stripe" },
};

export default function CompareStripePage() {
  return <ComparisonPage data={stripe} />;
}
