import {
  Badge,
  Button,
  Card,
  DataState,
  Field,
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
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, createFileRoute } from "@tanstack/react-router";
import { BanIcon, PauseIcon, PlayIcon, PlusIcon, RepeatIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";
import { type CsvColumn, exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";

export const Route = createFileRoute("/_app/subscriptions")({ component: SubscriptionsPage });

// ── Types & options (mirrors billing/BillingController SubscriptionView) ──────

type SubscriptionView = {
  id: string;
  planId: string;
  status: string;
  firstInvoiceId: string | null;
  cancelAtPeriodEnd: boolean;
};

type BadgeVariant =
  | "default"
  | "success"
  | "warning"
  | "outline"
  | "secondary"
  | "destructive"
  | "muted";

const STATUSES = ["TRIALING", "ACTIVE", "PAST_DUE", "PAUSED", "CANCELLED"] as const;

const STATUS_VARIANT: Record<string, BadgeVariant> = {
  ACTIVE: "success",
  TRIALING: "secondary",
  PAST_DUE: "warning",
  PAUSED: "outline",
  CANCELLED: "muted",
};

type Action = "pause" | "resume" | "cancel";

function SubscriptionsPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [planId, setPlanId] = useState("");
  const [customerRef, setCustomerRef] = useState("");

  const subsQ = useQuery({
    queryKey: ["subscriptions"],
    queryFn: () => api<SubscriptionView[]>("/v1/subscriptions"),
    staleTime: 15_000,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["subscriptions"] });

  const createM = useMutation({
    mutationFn: (body: { planId: string; customerRef: string }) =>
      api<SubscriptionView>("/v1/subscriptions", { method: "POST", body }),
    onSuccess: () => {
      invalidate();
      toast.success("Subscription created");
      setOpen(false);
      setPlanId("");
      setCustomerRef("");
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Failed to create subscription"),
  });

  const actionM = useMutation({
    mutationFn: ({ id, action }: { id: string; action: Action }) =>
      api<SubscriptionView>(`/v1/subscriptions/${id}/${action}`, {
        method: "POST",
        query: action === "cancel" ? { atPeriodEnd: false } : undefined,
      }),
    onSuccess: (_s, vars) => {
      invalidate();
      toast.success(`Subscription ${vars.action}d`);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Action failed"),
  });

  function submit(e: React.FormEvent) {
    e.preventDefault();
    createM.mutate({ planId: planId.trim(), customerRef: customerRef.trim() });
  }

  const lv = useListView(subsQ.data ?? [], {
    searchFields: (s) => [s.id, s.planId, s.status],
    filterFields: { status: (s) => s.status },
    sortFields: { status: (s) => s.status, planId: (s) => s.planId },
  });

  const columns: CsvColumn<SubscriptionView>[] = [
    { header: "ID", value: (s) => s.id },
    { header: "Plan ID", value: (s) => s.planId },
    { header: "Status", value: (s) => s.status },
    { header: "Cancel at period end", value: (s) => s.cancelAtPeriodEnd },
    { header: "First invoice ID", value: (s) => s.firstInvoiceId ?? "" },
  ];

  const canPause = (s: string) => s === "ACTIVE" || s === "TRIALING" || s === "PAST_DUE";
  const pending = actionM.isPending;

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Customer subscriptions and their lifecycle state."
        actions={
          <Button onClick={() => setOpen(true)}>
            <PlusIcon /> Create subscription
          </Button>
        }
      />

      <Card className="gap-0 overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search subscriptions…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: STATUSES.map((s) => ({ label: s.replace("_", " "), value: s })),
              onChange: (v) => lv.setFilter("status", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("subscriptions", lv.view, columns)
              : exportToJson("subscriptions", lv.view)
          }
          exportDisabled={lv.view.length === 0}
        >
          <Button size="sm" onClick={() => setOpen(true)}>
            <PlusIcon /> New subscription
          </Button>
        </ListToolbar>

        <DataState
          isLoading={subsQ.isLoading}
          isError={subsQ.isError}
          error={subsQ.error}
          isEmpty={lv.view.length === 0}
          empty={
            <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
              <div className="flex size-11 items-center justify-center rounded-full bg-muted text-muted-foreground">
                <RepeatIcon className="size-5" />
              </div>
              <div className="space-y-1">
                <p className="font-heading text-sm font-medium">No subscriptions yet</p>
                <p className="mx-auto max-w-sm text-sm text-muted-foreground">
                  Create a subscription against a{" "}
                  <Link to={"/plans" as never} className="underline underline-offset-2">
                    plan
                  </Link>
                  .
                </p>
              </div>
              <Button size="sm" onClick={() => setOpen(true)}>
                <PlusIcon /> Create subscription
              </Button>
            </div>
          }
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Subscription ID</TableHead>
                  <SortHeader columnKey="planId" sort={lv.sort} onToggle={lv.toggleSort}>
                    Plan ID
                  </SortHeader>
                  <SortHeader columnKey="status" sort={lv.sort} onToggle={lv.toggleSort}>
                    Status
                  </SortHeader>
                  <TableHead>First invoice</TableHead>
                  <TableHead className="text-end">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((s) => (
                  <TableRow key={s.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                    <TableCell className="font-mono text-xs">{s.id}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {s.planId}
                    </TableCell>
                    <TableCell>
                      <Badge variant={STATUS_VARIANT[s.status] ?? "muted"}>
                        {s.status.replace("_", " ")}
                      </Badge>
                      {s.cancelAtPeriodEnd && (
                        <span className="ms-2 text-xs text-muted-foreground">cancels at period end</span>
                      )}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {s.firstInvoiceId ?? "—"}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end gap-1.5">
                        {canPause(s.status) && (
                          <Button
                            size="xs"
                            variant="outline"
                            disabled={pending}
                            onClick={() => actionM.mutate({ id: s.id, action: "pause" })}
                          >
                            <PauseIcon /> Pause
                          </Button>
                        )}
                        {s.status === "PAUSED" && (
                          <Button
                            size="xs"
                            variant="outline"
                            disabled={pending}
                            onClick={() => actionM.mutate({ id: s.id, action: "resume" })}
                          >
                            <PlayIcon /> Resume
                          </Button>
                        )}
                        {s.status !== "CANCELLED" && (
                          <Button
                            size="xs"
                            variant="destructive"
                            disabled={pending}
                            onClick={() => actionM.mutate({ id: s.id, action: "cancel" })}
                          >
                            <BanIcon /> Cancel
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </DataState>
      </Card>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="w-full sm:max-w-md">
          <SheetHeader>
            <SheetTitle>Create subscription</SheetTitle>
            <SheetDescription>Subscribe a customer to a plan.</SheetDescription>
          </SheetHeader>

          <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
            <div className="flex-1 space-y-4 overflow-y-auto px-4">
              <Field>
                <FieldLabel htmlFor="sub-plan">Plan ID</FieldLabel>
                <Input
                  id="sub-plan"
                  required
                  value={planId}
                  onChange={(e) => setPlanId(e.target.value)}
                  placeholder="00000000-0000-0000-0000-000000000000"
                  className="font-mono"
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="sub-customer">Customer reference</FieldLabel>
                <Input
                  id="sub-customer"
                  required
                  value={customerRef}
                  onChange={(e) => setCustomerRef(e.target.value)}
                  placeholder="cust_abc123"
                />
              </Field>
            </div>

            <SheetFooter className="flex-row justify-end gap-2 border-t">
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createM.isPending}>
                {createM.isPending ? "Creating…" : "Create subscription"}
              </Button>
            </SheetFooter>
          </form>
        </SheetContent>
      </Sheet>
    </div>
  );
}
