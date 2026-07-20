import { CheckoutShell } from "@/components/checkout-shell";

import { type CheckoutSession, CheckoutForm } from "./checkout-form";

const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:4201";

// Fetched server-side so the first paint already shows the merchant, amount and
// status (no client round-trip, no loading flash). The endpoint is public — the
// link code is the capability — so no auth is sent. A 404 (or any error) yields
// a null session, which the client form renders as a friendly "link not found".
async function fetchCheckout(code: string): Promise<CheckoutSession | null> {
  try {
    const res = await fetch(`${API}/v1/checkout/${encodeURIComponent(code)}`, {
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (!res.ok) return null;
    return (await res.json()) as CheckoutSession;
  } catch {
    return null;
  }
}

export default async function CheckoutPage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  const session = await fetchCheckout(code);
  return (
    <CheckoutShell merchantName={session?.merchantName ?? null}>
      <CheckoutForm code={code} session={session} />
    </CheckoutShell>
  );
}
