import type { MetadataRoute } from "next";

import { SITE_URL } from "@/lib/links";

/**
 * Marketing-site sitemap (Next 16 `MetadataRoute.Sitemap`).
 *
 * Enumerates every public marketing route. The hand-maintained list keeps
 * non-public paths (`/api/`) out by construction. Priorities/frequencies follow
 * the marketing hierarchy: the home + top conversion pages rank highest and
 * change most often.
 */

const BASE_URL = SITE_URL;

type ChangeFrequency = NonNullable<MetadataRoute.Sitemap[number]["changeFrequency"]>;

function entry(
  path: string,
  changeFrequency: ChangeFrequency,
  priority: number,
  lastModified: string | Date,
): MetadataRoute.Sitemap[number] {
  return {
    url: path === "/" ? BASE_URL : `${BASE_URL}${path}`,
    lastModified,
    changeFrequency,
    priority,
  };
}

export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();

  const staticRoutes: MetadataRoute.Sitemap = [
    entry("/", "weekly", 1.0, now),
    entry("/product", "monthly", 0.9, now),
    entry("/pricing", "monthly", 0.9, now),
    entry("/compare", "monthly", 0.7, now),
    entry("/compare/razorpay", "monthly", 0.6, now),
    entry("/compare/chargebee", "monthly", 0.6, now),
    entry("/compare/stripe", "monthly", 0.6, now),
    entry("/about", "monthly", 0.6, now),
    entry("/contact", "yearly", 0.5, now),
  ];

  return staticRoutes;
}
