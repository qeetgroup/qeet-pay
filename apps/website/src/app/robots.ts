import type { MetadataRoute } from "next";

import { SITE_URL } from "@/lib/links";

// Crawl rules for the Qeet Pay marketing surface. The operator console lives
// on a different host and has its own robots; this file only covers the public
// marketing origin (pay.qeet.in).
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        // Compare pages are intentionally indexable — SEO bait for high-intent
        // queries like "Qeet Pay vs Razorpay".
        disallow: ["/api/"],
      },
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
