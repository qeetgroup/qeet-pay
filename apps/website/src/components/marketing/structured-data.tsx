/**
 * Schema.org JSON-LD blocks for Google rich-results + general search engine
 * surface area. Rendered as inline `<script type="application/ld+json">` tags.
 * We deliberately split each into its own component so call sites can pick
 * exactly which schemas apply to a given page.
 */

import { GITHUB_URL, SITE_URL } from "@/lib/links";

const BASE = SITE_URL;

interface JsonLdProps {
  data: Record<string, unknown>;
}

function JsonLd({ data }: JsonLdProps) {
  // Per Next.js JSON-LD guidance, escape `<` so an attacker can't break out of
  // the script tag via injected content. Cheaper than a serializer and
  // sufficient because our payloads are static.
  const json = JSON.stringify(data).replace(/</g, "\\u003c");
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: json }} />;
}

/** The Organization (publisher) block. Render once site-wide. */
export function OrganizationJsonLd() {
  return (
    <JsonLd
      data={{
        "@context": "https://schema.org",
        "@type": "Organization",
        "@id": `${BASE}/#organization`,
        name: "Qeet Pay",
        url: BASE,
        logo: `${BASE}/icon.png`,
        sameAs: [GITHUB_URL, "https://x.com/qeetpay"],
      }}
    />
  );
}

/** The WebSite block. Render once site-wide (typically on the home page). */
export function WebSiteJsonLd() {
  return (
    <JsonLd
      data={{
        "@context": "https://schema.org",
        "@type": "WebSite",
        "@id": `${BASE}/#website`,
        url: BASE,
        name: "Qeet Pay",
        publisher: { "@id": `${BASE}/#organization` },
        inLanguage: "en-IN",
      }}
    />
  );
}

/** Product block for the payments-platform offering. */
export function ProductJsonLd() {
  return (
    <JsonLd
      data={{
        "@context": "https://schema.org",
        "@type": "Product",
        name: "Qeet Pay",
        description:
          "India-first payments, billing, and financial infrastructure. UPI, cards, NACH & net banking acceptance, payouts, subscription billing, and GST-compliant invoicing with IRN e-invoicing — one API.",
        brand: { "@type": "Brand", name: "Qeet Pay" },
        offers: [
          {
            "@type": "Offer",
            name: "Free",
            price: "0",
            priceCurrency: "INR",
            availability: "https://schema.org/InStock",
            url: `${BASE}/pricing`,
          },
          {
            "@type": "Offer",
            name: "Growth",
            price: "9999",
            priceCurrency: "INR",
            availability: "https://schema.org/InStock",
            url: `${BASE}/pricing`,
          },
        ],
      }}
    />
  );
}

/** SoftwareApplication block — labels the service as fintech software. */
export function SoftwareApplicationJsonLd() {
  return (
    <JsonLd
      data={{
        "@context": "https://schema.org",
        "@type": "SoftwareApplication",
        name: "Qeet Pay",
        applicationCategory: "FinanceApplication",
        applicationSubCategory: "Payments, Billing & GST Infrastructure",
        operatingSystem: "Web, Linux, macOS, Windows",
        offers: { "@type": "Offer", price: "0", priceCurrency: "INR" },
        publisher: { "@id": `${BASE}/#organization` },
      }}
    />
  );
}

/**
 * FAQPage block — drives Google's FAQ rich result. Pass the same Q/A list the
 * visible FAQ accordion renders so the structured data always matches the page.
 */
export function FaqJsonLd({ items }: { items: { q: string; a: string }[] }) {
  return (
    <JsonLd
      data={{
        "@context": "https://schema.org",
        "@type": "FAQPage",
        mainEntity: items.map((item) => ({
          "@type": "Question",
          name: item.q,
          acceptedAnswer: {
            "@type": "Answer",
            text: item.a,
          },
        })),
      }}
    />
  );
}

/**
 * Breadcrumb helper for a route. Pass an ordered list of segments —
 * Schema.org expects 1-indexed positions.
 */
export function BreadcrumbJsonLd({ items }: { items: { name: string; url: string }[] }) {
  return (
    <JsonLd
      data={{
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        itemListElement: items.map((item, i) => ({
          "@type": "ListItem",
          position: i + 1,
          name: item.name,
          item: item.url.startsWith("http") ? item.url : `${BASE}${item.url}`,
        })),
      }}
    />
  );
}
