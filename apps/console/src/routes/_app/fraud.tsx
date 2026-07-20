import {
  Badge,
  Button,
  Card,
  DataState,
  Separator,
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TimeSince,
  cn,
} from "@qeetrix/ui";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
  CheckCircle2Icon,
  FingerprintIcon,
  ShieldXIcon,
  TriangleAlertIcon,
} from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { KpiRow, KpiTile } from "@/components/stat-tile";
import { DetailRow, ShortId } from "@/features/finance/shared";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";

export const Route = createFileRoute("/_app/fraud")({ component: FraudPage });

// Mirrors fraud/FraudController.
type FraudReason = { feature: string; contribution: number; value: number; reason: string };
type DecisionSummary = {
  id: string;
  paymentId: string | null;
  score: number;
  decision: string;
  model: string;
  createdAt: string;
};
type DecisionView = DecisionSummary & { topReasons: FraudReason[]; aiDecisionId: string | null };

const DECISIONS = ["ALLOW", "REVIEW", "BLOCK", "DENY"] as const;

function decisionTone(decision: string): "success" | "warning" | "destructive" | "secondary" {
  switch (decision) {
    case "ALLOW":
      return "success";
    case "REVIEW":
      return "warning";
    case "BLOCK":
    case "DENY":
      return "destructive";
    default:
      return "secondary";
  }
}

function scoreTone(score: number): "success" | "warning" | "danger" {
  if (score >= 70) return "danger";
  if (score >= 40) return "warning";
  return "success";
}

