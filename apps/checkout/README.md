# Qeet Pay — Hosted Checkout (`@qeet-pay/checkout`)

The public, payer-facing web app for Qeet Pay **payment links**. A customer opens
a shared link — `…/l/{code}` — sees who they're paying and how much, picks a
method (UPI / Card / Net Banking / Wallet), and pays. No account, no sign-in.

It is the payments sibling of the [Qeet ID hosted login](../../../qeet-hosted/qeet-id-login/)
and mirrors it exactly: **Next.js 16 App Router · React 19 · `@qeetrix/ui` ·
Tailwind v4 · i18next**, the same `page.tsx` (server) + `*-form.tsx` (client)
split, and the same shell / card / form-alert component shapes.

## Stack

- **Next.js 16** App Router, **React 19** (React Compiler on)
- **`@qeetrix/ui`** design system (components, tokens, Qeet brand)
- **Tailwind v4** via `@tailwindcss/postcss`
- **i18next / react-i18next** (`en`, `common` + `checkout` namespaces)

## Develop

```bash
pnpm install
cp .env.example .env      # NEXT_PUBLIC_API_URL=http://localhost:4201
pnpm dev                  # http://localhost:3200
pnpm typecheck            # tsc --noEmit
pnpm build                # next build
```

Runs on **port 3200** (front-ends on 30xx; the operator console is 3201).

## Backend contract

Talks to the Qeet Pay backend (`NEXT_PUBLIC_API_URL`, default
`http://localhost:4201`) over a **public, unauthenticated** API — the link
`code` in the URL is the capability, so there is no API key, cookie, or CSRF
token. Errors are RFC-7807 `problem+json`, surfaced as `ApiError`.

- `GET /v1/checkout/{code}` → `{ code, title, amountMinor, currency, status, merchantName, expiresAt }`
  (`amountMinor: null` = open, payer-entered amount; `404` = not found).
- `POST /v1/checkout/{code}/pay` — body `{ method, amountMinor?, customerName?, customerEmail? }`
  → `{ code, status, paid }`.

**Money is always integer minor units (paise).** Render with `formatMoney`;
convert a typed rupee amount once, at the input, with `toMinor` (`src/lib/money.ts`).

## Layout

```
src/
  app/
    layout.tsx            root layout — ThemeProvider + i18n provider + metadata
    globals.css           @qeetrix/ui tokens + the split-screen shell's own layer
    page.tsx              landing — enter a payment code (fallback for a stray visit)
    l/[code]/
      page.tsx            server — fetches GET /v1/checkout/{code}
      checkout-form.tsx   client — pay form, receipt, terminal-status & not-found states
  components/
    checkout-shell.tsx    split-screen branded frame (mirrors login's auth-shell)
    checkout-card.tsx     centered logo + title card       (mirrors auth-card)
    payment-method-select.tsx   UPI / Card / Net Banking / Wallet radio-card grid
    pay-summary.tsx       the description + amount headline block
    form-alert.tsx        inline danger banner              (copied from login)
  lib/
    api.ts                public fetch client — apiGet / apiPost + ApiError (RFC-7807)
    money.ts              formatMoney (paise → ₹) + toMinor
    branding.ts           Qeet Pay brand + brandingVars (design-token overrides)
  i18n/                   i18next setup + en/{common,checkout}.json
```

Product docs live in **qeet-docs** (`docs.qeet.in/pay`), not here.
