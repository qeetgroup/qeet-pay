import type { Metadata } from "next";

import { ComparisonPage } from "@/components/marketing/comparison-page";
import { chargebee } from "@/components/marketing/data/comparisons";

export const metadata: Metadata = {
  title: "Qeet Pay vs Chargebee",
  description:
    "Qeet Pay vs Chargebee — a full subscription-billing engine with native UPI/NACH acceptance and GST e-invoicing built in, so recurring revenue, collection and compliance live in one place.",
  alternates: { canonical: "/compare/chargebee" },
};

export default function CompareChargebeePage() {
  return <ComparisonPage data={chargebee} />;
}
