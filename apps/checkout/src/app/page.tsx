"use client";

import { Button, Input } from "@qeetrix/ui";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";

import { CheckoutCard } from "@/components/checkout-card";
import { CheckoutShell } from "@/components/checkout-shell";

// The checkout has no index of its own — payers arrive at /l/{code} from a
// shared link. This landing is the fallback for a stray visit: enter a payment
// code and we route to its checkout page.
export default function Home() {
  const { t } = useTranslation("checkout");
  const router = useRouter();
  const [code, setCode] = useState("");

  function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const trimmed = code.trim();
    if (trimmed) router.push(`/l/${encodeURIComponent(trimmed)}`);
  }

  return (
    <CheckoutShell>
      <CheckoutCard title={t("landing.title")} subtitle={t("landing.subtitle")}>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label htmlFor="code" className="text-sm font-medium">
              {t("landing.codeLabel")}
            </label>
            <Input
              id="code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder={t("landing.codePlaceholder")}
              autoComplete="off"
              autoFocus
            />
          </div>
          <Button type="submit" size="lg" className="w-full" disabled={!code.trim()}>
            {t("landing.submit")}
          </Button>
        </form>
        <p className="text-muted-foreground text-center text-xs">{t("landing.hint")}</p>
      </CheckoutCard>
    </CheckoutShell>
  );
}
