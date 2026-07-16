# @qeet-pay/website

The **Qeet Pay marketing site** — `pay.qeet.in`. India-first payments, billing,
and GST infrastructure: one API for payment acceptance (UPI / Cards / NACH /
Net Banking / Wallets), payouts, subscription billing, GST-compliant invoicing
with IRN e-invoicing, payment orchestration, embedded finance, explainable
fraud (XAI), and native financial analytics.

It is a **pure marketing site** — no backend API calls. "Sign in" / "Start"
links point to the operator console; "Docs" / "API reference" point to the
Qeet developer surfaces. Money figures (₹) are display copy sourced from the
product README + PRD.

## Stack

- **Next.js 16** (App Router, React 19, React Compiler)
- **`@qeetrix/ui` ^0.4.0** — the Qeet Group design system (components, tokens,
  Cal Sans / Fira Code brand fonts, `@qeetrix/ui/brand` logos)
- **`motion`** (the framer-motion successor) — reduced-motion-safe animation kit
- **`lucide-react`** — icons
- **Tailwind v4** via `@tailwindcss/postcss`
- **TypeScript** with the `@/*` path alias

Mirrors the qeet-id marketing site (`qeet-id/apps/website`) — the group's
gold-standard for consistency: same `(marketing)` route group, motion approach,
section rhythm, brand layer, and SEO/OG conventions.

## Develop

```bash
pnpm install
pnpm dev          # http://localhost:3202
pnpm typecheck    # tsc --noEmit
pnpm build        # next build
pnpm start        # serve the production build on :3202
```

`pnpm-workspace.yaml` declines sharp's native build (this site never uses
Next's on-the-fly image optimizer — `next.config` sets `images.unoptimized`),
so install + build run prompt-free.

## Structure

```
src/
  app/
    layout.tsx                 # ThemeProvider, site metadata, JSON-LD, theme bootstrap
    globals.css                # @qeetrix/ui styles + Qeet-orange brand layer + motion keyframes
    opengraph-image.tsx        # bespoke homepage OG card (next/og)
    robots.ts · sitemap.ts
    (marketing)/
      layout.tsx               # grain + scroll-progress + header + footer
      page.tsx                 # homepage
      pricing/ product/ compare/ compare/razorpay/ about/ contact/
  components/marketing/
    sections/                  # hero, rails, features, orchestration, stats, embedded-finance,
                               #   compliance, compare, pricing, testimonials, faq, cta
    effects/  motion/  blocks/ # reduced-motion-safe visual + animation primitives
    data/pricing.ts            # single source of truth for tiers
  lib/                         # links, og card builder, reduced-motion hook
```

## Config

Public build-time config only (`NEXT_PUBLIC_*`), see `.env.example`:
`NEXT_PUBLIC_SITE_URL`, `NEXT_PUBLIC_CONSOLE_URL`, `NEXT_PUBLIC_DOCS_URL`,
`NEXT_PUBLIC_API_URL`.
