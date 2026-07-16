import { Badge } from "@qeetrix/ui";

// Maps the qeet-pay domain status enums (payouts, batches, settlements,
// reconciliation, rev-rec) onto Qeetrix Badge variants so every money screen
// colours status the same way. Unknown values fall back to "secondary".
const SUCCESS = new Set(["PAID", "COMPLETED", "RECONCILED", "MATCHED", "CAPTURED", "RECOGNIZED"]);
const WARNING = new Set(["PENDING_APPROVAL", "PARTIALLY_COMPLETED", "IN_PROGRESS"]);
const DESTRUCTIVE = new Set(["FAILED", "DISCREPANCY", "NODAL_IMBALANCE"]);
const MUTED = new Set(["REJECTED", "CANCELLED"]);

export type BadgeVariant =
  | "default"
  | "success"
  | "warning"
  | "outline"
  | "secondary"
  | "destructive"
  | "muted";

export function statusVariant(status: string): BadgeVariant {
  if (SUCCESS.has(status)) return "success";
  if (WARNING.has(status)) return "warning";
  if (DESTRUCTIVE.has(status)) return "destructive";
  if (MUTED.has(status)) return "muted";
  return "secondary";
}

/** A Badge coloured by the shared status → variant mapping. */
export function StatusBadge({ status }: { status: string }) {
  return <Badge variant={statusVariant(status)}>{status}</Badge>;
}
