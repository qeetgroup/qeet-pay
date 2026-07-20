import {
  Badge,
  Button,
  Field,
  FieldDescription,
  FieldLabel,
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@qeetrix/ui";
import type { ReactNode } from "react";

type BadgeVariant =
  | "default"
  | "success"
  | "warning"
  | "outline"
  | "secondary"
  | "destructive"
  | "muted";

// Maps every GST/tax status enum the backend returns (GstInvoiceStatus,
// IrnStatus, GstReturnStatus, ReconStatus) to a Badge tone. Anything unknown
// falls back to a neutral outline.
const STATUS_VARIANTS: Record<string, BadgeVariant> = {
  // GST invoice
  ISSUED: "secondary",
  PAID: "success",
  CANCELLED: "destructive",
  // e-invoice / IRN
  NONE: "muted",
  GENERATED: "success",
  // GST return
  DRAFT: "muted",
  PREPARED: "secondary",
  FILED: "success",
  ERROR: "destructive",
  // ITC reconciliation
  UNMATCHED: "muted",
  MATCHED: "success",
  MISMATCHED: "warning",
  MISSING_IN_2B: "destructive",
};

/** A Badge whose tone is derived from a backend status enum; underscores become spaces. */
export function StatusBadge({ value }: { value: string }) {
  return <Badge variant={STATUS_VARIANTS[value] ?? "outline"}>{value.replace(/_/g, " ")}</Badge>;
}

/** A labelled form control: label on top, control (children), optional hint below. */
export function LabeledField({
  label,
  htmlFor,
  description,
  children,
}: {
  label: ReactNode;
  htmlFor?: string;
  description?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Field>
      <FieldLabel htmlFor={htmlFor}>{label}</FieldLabel>
      {children}
      {description && <FieldDescription>{description}</FieldDescription>}
    </Field>
  );
}

/** A muted-label / bold-value row for the detail + summary panels. */
export function KeyValue({
  label,
  children,
  className,
}: {
  label: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`flex items-center justify-between gap-4 text-sm ${className ?? ""}`}>
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium tabular-nums">{children}</span>
    </div>
  );
}

/** Dashboard-style stat tile reused for the ITC / TDS summary panels. */
export function SummaryStat({
  label,
  value,
  hint,
}: {
  label: ReactNode;
  value: ReactNode;
  hint?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5 rounded-xl bg-card p-4 text-card-foreground ring-1 shadow-rest ring-foreground/10">
      <p className="text-sm font-medium text-muted-foreground">{label}</p>
      <p className="truncate font-heading text-2xl font-semibold tracking-tight tabular-nums">
        {value}
      </p>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

/**
 * The right-hand form Sheet every GST/tax page uses. Owns the header, a
 * scrollable body (children), and a footer with Cancel + submit. The parent
 * controls `open`/`onOpenChange` and supplies `onSubmit`.
 */
export function FormSheet({
  open,
  onOpenChange,
  title,
  description,
  submitLabel,
  submitting,
  disabled,
  onSubmit,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  submitLabel: string;
  submitting?: boolean;
  disabled?: boolean;
  onSubmit: () => void;
  children: ReactNode;
}) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent style={{ maxWidth: "40rem" }}>
        <SheetHeader>
          <SheetTitle>{title}</SheetTitle>
          {description && <SheetDescription>{description}</SheetDescription>}
        </SheetHeader>
        <form
          className="flex min-h-0 flex-1 flex-col"
          onSubmit={(e) => {
            e.preventDefault();
            onSubmit();
          }}
        >
          <div className="flex-1 space-y-4 overflow-y-auto px-4 pb-2">{children}</div>
          <SheetFooter className="flex-row justify-end">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting || disabled}>
              {submitting ? "Saving…" : submitLabel}
            </Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}
