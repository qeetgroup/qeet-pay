import {
  Badge,
  Button,
  Card,
  Checkbox,
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
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  TimeSince,
  cn,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { BotIcon, KeyRoundIcon, WrenchIcon } from "lucide-react";
import { useState } from "react";

import { ListToolbar } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import { DetailRow, FormError, MoneyField, ShortId, TextField } from "@/features/finance/shared";
import { FormSheet } from "@/features/gst/ui";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/agentic")({ component: AgenticPage });

type MandateView = {
  id: string;
  agentId: string;
  label: string | null;
  maxTxnMinor: number;
  cumulativeCapMinor: number;
  spentMinor: number;
  remainingMinor: number;
  allowedOperations: string[];
  allowedPayees: string[];
  validFrom: string | null;
  expiresAt: string | null;
  status: string;
  createdAt: string;
  revokedAt: string | null;
};
type UseView = {
  id: string;
  operation: string;
  payeeRef: string | null;
  amountMinor: number;
  allowed: boolean;
  reason: string | null;
  createdAt: string;
};
type MandateDetail = { mandate: MandateView; uses: UseView[] };

type McpTool = {
  name: string;
  description: string;
  requiredScope: string;
  inputSchema: Record<string, unknown>;
};
type McpManifest = {
  protocolVersion: string;
  server: string;
  version: string;
  description: string;
  tools: McpTool[];
};

function mandateTone(status: string): "success" | "muted" | "destructive" | "secondary" {
  switch (status) {
    case "ACTIVE":
      return "success";
    case "EXPIRED":
    case "EXHAUSTED":
      return "muted";
    case "REVOKED":
      return "destructive";
    default:
      return "secondary";
  }
}

function AgenticPage() {
  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Scoped, capped, revocable authority for AI agents — issue a mandate and inspect its deterministic ALLOW/DENY decision trail. The MCP manifest is the vocabulary a mandate may allowlist." />

      <Tabs defaultValue="mandates">
        <TabsList>
          <TabsTrigger value="mandates">
            <KeyRoundIcon /> Mandates
          </TabsTrigger>
          <TabsTrigger value="mcp">
            <WrenchIcon /> MCP tools
          </TabsTrigger>
        </TabsList>
        <TabsContent value="mandates" className="mt-4">
          <MandatesTab />
        </TabsContent>
        <TabsContent value="mcp" className="mt-4">
          <McpTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function MandatesTab() {
  const [issueOpen, setIssueOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);

  const listQ = useQuery({
    queryKey: ["agentic-mandates"],
    queryFn: () => api<MandateView[]>("/v1/agentic/mandates"),
    staleTime: 15_000,
  });

  const rows = listQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (m) => [m.id, m.agentId, m.label, m.status],
    filterFields: { status: (m) => m.status },
    sortFields: { spent: (m) => m.spentMinor, created: (m) => m.createdAt },
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Button onClick={() => setIssueOpen(true)}>Issue mandate</Button>
      </div>

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search agent, label…"
          filters={[
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: [
                { label: "Active", value: "ACTIVE" },
                { label: "Revoked", value: "REVOKED" },
                { label: "Expired", value: "EXPIRED" },
                { label: "Exhausted", value: "EXHAUSTED" },
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
              ? exportToCsv("agentic-mandates", lv.view, [
                  { header: "Mandate", value: (m: MandateView) => m.id },
                  { header: "Agent", value: (m: MandateView) => m.agentId },
                  { header: "Per-txn cap", value: (m: MandateView) => m.maxTxnMinor },
                  { header: "Cumulative cap", value: (m: MandateView) => m.cumulativeCapMinor },
                  { header: "Spent", value: (m: MandateView) => m.spentMinor },
                  { header: "Status", value: (m: MandateView) => m.status },
                ])
              : exportToJson("agentic-mandates", lv.view)
          }
        />
        <DataState
          isLoading={listQ.isLoading}
          isError={listQ.isError}
          error={listQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={BotIcon}
          emptyTitle="No agent mandates"
          emptyDescription="Issue a scoped mandate to authorize an AI agent to act within caps."
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Agent</TableHead>
                <TableHead className="text-end">Per-txn cap</TableHead>
                <TableHead>Cumulative usage</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created</TableHead>
                <TableHead className="text-end">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((m) => {
                const usedPct = m.cumulativeCapMinor > 0 ? (m.spentMinor / m.cumulativeCapMinor) * 100 : 0;
                return (
                  <TableRow key={m.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                    <TableCell>
                      <div className="font-medium">{m.label || m.agentId}</div>
                      <div className="font-mono text-xs text-muted-foreground">{m.agentId}</div>
                    </TableCell>
                    <TableCell className="text-end tabular-nums">{formatInr(m.maxTxnMinor)}</TableCell>
                    <TableCell className="w-56">
                      <div className="flex items-center justify-between text-xs tabular-nums text-muted-foreground">
                        <span>{formatInr(m.spentMinor)}</span>
                        <span>{formatInr(m.cumulativeCapMinor)}</span>
                      </div>
                      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-muted">
                        <div
                          className={cn("h-full rounded-full", usedPct >= 90 ? "bg-destructive" : "bg-primary")}
                          style={{ width: `${Math.min(100, usedPct)}%` }}
                        />
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant={mandateTone(m.status)}>{m.status}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      <TimeSince value={m.createdAt} />
                    </TableCell>
                    <TableCell className="text-end">
                      <Button variant="outline" size="sm" onClick={() => setDetailId(m.id)}>
                        Manage
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </DataState>
      </Card>

      <IssueMandateSheet open={issueOpen} onOpenChange={setIssueOpen} />
      <MandateDetailSheet mandateId={detailId} onClose={() => setDetailId(null)} />
    </div>
  );
}

function IssueMandateSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const [agentId, setAgentId] = useState("");
  const [label, setLabel] = useState("");
  const [maxTxn, setMaxTxn] = useState("");
  const [cap, setCap] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [operations, setOperations] = useState<Set<string>>(new Set());
  const [payees, setPayees] = useState("");

  const manifestQ = useQuery({
    queryKey: ["mcp-manifest"],
    queryFn: () => api<McpManifest>("/v1/agentic/mcp/manifest"),
    enabled: open,
    staleTime: 300_000,
  });

  const maxMinor = rupeesToMinor(maxTxn);
  const capMinor = rupeesToMinor(cap);
  const valid = agentId.trim() !== "" && maxMinor !== null && maxMinor > 0 && capMinor !== null && capMinor > 0;

  const mut = useMutation({
    mutationFn: () =>
      api<MandateView>("/v1/agentic/mandates", {
        method: "POST",
        body: {
          agentId: agentId.trim(),
          label: label.trim() || undefined,
          maxTxnMinor: maxMinor,
          cumulativeCapMinor: capMinor,
          allowedOperations: operations.size > 0 ? Array.from(operations) : undefined,
          allowedPayees: payees.trim() ? payees.split(",").map((p) => p.trim()).filter(Boolean) : undefined,
          expiresAt: expiresAt ? new Date(`${expiresAt}T23:59:59.999`).toISOString() : undefined,
        },
      }),
    meta: { successMessage: "Mandate issued" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["agentic-mandates"] });
      onOpenChange(false);
      setAgentId("");
      setLabel("");
      setMaxTxn("");
      setCap("");
      setExpiresAt("");
      setOperations(new Set());
      setPayees("");
    },
  });

  function toggleOp(name: string) {
    setOperations((prev) => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  }

  return (
    <FormSheet
      open={open}
      onOpenChange={onOpenChange}
      title="Issue agent mandate"
      description="Grant a scoped, capped, revocable authority. The agent's actions are checked against these caps and the allowlisted operations."
      submitLabel="Issue mandate"
      submitting={mut.isPending}
      disabled={!valid}
      onSubmit={() => mut.mutate()}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="am-agent" label="Agent ID" value={agentId} onChange={setAgentId} placeholder="agent_treasury_bot" required />
        <TextField id="am-label" label="Label" value={label} onChange={setLabel} placeholder="Treasury sweep bot" />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <MoneyField id="am-max" label="Per-transaction cap" value={maxTxn} onChange={setMaxTxn} required />
        <MoneyField id="am-cap" label="Cumulative cap" value={cap} onChange={setCap} required />
      </div>
      <TextField id="am-exp" label="Expires at" type="date" value={expiresAt} onChange={setExpiresAt} />

      <div>
        <p className="mb-2 text-sm font-medium">Allowed operations</p>
        <p className="mb-2 text-xs text-muted-foreground">Drawn from the MCP tool manifest. Leave all unchecked to allow any tool.</p>
        <div className="space-y-1.5">
          {(manifestQ.data?.tools ?? []).map((t) => (
            <label key={t.name} className="flex items-center gap-2 rounded-md border p-2 text-sm">
              <Checkbox checked={operations.has(t.name)} onCheckedChange={() => toggleOp(t.name)} />
              <span className="font-mono text-xs">{t.name}</span>
              <span className="ms-auto text-xs text-muted-foreground">{t.requiredScope}</span>
            </label>
          ))}
        </div>
      </div>

      <TextField
        id="am-payees"
        label="Allowed payees"
        value={payees}
        onChange={setPayees}
        placeholder="Comma-separated refs (optional)"
      />
      <FormError error={mut.error} />
    </FormSheet>
  );
}

function MandateDetailSheet({ mandateId, onClose }: { mandateId: string | null; onClose: () => void }) {
  const qc = useQueryClient();
  const detailQ = useQuery({
    queryKey: ["agentic-mandate", mandateId],
    queryFn: () => api<MandateDetail>(`/v1/agentic/mandates/${mandateId}`),
    enabled: mandateId !== null,
  });

  const revokeM = useMutation({
    mutationFn: () => api<MandateView>(`/v1/agentic/mandates/${mandateId}/revoke`, { method: "POST", body: {} }),
    meta: { successMessage: "Mandate revoked" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["agentic-mandate", mandateId] });
      qc.invalidateQueries({ queryKey: ["agentic-mandates"] });
    },
  });

  const m = detailQ.data?.mandate;
  const uses = detailQ.data?.uses ?? [];

  return (
    <Sheet open={mandateId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>{m ? m.label || m.agentId : "Mandate"}</SheetTitle>
          <SheetDescription>Caps, allowlists, and the deterministic authorize-decision trail.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto px-4">
          <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error}>
            {m && (
              <>
                <div>
                  <DetailRow label="Status" value={<Badge variant={mandateTone(m.status)}>{m.status}</Badge>} />
                  <DetailRow label="Agent" value={<span className="font-mono text-xs">{m.agentId}</span>} />
                  <DetailRow label="Per-transaction cap" value={formatInr(m.maxTxnMinor)} />
                  <DetailRow label="Cumulative cap" value={formatInr(m.cumulativeCapMinor)} />
                  <DetailRow label="Spent" value={formatInr(m.spentMinor)} />
                  <DetailRow label="Remaining" value={formatInr(m.remainingMinor)} />
                  {m.expiresAt && <DetailRow label="Expires" value={<TimeSince value={m.expiresAt} />} />}
                </div>

                {m.allowedOperations.length > 0 && (
                  <div>
                    <p className="mb-1.5 text-sm font-medium">Allowed operations</p>
                    <div className="flex flex-wrap gap-1.5">
                      {m.allowedOperations.map((op) => (
                        <Badge key={op} variant="outline" className="font-mono">
                          {op}
                        </Badge>
                      ))}
                    </div>
                  </div>
                )}

                <div>
                  <p className="mb-2 text-sm font-medium">Decision trail ({uses.length})</p>
                  {uses.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No authorize decisions recorded yet.</p>
                  ) : (
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Operation</TableHead>
                          <TableHead className="text-end">Amount</TableHead>
                          <TableHead>Result</TableHead>
                          <TableHead>When</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {uses.map((u) => (
                          <TableRow key={u.id}>
                            <TableCell className="font-mono text-xs">{u.operation}</TableCell>
                            <TableCell className="text-end tabular-nums">{formatInr(u.amountMinor)}</TableCell>
                            <TableCell>
                              <Badge variant={u.allowed ? "success" : "destructive"}>
                                {u.allowed ? "Allowed" : "Denied"}
                              </Badge>
                            </TableCell>
                            <TableCell className="text-muted-foreground">
                              <TimeSince value={u.createdAt} />
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )}
                </div>
              </>
            )}
          </DataState>
        </div>
        <SheetFooter>
          <Separator className="mb-2" />
          {m?.status === "ACTIVE" && (
            <Button variant="destructive" disabled={revokeM.isPending} onClick={() => revokeM.mutate()}>
              {revokeM.isPending ? "Revoking…" : "Revoke mandate"}
            </Button>
          )}
          <SheetClose render={<Button variant="outline">Close</Button>} />
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}

function McpTab() {
  const manifestQ = useQuery({
    queryKey: ["mcp-manifest"],
    queryFn: () => api<McpManifest>("/v1/agentic/mcp/manifest"),
    staleTime: 300_000,
  });

  const m = manifestQ.data;

  return (
    <SectionCard
      title="MCP tool manifest"
      description={m ? `${m.server} v${m.version} · protocol ${m.protocolVersion}` : "The safe tools an AI agent may call."}
      icon={WrenchIcon}
    >
      <DataState
        isLoading={manifestQ.isLoading}
        isError={manifestQ.isError}
        error={manifestQ.error}
        isEmpty={(m?.tools ?? []).length === 0}
        emptyIcon={WrenchIcon}
        emptyTitle="No tools published"
        emptyDescription="The MCP manifest is empty."
        skeletonRows={4}
      >
        {m && (
          <>
            <p className="mb-4 text-sm text-muted-foreground">{m.description}</p>
            <div className="grid gap-3 md:grid-cols-2">
              {m.tools.map((t) => {
                const schemaProps =
                  ((t.inputSchema?.properties as Record<string, unknown> | undefined) ?? {});
                const keys = Object.keys(schemaProps);
                return (
                  <div key={t.name} className="rounded-lg border p-3">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono text-sm font-medium">{t.name}</span>
                      <Badge variant="secondary">{t.requiredScope}</Badge>
                    </div>
                    <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">{t.description}</p>
                    {keys.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-1">
                        {keys.map((k) => (
                          <span key={k} className="rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground">
                            {k}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </>
        )}
      </DataState>
    </SectionCard>
  );
}
