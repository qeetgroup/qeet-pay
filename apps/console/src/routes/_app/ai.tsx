import {
  Badge,
  Card,
  CardContent,
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
  Button,
} from "@qeetrix/ui";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { GitBranchIcon, ShieldCheckIcon, SparklesIcon } from "lucide-react";
import { useState } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { KpiRow, KpiTile } from "@/components/stat-tile";
import { DetailRow, ShortId } from "@/features/finance/shared";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";

export const Route = createFileRoute("/_app/ai")({ component: AiPage });

type AiDecisionSummary = {
  id: string;
  feature: string;
  model: string;
  confidence: number;
  humanReviewed: boolean;
  fellBack: boolean;
  createdAt: string;
};

type AiDecisionView = AiDecisionSummary & {
  inputHash: string;
  maskedInput: string;
  outputJson: string;
};

type AiHealth = { status: string; model: string; sandbox: boolean };

function pct(confidence: number): string {
  return `${Math.round((confidence ?? 0) * 100)}%`;
}

function AiPage() {
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const healthQ = useQuery({
    queryKey: ["ai-health"],
    queryFn: () => api<AiHealth>("/v1/ai/health"),
  });

  const listQ = useQuery({
    queryKey: ["ai-decisions"],
    queryFn: () => api<AiDecisionSummary[]>("/v1/ai/decisions"),
  });

  const rows = listQ.data ?? [];
  const fallbacks = rows.filter((d) => d.fellBack).length;
  const reviewed = rows.filter((d) => d.humanReviewed).length;
  const lv = useListView(rows, {
    searchFields: (d) => [d.id, d.feature, d.model],
    filterFields: {
      resolution: (d) => (d.fellBack ? "fallback" : "model"),
      review: (d) => (d.humanReviewed ? "reviewed" : "auto"),
    },
    sortFields: {
      confidence: (d) => d.confidence,
      created: (d) => d.createdAt,
    },
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="The audit trail every AI feature writes through the gateway safety matrix (PII masked, human-review gated, fail-closed to a deterministic path)."
        actions={
          healthQ.data ? (
            <Badge variant={healthQ.data.sandbox ? "warning" : "success"}>
              {healthQ.data.sandbox ? "Sandbox" : "Live"} · {healthQ.data.model}
            </Badge>
          ) : null
        }
      />

      <KpiRow cols={3}>
        <KpiTile label="Decisions" value={rows.length.toLocaleString("en-IN")} icon={SparklesIcon} tone="info" loading={listQ.isLoading} />
        <KpiTile label="Deterministic fallbacks" value={fallbacks.toLocaleString("en-IN")} icon={GitBranchIcon} tone={fallbacks > 0 ? "warning" : "neutral"} hint="Fail-closed to a safe path" loading={listQ.isLoading} />
        <KpiTile label="Human-reviewed" value={reviewed.toLocaleString("en-IN")} icon={ShieldCheckIcon} tone="success" loading={listQ.isLoading} />
      </KpiRow>

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search feature, model, id…"
          filters={[
            {
              id: "resolution",
              label: "Resolution",
              value: lv.filters.resolution ?? "",
              options: [
                { label: "Model", value: "model" },
                { label: "Fallback", value: "fallback" },
              ],
              onChange: (v) => lv.setFilter("resolution", v),
            },
            {
              id: "review",
              label: "Review",
              value: lv.filters.review ?? "",
              options: [
                { label: "Human-reviewed", value: "reviewed" },
                { label: "Auto", value: "auto" },
              ],
              onChange: (v) => lv.setFilter("review", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          exportDisabled={lv.view.length === 0}
          onExport={(format) => {
            const cols = [
              { header: "Decision", value: (d: AiDecisionSummary) => d.id },
              { header: "Feature", value: (d: AiDecisionSummary) => d.feature },
              { header: "Model", value: (d: AiDecisionSummary) => d.model },
              { header: "Confidence", value: (d: AiDecisionSummary) => d.confidence },
              { header: "Human reviewed", value: (d: AiDecisionSummary) => d.humanReviewed },
              { header: "Fell back", value: (d: AiDecisionSummary) => d.fellBack },
              { header: "Created", value: (d: AiDecisionSummary) => d.createdAt },
            ];
            if (format === "csv") exportToCsv("ai-decisions", lv.view, cols);
            else exportToJson("ai-decisions", lv.view);
          }}
        />
        <CardContent className="p-0">
          <DataState
            isLoading={listQ.isLoading}
            isError={listQ.isError}
            error={listQ.error}
            isEmpty={lv.view.length === 0}
            emptyIcon={SparklesIcon}
            emptyTitle="No AI decisions yet"
            emptyDescription="AI features record every decision here as they run through the gateway."
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Feature</TableHead>
                  <TableHead>Model</TableHead>
                  <SortHeader columnKey="confidence" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Confidence
                  </SortHeader>
                  <TableHead>Review</TableHead>
                  <TableHead>Resolution</TableHead>
                  <SortHeader columnKey="created" sort={lv.sort} onToggle={lv.toggleSort}>
                    Created
                  </SortHeader>
                  <TableHead className="text-end">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((d) => (
                  <TableRow key={d.id}>
                    <TableCell className="font-medium">{d.feature}</TableCell>
                    <TableCell className="text-muted-foreground">{d.model}</TableCell>
                    <TableCell className="text-end tabular-nums">{pct(d.confidence)}</TableCell>
                    <TableCell>
                      <Badge variant={d.humanReviewed ? "success" : "secondary"}>
                        {d.humanReviewed ? "Reviewed" : "Auto"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={d.fellBack ? "warning" : "outline"}>
                        {d.fellBack ? "Fallback" : "Model"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <TimeSince value={d.createdAt} />
                    </TableCell>
                    <TableCell className="text-end">
                      <Button variant="outline" size="sm" onClick={() => setSelectedId(d.id)}>
                        View
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </CardContent>
      </Card>

      <AiDecisionSheet decisionId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}

function AiDecisionSheet({ decisionId, onClose }: { decisionId: string | null; onClose: () => void }) {
  const detailQ = useQuery({
    queryKey: ["ai-decision", decisionId],
    queryFn: () => api<AiDecisionView>(`/v1/ai/decisions/${decisionId}`),
    enabled: decisionId !== null,
  });

  const d = detailQ.data;

  return (
    <Sheet open={decisionId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Decision {d ? <ShortId id={d.id} /> : ""}</SheetTitle>
          <SheetDescription>Masked input, model/fallback outcome, and audit metadata.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto px-4">
          <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error}>
            {d && (
              <>
                <div>
                  <DetailRow label="Feature" value={d.feature} />
                  <DetailRow label="Model" value={d.model} />
                  <DetailRow label="Confidence" value={pct(d.confidence)} />
                  <DetailRow
                    label="Review"
                    value={
                      <Badge variant={d.humanReviewed ? "success" : "secondary"}>
                        {d.humanReviewed ? "Human-reviewed" : "Auto"}
                      </Badge>
                    }
                  />
                  <DetailRow
                    label="Resolution"
                    value={
                      <Badge variant={d.fellBack ? "warning" : "outline"}>
                        {d.fellBack ? "Deterministic fallback" : "Model result"}
                      </Badge>
                    }
                  />
                  <DetailRow label="Input hash" value={<span className="font-mono text-xs">{d.inputHash}</span>} />
                </div>

                <div>
                  <p className="mb-2 text-sm font-medium">Masked input</p>
                  <pre className="overflow-x-auto rounded-lg border bg-muted/40 p-3 text-xs whitespace-pre-wrap">
                    {d.maskedInput}
                  </pre>
                </div>

                <div>
                  <p className="mb-2 text-sm font-medium">Output</p>
                  <pre className="overflow-x-auto rounded-lg border bg-muted/40 p-3 text-xs whitespace-pre-wrap">
                    {prettyJson(d.outputJson)}
                  </pre>
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

function prettyJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}
