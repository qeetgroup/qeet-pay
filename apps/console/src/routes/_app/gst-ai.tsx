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
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  Textarea,
  TimeSince,
  cn,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { RadarIcon, SparklesIcon, TagIcon } from "lucide-react";
import { useState } from "react";

import { ListToolbar } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import { FormError, TextField, todayIso } from "@/features/finance/shared";
import { FormSheet } from "@/features/gst/ui";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr } from "@/lib/money";

export const Route = createFileRoute("/_app/gst-ai")({ component: GstAiPage });

type HsnSuggestion = { hsnSac: string; kind: string; gstRate: number; confidence: number; label: string };
type ClassificationResult = {
  decisionId: string;
  model: string;
  source: string;
  fellBack: boolean;
  requiresReview: boolean;
  confidence: number;
  explanation: string;
  suggestions: HsnSuggestion[];
};

type RegChangeView = {
  id: string;
  hsnSac: string;
  changeType: string;
  oldRatePct: number | null;
  newRatePct: number;
  effectiveDate: string;
  title: string;
  source: string | null;
  announcedAt: string;
};

type InvoiceImpact = {
  invoiceId: string;
  invoiceNumber: string;
  taxableMinor: number;
  currentGstMinor: number;
  forecastGstMinor: number;
  deltaGstMinor: number;
};
type RegChangeImpactReport = {
  changeId: string;
  hsnSac: string;
  oldRatePct: number | null;
  newRatePct: number;
  effectiveDate: string;
  forecast: boolean;
  confidence: number;
  disclaimer: string;
  affectedInvoiceCount: number;
  affectedLineCount: number;
  totalTaxableMinor: number;
  currentGstMinor: number;
  forecastGstMinor: number;
  deltaGstMinor: number;
  decisionId: string;
  invoices: InvoiceImpact[];
};

function pct(v: number): string {
  return `${Math.round((v ?? 0) * 100)}%`;
}

