import {
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  DataState,
  Field,
  FieldError,
  FieldLabel,
  Input,
  Sheet,
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
import { Link, createFileRoute } from "@tanstack/react-router";
import {
  CheckIcon,
  PlusIcon,
  RefreshCwIcon,
  SparklesIcon,
  XIcon,
} from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ListToolbar } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";
import { type CsvColumn, exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";

export const Route = createFileRoute("/_app/dunning")({ component: DunningPage });

// ── Types (mirrors dunning/DunningController) ─────────────────────────────────

type RuleView = {
  id: string;
  name: string;
  failureCodePattern: string | null;
  retryIntervalHours: number;
  maxAttempts: number;
  notifyChannels: string | null;
  active: boolean;
};

type ClassificationView = {
  category: string;
  retryable: boolean;
  recommendedDelayHours: number;
  recommendedChannels: string;
  rationale: string;
};

type AttemptView = {
  id: string;
  subscriptionId: string;
  attemptNumber: number;
  scheduledAt: string | null;
  attemptedAt: string | null;
  result: string | null;
  failureReason: string | null;
  failureCategory: string | null;
  recommendedDelayHours: number | null;
  recommendedChannels: string | null;
  classificationRationale: string | null;
};

type BadgeVariant =
  | "default"
  | "success"
  | "warning"
  | "outline"
  | "secondary"
  | "destructive"
  | "muted";

const CATEGORY_VARIANT: Record<string, BadgeVariant> = {
  INSUFFICIENT_FUNDS: "warning",
  LIMIT_EXCEEDED: "warning",
  TECHNICAL_DECLINE: "secondary",
  RISK_DECLINE: "destructive",
  MANDATE_ISSUE: "destructive",
  CUSTOMER_ACTION: "warning",
  UNKNOWN: "muted",
};

const EXAMPLE_CODES = ["U30", "U69", "insufficient_funds", "limit_exceeded", "risk_declined"];

const EMPTY_RULE = {
  name: "",
  failureCodePattern: "",
  retryIntervalHours: "24",
  maxAttempts: "3",
  notifyChannels: "EMAIL,SMS",
};

function DunningPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_RULE });
  const [ruleError, setRuleError] = useState<string | null>(null);
  const [failureCode, setFailureCode] = useState("");
  const [subId, setSubId] = useState("");
  const [loadedSubId, setLoadedSubId] = useState<string | null>(null);

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const rulesQ = useQuery({
    queryKey: ["dunning-rules"],
    queryFn: () => api<RuleView[]>("/v1/dunning/rules"),
    staleTime: 15_000,
  });

  const createM = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      api<RuleView>("/v1/dunning/rules", { method: "POST", body }),
    onSuccess: (r) => {
      toast.success(`Rule "${r.name}" created`);
      setOpen(false);
      setForm({ ...EMPTY_RULE });
      qc.invalidateQueries({ queryKey: ["dunning-rules"] });
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Failed to create rule"),
  });

  const classifyM = useMutation({
    mutationFn: (code: string) =>
      api<ClassificationView>("/v1/dunning/classify", {
        method: "POST",
        body: { failureCode: code },
      }),
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Classification failed"),
  });

  const attemptsM = useMutation({
    mutationFn: (id: string) =>
      api<AttemptView[]>(`/v1/dunning/subscriptions/${id}/attempts`),
    onSuccess: (_data, id) => setLoadedSubId(id),
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Failed to load attempts"),
  });

  function submitRule(e: React.FormEvent) {
    e.preventDefault();
    const interval = Number(form.retryIntervalHours);
    const attempts = Number(form.maxAttempts);
    if (!Number.isInteger(interval) || interval < 1 || !Number.isInteger(attempts) || attempts < 1) {
      setRuleError("Retry interval and max attempts must be whole numbers of at least 1.");
      return;
    }
    setRuleError(null);
    createM.mutate({
      name: form.name.trim(),
      failureCodePattern: form.failureCodePattern.trim() || undefined,
      retryIntervalHours: interval,
      maxAttempts: attempts,
      notifyChannels: form.notifyChannels.trim() || undefined,
    });
  }

  const lv = useListView(rulesQ.data ?? [], {
    searchFields: (r) => [r.name, r.failureCodePattern, r.notifyChannels],
    filterFields: { active: (r) => (r.active ? "active" : "inactive") },
    sortFields: {
      name: (r) => r.name,
      retryIntervalHours: (r) => r.retryIntervalHours,
      maxAttempts: (r) => r.maxAttempts,
    },
  });

  const columns: CsvColumn<RuleView>[] = [
    { header: "ID", value: (r) => r.id },
    { header: "Name", value: (r) => r.name },
    { header: "Failure pattern", value: (r) => r.failureCodePattern ?? "" },
    { header: "Retry interval (h)", value: (r) => r.retryIntervalHours },
    { header: "Max attempts", value: (r) => r.maxAttempts },
    { header: "Channels", value: (r) => r.notifyChannels ?? "" },
    { header: "Active", value: (r) => r.active },
  ];

  const cls = classifyM.data;

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Retry rules and the AI failure classifier that adapts recovery to each decline."
        actions={
          <Button onClick={() => setOpen(true)}>
            <PlusIcon /> Create rule
          </Button>
        }
      />

      {/* Rules list */}
      <Card className="gap-0 overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search rules…"
          filters={[
            {
              id: "active",
              label: "State",
              value: lv.filters.active ?? "",
              options: [
                { label: "Active", value: "active" },
                { label: "Inactive", value: "inactive" },
              ],
              onChange: (v) => lv.setFilter("active", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("dunning-rules", lv.view, columns)
              : exportToJson("dunning-rules", lv.view)
          }
          exportDisabled={lv.view.length === 0}
        >
          <Button size="sm" onClick={() => setOpen(true)}>
            <PlusIcon /> New rule
          </Button>
        </ListToolbar>

        <DataState
          isLoading={rulesQ.isLoading}
          isError={rulesQ.isError}
          error={rulesQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={RefreshCwIcon}
          emptyTitle="No dunning rules"
          emptyDescription="Add a retry rule to control how failed collections are re-attempted."
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Failure pattern</TableHead>
                  <TableHead className="text-end">Retry interval</TableHead>
                  <TableHead className="text-end">Max attempts</TableHead>
                  <TableHead>Channels</TableHead>
                  <TableHead>State</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((r) => (
                  <TableRow key={r.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                    <TableCell className="font-medium">{r.name}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {r.failureCodePattern ?? "(any)"}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">{r.retryIntervalHours}h</TableCell>
                    <TableCell className="text-end tabular-nums">{r.maxAttempts}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {r.notifyChannels ?? "—"}
                    </TableCell>
                    <TableCell>
                      <Badge variant={r.active ? "success" : "muted"}>
                        {r.active ? "Active" : "Inactive"}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </DataState>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* AI failure classifier */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <SparklesIcon className="size-4 text-primary" /> Classify failure
            </CardTitle>
            <CardDescription>
              Map a raw provider failure code to a recovery strategy — explainable, no state change.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <form
              className="flex items-end gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                if (failureCode.trim()) classifyM.mutate(failureCode.trim());
              }}
            >
              <Field className="flex-1">
                <FieldLabel htmlFor="fail-code">Failure code</FieldLabel>
                <Input
                  id="fail-code"
                  value={failureCode}
                  onChange={(e) => setFailureCode(e.target.value)}
                  placeholder="U30"
                />
              </Field>
              <Button type="submit" disabled={!failureCode.trim() || classifyM.isPending}>
                {classifyM.isPending ? "Classifying…" : "Classify"}
              </Button>
            </form>

            <div className="flex flex-wrap gap-1.5">
              {EXAMPLE_CODES.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => {
                    setFailureCode(c);
                    classifyM.mutate(c);
                  }}
                  className="rounded-full border px-2.5 py-0.5 font-mono text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                >
                  {c}
                </button>
              ))}
            </div>

            {cls && (
              <div className="space-y-3 rounded-lg border bg-muted/30 p-4">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant={CATEGORY_VARIANT[cls.category] ?? "muted"}>
                    {cls.category.replace(/_/g, " ")}
                  </Badge>
                  <Badge variant={cls.retryable ? "success" : "destructive"}>
                    {cls.retryable ? (
                      <>
                        <CheckIcon /> retryable
                      </>
                    ) : (
                      <>
                        <XIcon /> do not retry
                      </>
                    )}
                  </Badge>
                </div>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <p className="text-xs text-muted-foreground">Recommended delay</p>
                    <p className="font-medium tabular-nums">{cls.recommendedDelayHours}h</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Channels</p>
                    <p className="font-medium">{cls.recommendedChannels || "—"}</p>
                  </div>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Rationale</p>
                  <p className="text-sm">{cls.rationale}</p>
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Attempts viewer */}
        <Card>
          <CardHeader>
            <CardTitle>Subscription attempts</CardTitle>
            <CardDescription>
              Dunning attempt history for a{" "}
              <Link to={"/subscriptions" as never} className="underline underline-offset-2">
                subscription
              </Link>
              .
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <form
              className="flex items-end gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                if (subId.trim()) attemptsM.mutate(subId.trim());
              }}
            >
              <Field className="flex-1">
                <FieldLabel htmlFor="sub-id">Subscription ID</FieldLabel>
                <Input
                  id="sub-id"
                  value={subId}
                  onChange={(e) => setSubId(e.target.value)}
                  placeholder="00000000-0000-0000-0000-000000000000"
                  className="font-mono"
                />
              </Field>
              <Button type="submit" variant="outline" disabled={!subId.trim() || attemptsM.isPending}>
                {attemptsM.isPending ? "Loading…" : "Load"}
              </Button>
            </form>

            {loadedSubId && (
              <DataState
                isLoading={attemptsM.isPending}
                isEmpty={(attemptsM.data ?? []).length === 0}
                emptyIcon={RefreshCwIcon}
                emptyTitle="No attempts"
                emptyDescription="This subscription has no recorded dunning attempts."
                skeletonRows={3}
              >
                <div className="space-y-2">
                  {(attemptsM.data ?? []).map((a) => (
                    <div key={a.id} className="rounded-lg border p-3 text-sm">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium">Attempt #{a.attemptNumber}</span>
                        {a.result && (
                          <Badge variant={a.result === "SUCCESS" ? "success" : "destructive"}>
                            {a.result}
                          </Badge>
                        )}
                      </div>
                      <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
                        {a.scheduledAt && (
                          <span>
                            scheduled <TimeSince value={a.scheduledAt} />
                          </span>
                        )}
                        {a.attemptedAt && (
                          <span>
                            attempted <TimeSince value={a.attemptedAt} />
                          </span>
                        )}
                        {a.failureCategory && (
                          <Badge variant={CATEGORY_VARIANT[a.failureCategory] ?? "muted"}>
                            {a.failureCategory.replace(/_/g, " ")}
                          </Badge>
                        )}
                      </div>
                      {a.failureReason && (
                        <p className="mt-1 text-xs">
                          <span className="text-muted-foreground">Reason: </span>
                          {a.failureReason}
                        </p>
                      )}
                      {a.classificationRationale && (
                        <p className="mt-1 text-xs text-muted-foreground">{a.classificationRationale}</p>
                      )}
                      {(a.recommendedDelayHours != null || a.recommendedChannels) && (
                        <p className="mt-1 text-xs text-muted-foreground">
                          {a.recommendedDelayHours != null && <>retry in {a.recommendedDelayHours}h</>}
                          {a.recommendedChannels && <> · via {a.recommendedChannels}</>}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              </DataState>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Create rule */}
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="w-full sm:max-w-md">
          <SheetHeader>
            <SheetTitle>Create dunning rule</SheetTitle>
            <SheetDescription>Control how failed collections are retried.</SheetDescription>
          </SheetHeader>

          <form onSubmit={submitRule} className="flex min-h-0 flex-1 flex-col">
            <div className="flex-1 space-y-4 overflow-y-auto px-4">
              <Field>
                <FieldLabel htmlFor="r-name">Name</FieldLabel>
                <Input
                  id="r-name"
                  required
                  value={form.name}
                  onChange={(e) => set("name", e.target.value)}
                  placeholder="Standard retry"
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="r-pattern">Failure code pattern (optional)</FieldLabel>
                <Input
                  id="r-pattern"
                  value={form.failureCodePattern}
                  onChange={(e) => set("failureCodePattern", e.target.value)}
                  placeholder="U30 (blank = matches any)"
                  className="font-mono"
                />
              </Field>
              <div className="grid grid-cols-2 gap-3">
                <Field>
                  <FieldLabel htmlFor="r-interval">Retry interval (hours)</FieldLabel>
                  <Input
                    id="r-interval"
                    inputMode="numeric"
                    value={form.retryIntervalHours}
                    onChange={(e) => set("retryIntervalHours", e.target.value)}
                    placeholder="24"
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="r-attempts">Max attempts</FieldLabel>
                  <Input
                    id="r-attempts"
                    inputMode="numeric"
                    value={form.maxAttempts}
                    onChange={(e) => set("maxAttempts", e.target.value)}
                    placeholder="3"
                  />
                </Field>
              </div>
              <Field>
                <FieldLabel htmlFor="r-channels">Notify channels</FieldLabel>
                <Input
                  id="r-channels"
                  value={form.notifyChannels}
                  onChange={(e) => set("notifyChannels", e.target.value)}
                  placeholder="EMAIL,SMS,WHATSAPP"
                />
                {ruleError && <FieldError errors={[{ message: ruleError }]} />}
              </Field>
            </div>

            <SheetFooter className="flex-row justify-end gap-2 border-t">
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createM.isPending}>
                {createM.isPending ? "Creating…" : "Create rule"}
              </Button>
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>
    </div>
  );
}
