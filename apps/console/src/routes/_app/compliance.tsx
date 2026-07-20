import {
  Badge,
  Button,
  DataState,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  cn,
} from "@qeetrix/ui";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import {
  BanknoteIcon,
  CheckCircle2Icon,
  FileCheck2Icon,
  RepeatIcon,
  ScaleIcon,
  ShieldCheckIcon,
  SirenIcon,
  TrendingUpIcon,
} from "lucide-react";
import { useState } from "react";
import type { ComponentType } from "react";

import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import { KpiRow, KpiTile } from "@/components/stat-tile";
import { api } from "@/lib/api";
import { formatInr } from "@/lib/money";

export const Route = createFileRoute("/_app/compliance")({ component: CompliancePage });

type ReconHealth = {
  totalSettlements: number;
  reconciledCount: number;
  discrepancyCount: number;
  pendingCount: number;
  nodalBalanceMinor: number;
  nodalHealthy: boolean;
  status: string;
  asOf: string;
};
type FilingSummary = {
  totalReturns: number;
  filedCount: number;
  preparedCount: number;
  draftCount: number;
  errorCount: number;
  totalTaxFiledMinor: number;
  latestFiledPeriod: string | null;
  status: string;
  asOf: string;
};
type FraudPosture = { scoringEnabled: boolean; mode: string; description: string; asOf: string };
type KybOnboarding = {
  overallStatus: string;
  panStatus: string;
  gstinStatus: string;
  bankStatus: string;
  onboardingComplete: boolean;
  verifiedAt: string | null;
  asOf: string;
};
type FinancialKpis = {
  currentMrrMinor: number;
  currentArrMinor: number;
  trailingTpvMinor: number;
  trailingSuccessRatePercent: number;
  windowDays: number;
  asOf: string;
};
type ComplianceHealth = {
  merchantId: string;
  generatedAt: string;
  overallStatus: string;
  reconciliation: ReconHealth;
  filing: FilingSummary;
  fraud: FraudPosture;
  kyb: KybOnboarding;
  kpis: FinancialKpis;
};

function healthTone(status: string): "success" | "warning" {
  return status === "HEALTHY" ? "success" : "warning";
}

