import { Badge, cn } from "@qeetrix/ui";
import { CheckIcon } from "lucide-react";

import { BezelCard } from "@/components/marketing/blocks/bezel-card";
import { ButtonLink } from "@/components/marketing/button-link";
import { Stagger, StaggerItem } from "@/components/marketing/motion";
import type { PricingTier } from "@/components/marketing/data/pricing";

/**
 * Presentational grid of pricing tier cards, composed from the existing
 * `BezelCard` + `ButtonLink` primitives. Shared by the full `/pricing` page and
 * the condensed homepage teaser so both stay in sync with `data/pricing.ts`.
 * The featured tier gets the warm bezel treatment and a "Most popular" badge.
 */
export function PricingTiers({
  tiers,
  className,
}: {
  tiers: PricingTier[];
  className?: string;
}) {
  return (
    <Stagger
      staggerDelay={0.08}
      className={cn("grid gap-6 sm:grid-cols-2 lg:grid-cols-3", className)}
    >
      {tiers.map((tier) => (
        <StaggerItem key={tier.name} className="h-full">
          <BezelCard featured={tier.featured} className="p-6 sm:p-7">
            <div className="flex items-center justify-between gap-2">
              <h3 className="font-display text-lg font-semibold tracking-tight">{tier.name}</h3>
              {tier.featured && <Badge>Most popular</Badge>}
            </div>
            <p className="mt-2 text-sm text-muted-foreground">{tier.description}</p>

            <div className="mt-5 flex items-baseline gap-1.5">
              <span className="font-display text-4xl font-semibold tracking-tight">
                {tier.price}
              </span>
              <span className="text-sm text-muted-foreground">{tier.period}</span>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">{tier.tpv}</p>

            <ButtonLink
              href={tier.cta.href}
              variant={tier.featured ? "default" : "outline"}
              className="mt-6 w-full"
            >
              {tier.cta.label}
            </ButtonLink>

            <ul className="mt-6 flex flex-col gap-3 text-sm">
              {tier.features.map((f) => (
                <li key={f} className="flex items-start gap-2.5">
                  <span className="mt-0.5 inline-flex size-4 shrink-0 items-center justify-center rounded-full bg-brand/15 text-brand">
                    <CheckIcon className="size-2.5" aria-hidden />
                  </span>
                  <span className="text-muted-foreground">{f}</span>
                </li>
              ))}
            </ul>
          </BezelCard>
        </StaggerItem>
      ))}
    </Stagger>
  );
}