function FraudPage() {
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const listQ = useQuery({
    queryKey: ["fraud-decisions"],
    queryFn: () => api<DecisionSummary[]>("/v1/fraud/decisions"),
    staleTime: 15_000,
  });

  const rows = listQ.data ?? [];
  const blocked = rows.filter((r) => r.decision === "BLOCK" || r.decision === "DENY").length;
  const review = rows.filter((r) => r.decision === "REVIEW").length;
  const avgScore =
    rows.length === 0 ? 0 : Math.round(rows.reduce((s, r) => s + r.score, 0) / rows.length);

  const lv = useListView(rows, {
    searchFields: (d) => [d.id, d.paymentId, d.decision, d.model],
    filterFields: { decision: (d) => d.decision },
    sortFields: { score: (d) => d.score, created: (d) => d.createdAt },
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Every fraud-scoring decision, its verdict, and the Explainable-AI top reasons. Scoring is advisory and fail-open — an outage never blocks a payment." />

      <KpiRow>
        <KpiTile
          label="Decisions scored"
          value={rows.length.toLocaleString("en-IN")}
          icon={FingerprintIcon}
          tone="info"
          loading={listQ.isLoading}
        />
        <KpiTile
          label="Blocked / denied"
          value={blocked.toLocaleString("en-IN")}
          icon={ShieldXIcon}
          tone={blocked > 0 ? "danger" : "neutral"}
          loading={listQ.isLoading}
        />
        <KpiTile
          label="Flagged for review"
          value={review.toLocaleString("en-IN")}
          icon={TriangleAlertIcon}
          tone={review > 0 ? "warning" : "neutral"}
          loading={listQ.isLoading}
        />
        <KpiTile
          label="Average risk score"
          value={`${avgScore}`}
          icon={CheckCircle2Icon}
          tone={scoreTone(avgScore) === "danger" ? "danger" : scoreTone(avgScore) === "warning" ? "warning" : "success"}
          hint="0 (safe) – 100 (risky)"
          loading={listQ.isLoading}
        />
      </KpiRow>

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search decision, payment, model…"
          filters={[
            {
              id: "decision",
              label: "Verdict",
              value: lv.filters.decision ?? "",
              options: DECISIONS.map((d) => ({ label: d, value: d })),
              onChange: (v) => lv.setFilter("decision", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          exportDisabled={lv.view.length === 0}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("fraud-decisions", lv.view, [
                  { header: "Decision", value: (d: DecisionSummary) => d.id },
                  { header: "Payment", value: (d: DecisionSummary) => d.paymentId ?? "" },
                  { header: "Score", value: (d: DecisionSummary) => d.score },
                  { header: "Verdict", value: (d: DecisionSummary) => d.decision },
                  { header: "Model", value: (d: DecisionSummary) => d.model },
                  { header: "Created", value: (d: DecisionSummary) => d.createdAt },
                ])
              : exportToJson("fraud-decisions", lv.view)
          }
        />

        <DataState
          isLoading={listQ.isLoading}
          isError={listQ.isError}
          error={listQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={FingerprintIcon}
          emptyTitle={lv.hasActiveFilters ? "No matching decisions" : "No fraud decisions yet"}
          emptyDescription={
            lv.hasActiveFilters
              ? "Try clearing filters or search."
              : "Each payment scored by the fraud engine is recorded here with its explanation."
          }
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Decision</TableHead>
                <TableHead>Payment</TableHead>
                <SortHeader columnKey="score" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                  Risk score
                </SortHeader>
                <TableHead>Verdict</TableHead>
                <TableHead>Model</TableHead>
                <SortHeader columnKey="created" sort={lv.sort} onToggle={lv.toggleSort}>
                  Scored
                </SortHeader>
                <TableHead className="text-end">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((d) => (
                <TableRow key={d.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                  <TableCell>
                    <ShortId id={d.id} />
                  </TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">
                    {d.paymentId ? d.paymentId.slice(0, 8) : "—"}
                  </TableCell>
                  <TableCell className="text-end">
                    <ScorePill score={d.score} />
                  </TableCell>
                  <TableCell>
                    <Badge variant={decisionTone(d.decision)}>{d.decision}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{d.model}</TableCell>
                  <TableCell className="text-muted-foreground">
                    <TimeSince value={d.createdAt} />
                  </TableCell>
                  <TableCell className="text-end">
                    <Button variant="outline" size="sm" onClick={() => setSelectedId(d.id)}>
                      Explain
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </Card>

      <DecisionSheet decisionId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}

function ScorePill({ score }: { score: number }) {
  const tone = scoreTone(score);
  const cls =
    tone === "danger"
      ? "bg-destructive/10 text-destructive"
      : tone === "warning"
        ? "bg-warning/10 text-warning"
        : "bg-success/10 text-success";
  return (
    <span className={cn("inline-flex min-w-9 justify-center rounded-md px-2 py-0.5 text-sm font-semibold tabular-nums", cls)}>
      {score}
    </span>
  );
}

function DecisionSheet({ decisionId, onClose }: { decisionId: string | null; onClose: () => void }) {
  const detailQ = useQuery({
    queryKey: ["fraud-decision", decisionId],
    queryFn: () => api<DecisionView>(`/v1/fraud/decisions/${decisionId}`),
    enabled: decisionId !== null,
  });

  const d = detailQ.data;
  const maxAbs = d ? Math.max(1, ...d.topReasons.map((r) => Math.abs(r.contribution))) : 1;

  return (
    <Sheet open={decisionId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Decision {d ? <ShortId id={d.id} /> : ""}</SheetTitle>
          <SheetDescription>
            The score, verdict, and the top contributing features (Explainable AI).
          </SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto px-4">
          <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error}>
            {d && (
              <>
                <div className="flex items-center gap-4 rounded-xl bg-muted/40 p-4">
                  <ScorePill score={d.score} />
                  <div className="min-w-0">
                    <Badge variant={decisionTone(d.decision)}>{d.decision}</Badge>
                    <p className="mt-1 text-xs text-muted-foreground">
                      Risk score {d.score} of 100 · {d.model}
                    </p>
                  </div>
                </div>

                <div>
                  <DetailRow
                    label="Payment"
                    value={
                      d.paymentId ? (
                        <span className="font-mono text-xs">{d.paymentId}</span>
                      ) : (
                        "—"
                      )
                    }
                  />
                  <DetailRow
                    label="AI decision"
                    value={
                      d.aiDecisionId ? (
                        <span className="font-mono text-xs">{d.aiDecisionId.slice(0, 8)}</span>
                      ) : (
                        "—"
                      )
                    }
                  />
                </div>

                <div>
                  <p className="mb-3 text-sm font-medium">Top contributing features</p>
                  {d.topReasons.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No explanation was recorded.</p>
                  ) : (
                    <ul className="space-y-3">
                      {d.topReasons.map((r) => {
                        const risk = r.contribution >= 0;
                        const pct = (Math.abs(r.contribution) / maxAbs) * 100;
                        return (
                          <li key={r.feature} className="rounded-lg border p-3">
                            <div className="flex items-center justify-between gap-2">
                              <span className="font-mono text-xs font-medium">{r.feature}</span>
                              <span
                                className={cn(
                                  "text-xs font-semibold tabular-nums",
                                  risk ? "text-destructive" : "text-success",
                                )}
                              >
                                {risk ? "+" : ""}
                                {r.contribution.toFixed(1)}
                              </span>
                            </div>
                            <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted">
                              <div
                                className={cn("h-full rounded-full", risk ? "bg-destructive" : "bg-success")}
                                style={{ width: `${pct}%` }}
                              />
                            </div>
                            <p className="mt-2 text-xs leading-relaxed text-muted-foreground">{r.reason}</p>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              </>
            )}
          </DataState>
        </div>
        <SheetFooter>
          <Separator className="mb-2" />
          <SheetClose render={<Button variant="outline">Close</Button>} />
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
