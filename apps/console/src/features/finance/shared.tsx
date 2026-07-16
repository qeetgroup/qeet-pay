import {
  Badge,
  Field,
  FieldDescription,
  FieldError,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@qeetrix/ui";
import type { ComponentType, ReactNode } from "react";

// Shared building blocks for the Embedded-Finance console pages (lending,
// BNPL, cards, insurance, escrow). Kept intentionally small — page-specific
// tables and forms live in their own route files.

// ── Status badges ─────────────────────────────────────────────────────────

type BadgeVariant =
  | "default"
  | "success"
  | "warning"
  | "outline"
  | "secondary"
  | "destructive"
  | "muted";

// One mapping covers every enum across the finance modules — states that mean
// "money is live / done well" are success, "waiting on a human/schedule" are
// warning, terminal-but-fine are muted, terminal-and-bad are destructive.
const STATUS_TONE: Record<string, BadgeVariant> = {
  ACTIVE: "success",
  ACCEPTED: "success",
  REPAID: "success",
  SETTLED: "success",
  PAID: "success",
  APPROVED: "success",
  RELEASED: "success",
  HELD: "warning",
  FROZEN: "warning",
  FILED: "warning",
  PENDING: "warning",
  OFFERED: "outline",
  REFUNDED: "secondary",
  EXPIRED: "muted",
  CLOSED: "muted",
  CANCELLED: "muted",
  DECLINED: "muted",
  WRITTEN_OFF: "destructive",
  REJECTED: "destructive",
};

export function StatusBadge({ status }: { status: string }) {
  return <Badge variant={STATUS_TONE[status] ?? "outline"}>{status.replaceAll("_", " ")}</Badge>;
}

// ── Currency ────────────────────────────────────────────────────────────────

export const CURRENCIES = ["INR", "USD", "EUR", "GBP", "SGD", "AED"] as const;

export function CurrencySelect({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <Field>
      <FieldLabel htmlFor="currency">Currency</FieldLabel>
      <Select value={value} onValueChange={(v) => v && onChange(v)}>
        <SelectTrigger id="currency" className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {CURRENCIES.map((c) => (
            <SelectItem key={c} value={c}>
              {c}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </Field>
  );
}

// ── Form fields ───────────────────────────────────────────────────────────

export function TextField({
  id,
  label,
  value,
  onChange,
  placeholder,
  description,
  type = "text",
  inputMode,
  required,
  error,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  description?: string;
  type?: string;
  inputMode?: "text" | "numeric" | "decimal";
  required?: boolean;
  error?: string;
}) {
  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Input
        id={id}
        type={type}
        inputMode={inputMode}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        required={required}
      />
      {description && <FieldDescription>{description}</FieldDescription>}
      {error && <FieldError errors={[{ message: error }]} />}
    </Field>
  );
}

/** Rupee-denominated amount input. Collects rupees; callers convert with `rupeesToMinor`. */
export function MoneyField({
  id,
  label,
  value,
  onChange,
  description,
  required,
  error,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  description?: string;
  required?: boolean;
  error?: string;
}) {
  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <div className="relative">
        <span className="pointer-events-none absolute inset-s-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">
          ₹
        </span>
        <Input
          id={id}
          inputMode="decimal"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="0.00"
          className="ps-7"
          required={required}
        />
      </div>
      {description && <FieldDescription>{description}</FieldDescription>}
      {error && <FieldError errors={[{ message: error }]} />}
    </Field>
  );
}

export function SelectField({
  id,
  label,
  value,
  onChange,
  options,
  description,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { label: string; value: string }[];
  description?: string;
}) {
  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Select value={value} onValueChange={(v) => v && onChange(v)}>
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((o) => (
            <SelectItem key={o.value} value={o.value}>
              {o.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {description && <FieldDescription>{description}</FieldDescription>}
    </Field>
  );
}

// ── Misc helpers ────────────────────────────────────────────────────────────

/** First 8 chars of a UUID, monospaced — enough to eyeball a row. */
export function ShortId({ id }: { id: string }) {
  return <span className="font-mono text-xs text-muted-foreground">{id.slice(0, 8)}</span>;
}

/** Label / value row used in the detail sheets. */
export function DetailRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 py-1 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right font-medium tabular-nums">{value}</span>
    </div>
  );
}

/** Inline error banner for a form Sheet — surfaces 400/422 problems the global toast skips. */
export function FormError({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof Error ? error.message : "Something went wrong.";
  return (
    <p className="rounded-md bg-destructive/10 p-2.5 text-sm text-destructive">{message}</p>
  );
}

/** Today as an ISO date (YYYY-MM-DD) for date-input defaults. */
export function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export type Icon = ComponentType<{ className?: string }>;
