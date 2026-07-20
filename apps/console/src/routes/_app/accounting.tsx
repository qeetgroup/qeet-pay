import {
  Badge,
  Button,
  Card,
  DataState,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Switch,
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
import { BookOpenTextIcon, DownloadIcon, PlugIcon, PlusIcon } from "lucide-react";
import { useState } from "react";

import { ListToolbar } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import {
  FormError,
  SelectField,
  ShortId,
  TextField,
  todayIso,
} from "@/features/finance/shared";
import { FormSheet } from "@/features/gst/ui";
import { StatusBadge } from "@/features/money/status";
import { API_BASE, api, keyStore } from "@/lib/api";
import { downloadBlob, exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";

export const Route = createFileRoute("/_app/accounting")({ component: AccountingPage });

const TARGETS = [
  { value: "TALLY", label: "Tally (XML)" },
  { value: "ZOHO", label: "Zoho Books" },
  { value: "WEBHOOK", label: "Webhook (JSON)" },
];
const TARGET_LABEL: Record<string, string> = {
  TALLY: "Tally",
  ZOHO: "Zoho Books",
  WEBHOOK: "Webhook",
};

type ExportView = {
  id: string;
  target: string;
  periodStart: string;
  periodEnd: string;
  status: string;
  recordCount: number;
  externalRef: string | null;
  detail: string | null;
  createdAt: string;
  completedAt: string | null;
};

type ConnectionView = {
  id: string;
  target: string;
  enabled: boolean;
  webhookUrl: string | null;
  zohoOrganizationId: string | null;
  updatedAt: string;
};

async function downloadExport(id: string) {
  const key = keyStore.get();
  const res = await fetch(`${API_BASE}/v1/accounting/exports/${id}/download`, {
    headers: key ? { "X-Api-Key": key } : {},
  });
  const text = await res.text();
  const isXml = res.headers.get("content-type")?.includes("xml");
  downloadBlob(text, isXml ? "application/xml" : "application/json", `accounting-export-${id.slice(0, 8)}.${isXml ? "xml" : "json"}`);
}

function AccountingPage() {
  const qc = useQueryClient();
  const [runOpen, setRunOpen] = useState(false);
  const [connOpen, setConnOpen] = useState(false);

  const exportsQ = useQuery({
    queryKey: ["accounting-exports"],
    queryFn: () => api<ExportView[]>("/v1/accounting/exports"),
    staleTime: 15_000,
  });
  const connectionsQ = useQuery({
    queryKey: ["accounting-connections"],
    queryFn: () => api<ConnectionView[]>("/v1/accounting/connections"),
    staleTime: 30_000,
  });

  const rows = exportsQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (r) => [r.id, r.target, r.status, r.externalRef],
    filterFields: { target: (r) => r.target, status: (r) => r.status },
    sortFields: { created: (r) => r.createdAt, records: (r) => r.recordCount },
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Export a period's ledger journal entries + GST invoices to Tally, Zoho Books, or a webhook, and manage per-target connections."
        actions={
          <Button onClick={() => setRunOpen(true)}>
            <PlusIcon /> Run export
          </Button>
        }
      />

      <SectionCard
        title="Connections"
        description="Per-target settings used when an export runs."
        icon={PlugIcon}
        action={
          <Button variant="outline" size="sm" onClick={() => setConnOpen(true)}>
            <PlusIcon /> Configure
          </Button>
        }
      >
        <DataState
          isLoading={connectionsQ.isLoading}
          isError={connectionsQ.isError}
          error={connectionsQ.error}
          isEmpty={(connectionsQ.data ?? []).length === 0}
          emptyIcon={PlugIcon}
          emptyTitle="No connections configured"
          emptyDescription="Configure a Tally, Zoho, or webhook target to route exports."
          skeletonRows={2}
        >
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {(connectionsQ.data ?? []).map((c) => (
              <div key={c.id} className="rounded-lg border p-3">
                <div className="flex items-center justify-between">
                  <span className="font-medium">{TARGET_LABEL[c.target] ?? c.target}</span>
                  <Badge variant={c.enabled ? "success" : "muted"}>{c.enabled ? "Enabled" : "Disabled"}</Badge>
                </div>
                <p className="mt-1 truncate text-xs text-muted-foreground">
                  {c.webhookUrl || c.zohoOrganizationId || "Default connection"}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Updated <TimeSince value={c.updatedAt} />
                </p>
              </div>
            ))}
          </div>
        </DataState>
      </SectionCard>

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search exports…"
          filters={[
            {
              id: "target",
              label: "Target",
              value: lv.filters.target ?? "",
              options: TARGETS,
              onChange: (v) => lv.setFilter("target", v),
            },
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: [
                { label: "Completed", value: "COMPLETED" },
                { label: "Pending", value: "PENDING" },
                { label: "Failed", value: "FAILED" },
              ],
              onChange: (v) => lv.setFilter("status", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          exportDisabled={lv.view.length === 0}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("accounting-exports", lv.view, [
                  { header: "Export", value: (r: ExportView) => r.id },
                  { header: "Target", value: (r: ExportView) => r.target },
                  { header: "Period start", value: (r: ExportView) => r.periodStart },
                  { header: "Period end", value: (r: ExportView) => r.periodEnd },
                  { header: "Status", value: (r: ExportView) => r.status },
                  { header: "Records", value: (r: ExportView) => r.recordCount },
                  { header: "External ref", value: (r: ExportView) => r.externalRef ?? "" },
                ])
              : exportToJson("accounting-exports", lv.view)
          }
        />

        <DataState
          isLoading={exportsQ.isLoading}
          isError={exportsQ.isError}
          error={exportsQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={BookOpenTextIcon}
          emptyTitle={lv.hasActiveFilters ? "No matching exports" : "No exports yet"}
          emptyDescription={
            lv.hasActiveFilters
              ? "Try clearing filters or search."
              : "Run an export to push a period's ledger + GST to your accounting system."
          }
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Export</TableHead>
                <TableHead>Target</TableHead>
                <TableHead>Period</TableHead>
                <TableHead className="text-end">Records</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created</TableHead>
                <TableHead className="text-end">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((r) => (
                <TableRow key={r.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                  <TableCell>
                    <ShortId id={r.id} />
                  </TableCell>
                  <TableCell>{TARGET_LABEL[r.target] ?? r.target}</TableCell>
                  <TableCell className="text-xs text-muted-foreground tabular-nums">
                    {r.periodStart.slice(0, 10)} → {r.periodEnd.slice(0, 10)}
                  </TableCell>
                  <TableCell className="text-end tabular-nums">{r.recordCount.toLocaleString("en-IN")}</TableCell>
                  <TableCell>
                    <StatusBadge status={r.status} />
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    <TimeSince value={r.createdAt} />
                  </TableCell>
                  <TableCell className="text-end">
                    <Button variant="outline" size="sm" onClick={() => downloadExport(r.id)}>
                      <DownloadIcon /> Download
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </Card>

      <RunExportSheet open={runOpen} onOpenChange={setRunOpen} onDone={() => qc.invalidateQueries({ queryKey: ["accounting-exports"] })} />
      <ConnectionSheet open={connOpen} onOpenChange={setConnOpen} onDone={() => qc.invalidateQueries({ queryKey: ["accounting-connections"] })} />
    </div>
  );
}

function RunExportSheet({
  open,
  onOpenChange,
  onDone,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  onDone: () => void;
}) {
  const [target, setTarget] = useState("TALLY");
  const [periodStart, setPeriodStart] = useState(todayIso().slice(0, 8) + "01");
  const [periodEnd, setPeriodEnd] = useState(todayIso());

  const mut = useMutation({
    mutationFn: () =>
      api<ExportView>("/v1/accounting/exports", {
        method: "POST",
        body: {
          target,
          periodStart: new Date(`${periodStart}T00:00:00`).toISOString(),
          periodEnd: new Date(`${periodEnd}T23:59:59.999`).toISOString(),
        },
      }),
    meta: { successMessage: "Export run" },
    onSuccess: () => {
      onDone();
      onOpenChange(false);
    },
  });

  return (
    <FormSheet
      open={open}
      onOpenChange={onOpenChange}
      title="Run accounting export"
      description="Aggregates the period's journal entries + GST invoices and pushes them to the selected target."
      submitLabel="Run export"
      submitting={mut.isPending}
      onSubmit={() => mut.mutate()}
    >
      <SelectField id="acc-target" label="Target" value={target} onChange={setTarget} options={TARGETS} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="acc-start" label="Period start" type="date" value={periodStart} onChange={setPeriodStart} />
        <TextField id="acc-end" label="Period end" type="date" value={periodEnd} onChange={setPeriodEnd} />
      </div>
      <FormError error={mut.error} />
    </FormSheet>
  );
}

function ConnectionSheet({
  open,
  onOpenChange,
  onDone,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  onDone: () => void;
}) {
  const [target, setTarget] = useState("WEBHOOK");
  const [enabled, setEnabled] = useState(true);
  const [webhookUrl, setWebhookUrl] = useState("");
  const [zohoOrganizationId, setZohoOrganizationId] = useState("");

  const mut = useMutation({
    mutationFn: () =>
      api<ConnectionView>("/v1/accounting/connections", {
        method: "PUT",
        body: {
          target,
          enabled,
          webhookUrl: webhookUrl.trim() || undefined,
          zohoOrganizationId: zohoOrganizationId.trim() || undefined,
        },
      }),
    meta: { successMessage: "Connection saved" },
    onSuccess: () => {
      onDone();
      onOpenChange(false);
    },
  });

  return (
    <FormSheet
      open={open}
      onOpenChange={onOpenChange}
      title="Configure connection"
      description="Per-target settings used when an export runs against this target."
      submitLabel="Save connection"
      submitting={mut.isPending}
      onSubmit={() => mut.mutate()}
    >
      <SelectField id="conn-target" label="Target" value={target} onChange={setTarget} options={TARGETS} />
      <label className="flex items-center justify-between gap-3 rounded-lg border p-3 text-sm">
        <span>
          <span className="font-medium">Enabled</span>
          <span className="block text-xs text-muted-foreground">Route exports to this target.</span>
        </span>
        <Switch checked={enabled} onCheckedChange={setEnabled} />
      </label>
      {target === "WEBHOOK" && (
        <TextField
          id="conn-webhook"
          label="Webhook URL"
          value={webhookUrl}
          onChange={setWebhookUrl}
          placeholder="https://hooks.example.com/qeet-pay"
        />
      )}
      {target === "ZOHO" && (
        <TextField
          id="conn-zoho"
          label="Zoho organization ID"
          value={zohoOrganizationId}
          onChange={setZohoOrganizationId}
          placeholder="60012345678"
        />
      )}
      <FormError error={mut.error} />
    </FormSheet>
  );
}
