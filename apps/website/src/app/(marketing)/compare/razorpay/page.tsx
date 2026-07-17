import type { Metadata } from "next";

import { ComparisonPage } from "@/components/marketing/comparison-page";
import { razorpay } from "@/components/marketing/data/comparisons";

export const metadata: Metadata = {
  title: "Qeet Pay vs Razorpay",
  description:
    "Qeet Pay vs Razorpay — one platform for UPI-first acceptance, subscription billing, GST e-invoicing with IRN, AI dunning and orchestration, instead of stitching Razorpay + Chargebee + ClearTax together.",
  alternates: { canonical: "/compare/razorpay" },
};

export default function CompareRazorpayPage() {
  return <ComparisonPage data={razorpay} />;
}
