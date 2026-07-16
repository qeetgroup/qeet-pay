import {
  Badge,
  Button,
  Card,
  CardContent,
  DataState,
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
  Input,
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
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { RouteIcon } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/page-header";
import { api } from "@/lib/api";
import { formatBps } from "@/lib/money";

export const Route = createFileRoute("/_app/orchestration")({ component: OrchestrationPage });

type ProviderHealth = "HEALTHY" | "DEGRADED" | "DOWN";

type ScorecardView = {
  provider: string;
  attempts: number;
  successes: number;
  failures: number;
  authRate: number;
  consecutiveFailures: number;
  costBps: number;
  health: string;
  lastOutcomeAt: string | null;
};

function healthVariant(health: string) {
  switch (health as ProviderHealth) {
    case "HEALTHY":
      return "success" as const;
    case "DEGRADED":
      return "warning" as const;
    case "DOWN":
      return "destructive" as const;
    default:
      return "secondary" as const;
  }
}

function OrchestrationPage() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<ScorecardView | null>(null);
  const [costBps, setCostBps] = useState("");
  const [costError, setCostError] = useState<string | null>(null);

  const cardsQ = useQuery({
    queryKey: ["provider-scorecards"],
    queryFn: () => api<ScorecardView[]>("/v1/payments/providers/scorecards"),
    staleTime: 15_000,
  });

  const rows = cardsQ.data ?? [];

  const setCostM = useMutation({
    mutationFn: (vars: { provider: string; costBps: number }) =>
      api<ScorecardView>(`/v1/payments/providers/${vars.provider}/cost`, {
        method: "PUT",
        body: { costBps: vars.costBps },
      }),
    meta: { successMessage: "Provider cost updated" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["provider-scorecards"] });
      setEditing(null);
    },
  });

  function openEditor(card: ScorecardView) {
    setEditing(card);
    setCostBps(String(card.costBps));
    setCostError(null);
  }

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!editing) return;
    const n = Number(costBps);
    if (!Number.isInteger(n) || n < 0 || n > 10_000) {
      setCostError("Cost must be a whole number of basis points between 0 and 10000.");
      return;
    }
    setCostError(null);
    setCostM.mutate({ provider: editing.provider, costBps: n });
  }

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Per-provider routing scorecards. Smart routing favours the highest auth rate at the lowest cost, skipping unhealthy acquirers." />

      <Card className="overflow-hidden p-0">
        <CardContent className="p-0">
          <DataState
            isLoading={cardsQ.isLoading}
            isError={cardsQ.isError}
            error={cardsQ.error}
            isEmpty={rows.length === 0}
            emptyIcon={RouteIcon}
            emptyTitle="No provider data yet"
            emptyDescription="Scorecards appear once payments have been routed through a provider."
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Provider</TableHead>
                  <TableHead>Health</TableHead>
                  <TableHead className="text-right">Auth Rate</TableHead>
                  <TableHead className="text-right">Attempts</TableHead>
                  <TableHead className="text-right">Successes</TableHead>
                  <TableHead className="text-right">Failures</TableHead>
                  <TableHead className="text-right">Cost</TableHead>
                  <TableHead>Last Outcome</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((c) => (
                  <TableRow key={c.provider}>
                    <TableCell className="font-medium">{c.provider}</TableCell>
                    <TableCell>
                      <Badge variant={healthVariant(c.health)}>{c.health}</Badge>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {(c.authRate * 100).toFixed(1)}%
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {c.attempts.toLocaleString("en-IN")}
                    </TableCell>
                    <TableCell className="text-right tabular-nums text-green-600 dark:text-green-400">
                      {c.successes.toLocaleString("en-IN")}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {c.failures.toLocaleString("en-IN")}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{formatBps(c.costBps)}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {c.lastOutcomeAt ? <TimeSince value={c.lastOutcomeAt} /> : "—"}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button size="sm" variant="outline" onClick={() => openEditor(c)}>
                        Set cost
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </CardContent>
      </Card>

      <Sheet open={editing !== null} onOpenChange={(o) => !o && setEditing(null)}>
        <SheetContent side="right" className="w-full sm:max-w-md">
          <form onSubmit={submit} className="flex h-full flex-col">
            <SheetHeader>
              <SheetTitle>Set provider cost</SheetTitle>
              <SheetDescription>
                {editing
                  ? `Cost basis used when routing ${editing.provider}.`
                  : "Cost basis used when routing."}
              </SheetDescription>
            </SheetHeader>

            <FieldGroup className="flex-1 overflow-y-auto p-4">
              <Field>
                <FieldLabel htmlFor="cost-bps">Cost (basis points)</FieldLabel>
                <Input
                  id="cost-bps"
                  type="number"
                  min="0"
                  max="10000"
                  step="1"
                  inputMode="numeric"
                  value={costBps}
                  onChange={(e) => setCostBps(e.target.value)}
                  placeholder="e.g. 200"
                />
                <FieldDescription>
                  100 bps = 1%. Current preview: {formatBps(Number(costBps) || 0)}.
                </FieldDescription>
                <FieldError errors={costError ? [{ message: costError }] : undefined} />
              </Field>
            </FieldGroup>

            <SheetFooter>
              <Button type="submit" disabled={setCostM.isPending}>
                {setCostM.isPending ? "Saving…" : "Save cost"}
              </Button>
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>
    </div>
  );
}
