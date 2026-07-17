"use client";

import { Badge, Button, Input, Separator, Spinner } from "@qeetrix/ui";
import { type FormEvent, type ReactNode, useState } from "react";
import { useTranslation } from "react-i18next";

import { CheckoutCard } from "@/components/checkout-card";
import { FormAlert } from "@/components/form-alert";
import { PaySummary } from "@/components/pay-summary";
import { type PaymentMethod, PaymentMethodSelect } from "@/components/payment-method-select";
import { ApiError, apiPost } from "@/lib/api";
import { formatMoney, toMinor } from "@/lib/money";

export type CheckoutStatus = "ACTIVE" | "PAID" | "EXPIRED" | "CANCELLED";

// The public checkout DTO (GET /v1/checkout/{code}). `amountMinor` is null for
// an open, payer-entered amount; every money field is integer minor units.
export type CheckoutSession = {
  code: string;
  title: string;
  amountMinor: number | null;
  currency: string;
  status: CheckoutStatus;
  merchantName: string;
  expiresAt: string | null;
};

type PayResponse = {
  code: string;
  status: string;
  paid: boolean;
};

type CheckoutFormProps = {
  code: string;
  /** Null when the link couldn't be resolved (404 / fetch error). */
  session: CheckoutSession | null;
};

/**
 * The client entry point for a checkout screen. Like the hosted login's
 * login-form, the server page delegates all rendering here so this one client
 * component owns every state: not-found, a terminal link status, the live pay
 * form, and the post-payment receipt.
 */
export function CheckoutForm({ code, session }: CheckoutFormProps) {
  if (!session) return <NotFoundCard />;
  if (session.status !== "ACTIVE") return <StatusCard session={session} />;
  return <PayFlow code={code} session={session} />;
}

function PayFlow({ code, session }: { code: string; session: CheckoutSession }) {
  const { t } = useTranslation("checkout");
  const openAmount = session.amountMinor === null;

  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("UPI");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [receipt, setReceipt] = useState<{ amountMinor: number; method: PaymentMethod } | null>(
    null,
  );

  // The amount actually payable: fixed on the link, or parsed from the input.
  const payable = openAmount ? toMinor(amount) : session.amountMinor;
  const canPay = payable !== null && payable > 0;

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    if (!canPay || payable === null) {
      setError(t("errors.invalidAmount"));
      return;
    }
    setLoading(true);
    try {
      const res = await apiPost<PayResponse>(`/v1/checkout/${encodeURIComponent(code)}/pay`, {
        method,
        ...(openAmount ? { amountMinor: payable } : {}),
        ...(name.trim() ? { customerName: name.trim() } : {}),
        ...(email.trim() ? { customerEmail: email.trim() } : {}),
      });
      if (res.paid || res.status === "PAID") {
        setReceipt({ amountMinor: payable, method });
      } else {
        setError(t("errors.notCompleted"));
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("common:errors.generic"));
    } finally {
      setLoading(false);
    }
  }

  if (receipt) {
    return <ReceiptCard session={session} amountMinor={receipt.amountMinor} method={receipt.method} />;
  }

  return (
    <CheckoutCard title={session.merchantName} subtitle={t("securePaymentVia")}>
      <PaySummary
        title={session.title}
        amountMinor={openAmount ? payable : session.amountMinor}
        currency={session.currency}
        expiresAt={session.expiresAt}
      />

      <form onSubmit={onSubmit} className="space-y-4">
        {openAmount ? (
          <div className="space-y-1.5">
            <label htmlFor="amount" className="text-sm font-medium">
              {t("fields.amount")}
            </label>
            <Input
              id="amount"
              inputMode="decimal"
              autoComplete="off"
              autoFocus
              required
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder={t("fields.amountPlaceholder")}
            />
          </div>
        ) : null}

        <PaymentMethodSelect value={method} onChange={setMethod} disabled={loading} />

        <div className="space-y-1.5">
          <label htmlFor="name" className="flex items-center justify-between text-sm font-medium">
            {t("fields.name")}
            <span className="text-muted-foreground text-xs font-normal">{t("fields.optional")}</span>
          </label>
          <Input
            id="name"
            autoComplete="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t("fields.namePlaceholder")}
          />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="email" className="flex items-center justify-between text-sm font-medium">
            {t("fields.email")}
            <span className="text-muted-foreground text-xs font-normal">{t("fields.optional")}</span>
          </label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder={t("fields.emailPlaceholder")}
          />
        </div>

        <FormAlert>{error}</FormAlert>

        <Button type="submit" size="lg" className="w-full" disabled={loading || !canPay}>
          {loading ? (
            <>
              <Spinner size="sm" className="mr-2" /> {t("submit.busy")}
            </>
          ) : canPay && payable !== null ? (
            t("submit.idle", { amount: formatMoney(payable, session.currency) })
          ) : (
            t("submit.idleOpen")
          )}
        </Button>
      </form>

      <p className="text-muted-foreground text-center text-xs">{t("secured")}</p>
    </CheckoutCard>
  );
}

function ReceiptCard({
  session,
  amountMinor,
  method,
}: {
  session: CheckoutSession;
  amountMinor: number;
  method: PaymentMethod;
}) {
  const { t } = useTranslation("checkout");
  return (
    <CheckoutCard
      title={t("success.title")}
      subtitle={t("success.subtitle", { merchant: session.merchantName })}
    >
      <div className="flex justify-center">
        <span className="bg-success/15 text-success flex size-14 items-center justify-center rounded-full">
          <CheckIcon />
        </span>
      </div>

      <dl className="border-border/60 divide-border/60 divide-y rounded-lg border text-sm">
        <Row label={t("success.amountLabel")}>
          <span className="font-semibold">{formatMoney(amountMinor, session.currency)}</span>
        </Row>
        <Row label={t("success.methodLabel")}>{t(`method.${method}`)}</Row>
        <Row label={t("success.refLabel")}>
          <span className="font-mono text-xs">{session.code}</span>
        </Row>
      </dl>

      <p className="text-muted-foreground text-center text-xs">
        {t("success.note", { merchant: session.merchantName })}
      </p>
    </CheckoutCard>
  );
}

function StatusCard({ session }: { session: CheckoutSession }) {
  const { t } = useTranslation("checkout");
  const status = session.status as Exclude<CheckoutStatus, "ACTIVE">;
  const variant: "success" | "muted" | "destructive" =
    status === "PAID" ? "success" : status === "CANCELLED" ? "destructive" : "muted";
  return (
    <CheckoutCard title={session.merchantName} subtitle={t("securePaymentVia")}>
      <PaySummary
        title={session.title}
        amountMinor={session.amountMinor}
        currency={session.currency}
        expiresAt={session.expiresAt}
      />
      <div className="space-y-3 text-center">
        <div className="flex justify-center">
          <Badge variant={variant}>{t(`status.${status}.badge`)}</Badge>
        </div>
        <Separator />
        <p className="text-base font-medium">{t(`status.${status}.title`)}</p>
        <p className="text-muted-foreground text-sm">
          {t(`status.${status}.body`, { merchant: session.merchantName })}
        </p>
      </div>
    </CheckoutCard>
  );
}

function NotFoundCard() {
  const { t } = useTranslation("checkout");
  return (
    <CheckoutCard title={t("notFound.title")} subtitle={t("notFound.subtitle")}>
      <p className="text-muted-foreground text-center text-sm">{t("notFound.body")}</p>
    </CheckoutCard>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between px-4 py-2.5">
      <dt className="text-muted-foreground">{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function CheckIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-7"
      aria-hidden
    >
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}