function CompliancePage() {
  const [windowDays, setWindowDays] = useState("30");

  const healthQ = useQuery({
    queryKey: ["compliance-health", windowDays],
    queryFn: () => api<ComplianceHealth>("/v1/analytics/compliance-health", { query: { windowDays } }),
    staleTime: 30_000,
  });

  const h = healthQ.data;

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <PageHeader
        description="A single-screen view of settlement/nodal reconciliation, GSTR filing, fraud posture, KYB onboarding, and headline financial KPIs. Read on demand — each tile carries an as-of time."
        actions={
          <div className="flex items-center gap-2">
            {h && (
              <Badge variant={healthTone(h.overallStatus)} className="h-8 px-3">
                {h.overallStatus === "HEALTHY" ? "All systems healthy" : "Needs attention"}
              </Badge>
            )}
            <Select value={windowDays} onValueChange={(v) => v && setWindowDays(v)}>
              <SelectTrigger size="sm" className="w-auto min-w-28">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="30">30-day window</SelectItem>
                <SelectItem value="60">60-day window</SelectItem>
                <SelectItem value="90">90-day window</SelectItem>
              </SelectContent>
            </Select>
          </div>
        }
      />

      <KpiRow>
        <KpiTile
          label={`TPV · ${h?.kpis.windowDays ?? windowDays} days`}
          value={formatInr(h?.kpis.trailingTpvMinor ?? 0)}
          icon={BanknoteIcon}
          tone="brand"
          loading={healthQ.isLoading}
          to="/analytics"
        />
        <KpiTile
          label="Success rate"
          value={`${(h?.kpis.trailingSuccessRatePercent ?? 0).toFixed(1)}%`}
          icon={CheckCircle2Icon}
          tone="success"
          loading={healthQ.isLoading}
        />
        <KpiTile
          label="MRR"
          value={formatInr(h?.kpis.currentMrrMinor ?? 0)}
          icon={RepeatIcon}
          tone="info"
          hint={`${formatInr(h?.kpis.currentArrMinor ?? 0)} ARR`}
          loading={healthQ.isLoading}
          to="/subscriptions"
        />
        <KpiTile
          label="Nodal balance"
          value={formatInr(h?.reconciliation.nodalBalanceMinor ?? 0)}
          icon={ScaleIcon}
          tone={h?.reconciliation.nodalHealthy === false ? "danger" : "brand"}
          hint={h?.reconciliation.nodalHealthy === false ? "Nodal imbalance" : "Holding account healthy"}
          loading={healthQ.isLoading}
          to="/reconciliation"
        />
      </KpiRow>

      <DataState
        isLoading={healthQ.isLoading}
        isError={healthQ.isError}
        error={healthQ.error}
        skeletonRows={6}
      >
        {h && (
          <div className="grid gap-4 lg:grid-cols-2">
            <HealthCard
              title="Reconciliation & nodal"
              icon={FileCheck2Icon}
              status={h.reconciliation.status}
              to="/reconciliation"
              asOf={h.reconciliation.asOf}
            >
              <Metric label="Settlements" value={h.reconciliation.totalSettlements.toLocaleString("en-IN")} />
              <Metric label="Reconciled" value={h.reconciliation.reconciledCount.toLocaleString("en-IN")} tone="success" />
              <Metric label="Discrepancies" value={h.reconciliation.discrepancyCount.toLocaleString("en-IN")} tone={h.reconciliation.discrepancyCount > 0 ? "danger" : undefined} />
              <Metric label="Pending" value={h.reconciliation.pendingCount.toLocaleString("en-IN")} />
              <Metric label="Nodal balance" value={formatInr(h.reconciliation.nodalBalanceMinor)} tone={h.reconciliation.nodalHealthy ? "success" : "danger"} />
            </HealthCard>

            <HealthCard
              title="GSTR filing"
              icon={ScaleIcon}
              status={h.filing.status}
              to="/gst-returns"
              asOf={h.filing.asOf}
            >
              <Metric label="Returns" value={h.filing.totalReturns.toLocaleString("en-IN")} />
              <Metric label="Filed" value={h.filing.filedCount.toLocaleString("en-IN")} tone="success" />
              <Metric label="Prepared" value={h.filing.preparedCount.toLocaleString("en-IN")} />
              <Metric label="Errors" value={h.filing.errorCount.toLocaleString("en-IN")} tone={h.filing.errorCount > 0 ? "danger" : undefined} />
              <Metric label="Tax filed" value={formatInr(h.filing.totalTaxFiledMinor)} />
              {h.filing.latestFiledPeriod && <Metric label="Latest period" value={h.filing.latestFiledPeriod} />}
            </HealthCard>

            <HealthCard
              title="Fraud posture"
              icon={SirenIcon}
              status={h.fraud.scoringEnabled ? "HEALTHY" : "ATTENTION"}
              statusLabel={h.fraud.mode.replaceAll("_", " ")}
              to="/fraud"
              asOf={h.fraud.asOf}
            >
              <p className="text-sm leading-relaxed text-muted-foreground">{h.fraud.description}</p>
              <div className="mt-3">
                <Metric label="Scoring" value={h.fraud.scoringEnabled ? "Active" : "Disabled"} tone={h.fraud.scoringEnabled ? "success" : undefined} />
              </div>
            </HealthCard>

            <HealthCard
              title="KYB onboarding"
              icon={ShieldCheckIcon}
              status={h.kyb.onboardingComplete ? "HEALTHY" : "ATTENTION"}
              statusLabel={h.kyb.overallStatus.replaceAll("_", " ")}
              to="/kyb"
              asOf={h.kyb.asOf}
            >
              <Metric label="PAN" value={<CheckBadge status={h.kyb.panStatus} />} />
              <Metric label="GSTIN" value={<CheckBadge status={h.kyb.gstinStatus} />} />
              <Metric label="Bank account" value={<CheckBadge status={h.kyb.bankStatus} />} />
              <Metric label="Overall" value={<CheckBadge status={h.kyb.overallStatus} />} />
            </HealthCard>
          </div>
        )}
      </DataState>

      {h && (
        <p className="text-xs text-muted-foreground">
          Generated {new Date(h.generatedAt).toLocaleString("en-IN")} · MRR {formatInr(h.kpis.currentMrrMinor)} · ARR {formatInr(h.kpis.currentArrMinor)}
        </p>
      )}
    </div>
  );
}

function HealthCard({
  title,
  icon: Icon,
  status,
  statusLabel,
  to,
  asOf,
  children,
}: {
  title: string;
  icon: ComponentType<{ className?: string }>;
  status: string;
  statusLabel?: string;
  to: string;
  asOf: string;
  children: React.ReactNode;
}) {
  return (
    <SectionCard
      title={title}
      icon={Icon}
      description={`As of ${new Date(asOf).toLocaleString("en-IN")}`}
      action={
        <div className="flex items-center gap-2">
          <Badge variant={healthTone(status)}>{statusLabel ?? (status === "HEALTHY" ? "Healthy" : "Attention")}</Badge>
          <Button variant="ghost" size="sm" render={<Link to={to as never} />}>
            Open
          </Button>
        </div>
      }
    >
      <div className="space-y-1.5">{children}</div>
    </SectionCard>
  );
}

function Metric({
  label,
  value,
  tone,
}: {
  label: string;
  value: React.ReactNode;
  tone?: "success" | "danger";
}) {
  return (
    <div className="flex items-center justify-between gap-3 py-1 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span
        className={cn(
          "text-right font-medium tabular-nums",
          tone === "success" && "text-success",
          tone === "danger" && "text-destructive",
        )}
      >
        {value}
      </span>
    </div>
  );
}

function CheckBadge({ status }: { status: string }) {
  const tone = status === "VERIFIED" ? "success" : status === "REJECTED" ? "destructive" : status === "PENDING" ? "warning" : "muted";
  return <Badge variant={tone}>{status.replaceAll("_", " ")}</Badge>;
}