function GstAiPage() {
  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="AI-assisted HSN/SAC classification and the Regulatory-Change Impact Radar — both routed through the AI gateway safety matrix with a deterministic fallback." />

      <Tabs defaultValue="classify">
        <TabsList>
          <TabsTrigger value="classify">
            <TagIcon /> HSN / SAC classifier
          </TabsTrigger>
          <TabsTrigger value="radar">
            <RadarIcon /> Regulatory radar
          </TabsTrigger>
        </TabsList>
        <TabsContent value="classify" className="mt-4">
          <ClassifyTab />
        </TabsContent>
        <TabsContent value="radar" className="mt-4">
          <RadarTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function ClassifyTab() {
  const [description, setDescription] = useState("");
  const [result, setResult] = useState<ClassificationResult | null>(null);

  const classifyM = useMutation({
    mutationFn: () =>
      api<ClassificationResult>("/v1/gst/classify", {
        method: "POST",
        body: { description: description.trim() },
      }),
    onSuccess: setResult,
  });

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <SectionCard
        title="Classify a supply"
        description="Describe a good or service; the classifier suggests ranked HSN/SAC codes with GST rates."
        icon={SparklesIcon}
      >
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (description.trim()) classifyM.mutate();
          }}
        >
          <Textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="e.g. Cotton t-shirts, printed, retail packs"
            rows={4}
          />
          <FormError error={classifyM.error} />
          <Button type="submit" disabled={description.trim() === "" || classifyM.isPending}>
            {classifyM.isPending ? "Classifying…" : "Classify"}
          </Button>
        </form>
      </SectionCard>

      <SectionCard title="Suggestions" description="Ranked candidates, highest confidence first.">
        <DataState
          isLoading={classifyM.isPending}
          isEmpty={!result}
          emptyIcon={TagIcon}
          emptyTitle="No classification yet"
          emptyDescription="Describe a supply to get ranked HSN/SAC suggestions."
          skeletonRows={3}
        >
          {result && (
            <div className="space-y-4">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={result.requiresReview ? "warning" : "success"}>
                  {result.requiresReview ? "Review recommended" : "High confidence"}
                </Badge>
                <Badge variant={result.fellBack ? "warning" : "outline"}>
                  {result.fellBack ? "Deterministic fallback" : "Model"}
                </Badge>
                <span className="text-xs text-muted-foreground">
                  {pct(result.confidence)} · {result.model}
                </span>
              </div>
              <p className="rounded-lg bg-muted/50 p-3 text-sm leading-relaxed text-muted-foreground">
                {result.explanation}
              </p>
              <ul className="space-y-2">
                {result.suggestions.map((s, i) => (
                  <li
                    key={s.hsnSac}
                    className={cn(
                      "flex items-center gap-3 rounded-lg border p-3",
                      i === 0 && "border-primary/40 bg-primary/5",
                    )}
                  >
                    <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-muted font-mono text-xs font-semibold">
                      {s.gstRate}%
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="font-mono text-sm font-medium">{s.hsnSac}</p>
                      <p className="truncate text-xs text-muted-foreground">{s.label}</p>
                    </div>
                    <div className="text-end">
                      <Badge variant="secondary">{s.kind}</Badge>
                      <p className="mt-1 text-xs tabular-nums text-muted-foreground">{pct(s.confidence)}</p>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </DataState>
      </SectionCard>
    </div>
  );
}

function RadarTab() {
  const [createOpen, setCreateOpen] = useState(false);
  const [impactId, setImpactId] = useState<string | null>(null);

  const listQ = useQuery({
    queryKey: ["reg-changes"],
    queryFn: () => api<RegChangeView[]>("/v1/gst/reg-changes"),
    staleTime: 30_000,
  });

  const rows = listQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (r) => [r.hsnSac, r.title, r.source],
    filterFields: {},
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Button onClick={() => setCreateOpen(true)}>Record change</Button>
      </div>

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search HSN/SAC, title…"
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          exportDisabled={lv.view.length === 0}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("reg-changes", lv.view, [
                  { header: "HSN/SAC", value: (r: RegChangeView) => r.hsnSac },
                  { header: "Title", value: (r: RegChangeView) => r.title },
                  { header: "Old rate", value: (r: RegChangeView) => r.oldRatePct ?? "" },
                  { header: "New rate", value: (r: RegChangeView) => r.newRatePct },
                  { header: "Effective", value: (r: RegChangeView) => r.effectiveDate },
                ])
              : exportToJson("reg-changes", lv.view)
          }
        />
        <DataState
          isLoading={listQ.isLoading}
          isError={listQ.isError}
          error={listQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={RadarIcon}
          emptyTitle="No regulatory changes tracked"
          emptyDescription="Record an announced GST rate change to forecast its impact on your invoices."
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>HSN / SAC</TableHead>
                <TableHead>Change</TableHead>
                <TableHead className="text-end">Rate</TableHead>
                <TableHead>Effective</TableHead>
                <TableHead>Announced</TableHead>
                <TableHead className="text-end">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((r) => (
                <TableRow key={r.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                  <TableCell className="font-mono text-sm font-medium">{r.hsnSac}</TableCell>
                  <TableCell>
                    <div className="font-medium">{r.title}</div>
                    <div className="text-xs text-muted-foreground">{r.changeType.replaceAll("_", " ")}</div>
                  </TableCell>
                  <TableCell className="text-end tabular-nums">
                    {r.oldRatePct != null ? `${r.oldRatePct}% → ` : ""}
                    <span className="font-medium">{r.newRatePct}%</span>
                  </TableCell>
                  <TableCell className="tabular-nums">{r.effectiveDate}</TableCell>
                  <TableCell className="text-muted-foreground">
                    <TimeSince value={r.announcedAt} />
                  </TableCell>
                  <TableCell className="text-end">
                    <Button variant="outline" size="sm" onClick={() => setImpactId(r.id)}>
                      Forecast impact
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </Card>

      <CreateRegChangeSheet open={createOpen} onOpenChange={setCreateOpen} />
      <ImpactSheet changeId={impactId} onClose={() => setImpactId(null)} />
    </div>
  );
}

function CreateRegChangeSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const [hsnSac, setHsnSac] = useState("");
  const [title, setTitle] = useState("");
  const [oldRatePct, setOldRatePct] = useState("");
  const [newRatePct, setNewRatePct] = useState("");
  const [effectiveDate, setEffectiveDate] = useState(todayIso());
  const [source, setSource] = useState("");

  const mut = useMutation({
    mutationFn: () =>
      api<RegChangeView>("/v1/gst/reg-changes", {
        method: "POST",
        body: {
          hsnSac: hsnSac.trim(),
          oldRatePct: oldRatePct.trim() ? Number(oldRatePct) : undefined,
          newRatePct: Number(newRatePct),
          effectiveDate,
          title: title.trim(),
          source: source.trim() || undefined,
        },
      }),
    meta: { successMessage: "Regulatory change recorded" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["reg-changes"] });
      onOpenChange(false);
      setHsnSac("");
      setTitle("");
      setOldRatePct("");
      setNewRatePct("");
      setSource("");
    },
  });

  const valid = hsnSac.trim() !== "" && title.trim() !== "" && Number.isFinite(Number(newRatePct)) && newRatePct.trim() !== "";

  return (
    <FormSheet
      open={open}
      onOpenChange={onOpenChange}
      title="Record regulatory change"
      description="An announced per-HSN/SAC GST rate change. The radar forecasts its tax delta over your invoices."
      submitLabel="Record change"
      submitting={mut.isPending}
      disabled={!valid}
      onSubmit={() => mut.mutate()}
    >
      <TextField id="rc-hsn" label="HSN / SAC" value={hsnSac} onChange={setHsnSac} placeholder="6109" />
      <TextField id="rc-title" label="Title" value={title} onChange={setTitle} placeholder="GST on cotton apparel raised" />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="rc-old" label="Old rate (%)" type="number" inputMode="numeric" value={oldRatePct} onChange={setOldRatePct} placeholder="5" />
        <TextField id="rc-new" label="New rate (%)" type="number" inputMode="numeric" value={newRatePct} onChange={setNewRatePct} placeholder="12" required />
      </div>
      <TextField id="rc-eff" label="Effective date" type="date" value={effectiveDate} onChange={setEffectiveDate} />
      <TextField id="rc-src" label="Source" value={source} onChange={setSource} placeholder="Notification 05/2026-CT (Rate)" />
      <FormError error={mut.error} />
    </FormSheet>
  );
}

function ImpactSheet({ changeId, onClose }: { changeId: string | null; onClose: () => void }) {
  const impactQ = useQuery({
    queryKey: ["reg-change-impact", changeId],
    queryFn: () => api<RegChangeImpactReport>(`/v1/gst/reg-changes/${changeId}/impact`),
    enabled: changeId !== null,
  });

  const r = impactQ.data;
  const worse = (r?.deltaGstMinor ?? 0) >= 0;

  return (
    <Sheet open={changeId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-xl">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            <RadarIcon className="size-4 text-muted-foreground" /> Impact forecast
          </SheetTitle>
          <SheetDescription>
            {r ? `HSN/SAC ${r.hsnSac} · new rate ${r.newRatePct}%` : "Estimating impact…"}
          </SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto px-4">
          <DataState isLoading={impactQ.isLoading} isError={impactQ.isError} error={impactQ.error} skeletonRows={4}>
            {r && (
              <>
                <div className="grid grid-cols-3 gap-3">
                  <Stat label="Current GST" value={formatInr(r.currentGstMinor)} />
                  <Stat label="Forecast GST" value={formatInr(r.forecastGstMinor)} />
                  <Stat
                    label="Delta"
                    value={`${worse ? "+" : ""}${formatInr(r.deltaGstMinor)}`}
                    tone={worse ? "danger" : "success"}
                  />
                </div>
                <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                  <Badge variant="outline">Forecast · {pct(r.confidence)} confidence</Badge>
                  <span>
                    {r.affectedInvoiceCount} invoices · {r.affectedLineCount} lines · {formatInr(r.totalTaxableMinor)} taxable
                  </span>
                </div>
                <p className="rounded-lg bg-muted/50 p-3 text-xs leading-relaxed text-muted-foreground">
                  {r.disclaimer}
                </p>

                {r.invoices.length > 0 && (
                  <div>
                    <p className="mb-2 text-sm font-medium">Affected invoices</p>
                    <div className="overflow-hidden rounded-lg border">
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead>Invoice</TableHead>
                            <TableHead className="text-end">Taxable</TableHead>
                            <TableHead className="text-end">Current</TableHead>
                            <TableHead className="text-end">Forecast</TableHead>
                            <TableHead className="text-end">Delta</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {r.invoices.map((iv) => (
                            <TableRow key={iv.invoiceId}>
                              <TableCell className="font-medium">{iv.invoiceNumber}</TableCell>
                              <TableCell className="text-end tabular-nums">{formatInr(iv.taxableMinor)}</TableCell>
                              <TableCell className="text-end tabular-nums text-muted-foreground">{formatInr(iv.currentGstMinor)}</TableCell>
                              <TableCell className="text-end tabular-nums text-muted-foreground">{formatInr(iv.forecastGstMinor)}</TableCell>
                              <TableCell className="text-end font-medium tabular-nums">{formatInr(iv.deltaGstMinor)}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </div>
                  </div>
                )}
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

function Stat({ label, value, tone }: { label: string; value: string; tone?: "danger" | "success" }) {
  return (
    <div className="rounded-xl bg-card p-3 ring-1 shadow-rest ring-foreground/10">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p
        className={cn(
          "mt-1 truncate font-heading text-lg font-semibold tabular-nums",
          tone === "danger" && "text-destructive",
          tone === "success" && "text-success",
        )}
      >
        {value}
      </p>
    </div>
  );
}
