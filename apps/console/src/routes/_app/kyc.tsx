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
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { CheckCircle2Icon, ClockIcon, UserCheckIcon, UsersIcon } from "lucide-react";
import { useState } from "react";

import { ListToolbar } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { KpiRow, KpiTile } from "@/components/stat-tile";
import { DetailRow, FormError, ShortId, TextField } from "@/features/finance/shared";
import { FormSheet } from "@/features/gst/ui";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";

export const Route = createFileRoute("/_app/kyc")({ component: KycPage });

type KycView = {
  id: string;
  merchantId: string;
  customerRef: string;
  fullName: string;
  aadhaarLast4: string | null;
  aadhaarTxnId: string | null;
  aadhaarStatus: string;
  pan: string | null;
  panStatus: string;
  consentGiven: boolean;
  consentAt: string | null;
  overallStatus: string;
  verifiedAt: string | null;
};

function statusVariant(status: string): "success" | "destructive" | "warning" | "muted" | "secondary" {
  switch (status) {
    case "VERIFIED":
      return "success";
    case "REJECTED":
    case "FAILED":
      return "destructive";
    case "PENDING":
    case "INITIATED":
    case "IN_PROGRESS":
      return "warning";
    case "NONE":
    case "NOT_STARTED":
      return "muted";
    default:
      return "secondary";
  }
}

function KycPage() {
  const [createOpen, setCreateOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);

  const listQ = useQuery({
    queryKey: ["kyc-customers"],
    queryFn: () => api<KycView[]>("/v1/kyc/customers"),
    staleTime: 15_000,
  });

  const rows = listQ.data ?? [];
  const verified = rows.filter((r) => r.overallStatus === "VERIFIED").length;
  const pending = rows.length - verified;

  const lv = useListView(rows, {
    searchFields: (r) => [r.customerRef, r.fullName, r.pan, r.overallStatus],
    filterFields: { overallStatus: (r) => r.overallStatus },
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="End-customer KYC — Aadhaar-OTP e-KYC (simulated), PAN verification, and consent capture."
        actions={<Button onClick={() => setCreateOpen(true)}>Add customer</Button>}
      />

      <KpiRow cols={3}>
        <KpiTile label="Customers" value={rows.length.toLocaleString("en-IN")} icon={UsersIcon} tone="info" loading={listQ.isLoading} />
        <KpiTile label="Verified" value={verified.toLocaleString("en-IN")} icon={CheckCircle2Icon} tone="success" loading={listQ.isLoading} />
        <KpiTile label="In progress" value={pending.toLocaleString("en-IN")} icon={ClockIcon} tone={pending > 0 ? "warning" : "neutral"} loading={listQ.isLoading} />
      </KpiRow>

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search customer, name, PAN…"
          filters={[
            {
              id: "overallStatus",
              label: "Status",
              value: lv.filters.overallStatus ?? "",
              options: [
                { label: "Verified", value: "VERIFIED" },
                { label: "Pending", value: "PENDING" },
                { label: "Rejected", value: "REJECTED" },
              ],
              onChange: (v) => lv.setFilter("overallStatus", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          exportDisabled={lv.view.length === 0}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("kyc-customers", lv.view, [
                  { header: "Customer", value: (r: KycView) => r.customerRef },
                  { header: "Name", value: (r: KycView) => r.fullName },
                  { header: "Aadhaar", value: (r: KycView) => r.aadhaarStatus },
                  { header: "PAN", value: (r: KycView) => r.panStatus },
                  { header: "Overall", value: (r: KycView) => r.overallStatus },
                ])
              : exportToJson("kyc-customers", lv.view)
          }
        />
        <DataState
          isLoading={listQ.isLoading}
          isError={listQ.isError}
          error={listQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={UserCheckIcon}
          emptyTitle={lv.hasActiveFilters ? "No matching customers" : "No customers yet"}
          emptyDescription={
            lv.hasActiveFilters ? "Try clearing filters or search." : "Add a customer to run Aadhaar e-KYC and PAN verification."
          }
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Customer</TableHead>
                <TableHead>Aadhaar</TableHead>
                <TableHead>PAN</TableHead>
                <TableHead>Consent</TableHead>
                <TableHead>Overall</TableHead>
                <TableHead className="text-end">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((r) => (
                <TableRow key={r.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                  <TableCell>
                    <div className="font-medium">{r.fullName}</div>
                    <div className="font-mono text-xs text-muted-foreground">{r.customerRef}</div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(r.aadhaarStatus)}>{r.aadhaarStatus.replaceAll("_", " ")}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(r.panStatus)}>{r.panStatus.replaceAll("_", " ")}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={r.consentGiven ? "success" : "muted"}>{r.consentGiven ? "Given" : "Missing"}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(r.overallStatus)}>{r.overallStatus.replaceAll("_", " ")}</Badge>
                  </TableCell>
                  <TableCell className="text-end">
                    <Button variant="outline" size="sm" onClick={() => setDetailId(r.id)}>
                      Manage
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </Card>

      <CreateCustomerSheet open={createOpen} onOpenChange={setCreateOpen} />
      <KycDetailSheet kycId={detailId} onClose={() => setDetailId(null)} />
    </div>
  );
}

function CreateCustomerSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const [customerRef, setCustomerRef] = useState("");
  const [fullName, setFullName] = useState("");

  const mut = useMutation({
    mutationFn: () =>
      api<KycView>("/v1/kyc/customers", {
        method: "POST",
        body: { customerRef: customerRef.trim(), fullName: fullName.trim(), consentGiven: true },
      }),
    meta: { successMessage: "Customer created" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["kyc-customers"] });
      onOpenChange(false);
      setCustomerRef("");
      setFullName("");
    },
  });

  const valid = customerRef.trim() !== "" && fullName.trim() !== "";

  return (
    <FormSheet
      open={open}
      onOpenChange={onOpenChange}
      title="Add customer for KYC"
      description="Creates a KYC record with consent captured. Run Aadhaar e-KYC and PAN verification from the detail panel."
      submitLabel="Create customer"
      submitting={mut.isPending}
      disabled={!valid}
      onSubmit={() => mut.mutate()}
    >
      <TextField id="kyc-ref" label="Customer reference" value={customerRef} onChange={setCustomerRef} placeholder="cust_ACME_42" required />
      <TextField id="kyc-name" label="Full name" value={fullName} onChange={setFullName} placeholder="Priya Sharma" required />
      <FormError error={mut.error} />
    </FormSheet>
  );
}

function KycDetailSheet({ kycId, onClose }: { kycId: string | null; onClose: () => void }) {
  const qc = useQueryClient();
  const [aadhaar, setAadhaar] = useState("");
  const [otp, setOtp] = useState("");
  const [pan, setPan] = useState("");

  const detailQ = useQuery({
    queryKey: ["kyc-customer", kycId],
    queryFn: () => api<KycView>(`/v1/kyc/customers/${kycId}`),
    enabled: kycId !== null,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["kyc-customer", kycId] });
    qc.invalidateQueries({ queryKey: ["kyc-customers"] });
  };

  const initiateM = useMutation({
    mutationFn: () => api<KycView>(`/v1/kyc/customers/${kycId}/aadhaar/initiate`, { method: "POST", body: { aadhaar: aadhaar.trim() } }),
    onSuccess: invalidate,
  });
  const verifyM = useMutation({
    mutationFn: (txnId: string) =>
      api<KycView>(`/v1/kyc/customers/${kycId}/aadhaar/verify`, { method: "POST", body: { txnId, otp: otp.trim() } }),
    onSuccess: () => {
      invalidate();
      setOtp("");
    },
  });
  const panM = useMutation({
    mutationFn: () => api<KycView>(`/v1/kyc/customers/${kycId}/pan`, { method: "POST", body: { pan: pan.trim() } }),
    onSuccess: () => {
      invalidate();
      setPan("");
    },
  });

  const c = detailQ.data;

  return (
    <Sheet open={kycId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>{c ? c.fullName : "Customer KYC"}</SheetTitle>
          <SheetDescription>Aadhaar e-KYC (simulated OTP), PAN verification, and consent.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto px-4">
          <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error}>
            {c && (
              <>
                <div>
                  <DetailRow label="Customer" value={<span className="font-mono text-xs">{c.customerRef}</span>} />
                  <DetailRow label="Overall" value={<Badge variant={statusVariant(c.overallStatus)}>{c.overallStatus.replaceAll("_", " ")}</Badge>} />
                  <DetailRow label="Consent" value={<Badge variant={c.consentGiven ? "success" : "muted"}>{c.consentGiven ? "Given" : "Missing"}</Badge>} />
                </div>

                <div className="rounded-lg border p-3">
                  <div className="mb-2 flex items-center justify-between">
                    <p className="text-sm font-medium">Aadhaar e-KYC</p>
                    <Badge variant={statusVariant(c.aadhaarStatus)}>{c.aadhaarStatus.replaceAll("_", " ")}</Badge>
                  </div>
                  {c.aadhaarStatus !== "VERIFIED" && !c.aadhaarTxnId && (
                    <form
                      className="flex items-end gap-2"
                      onSubmit={(e) => {
                        e.preventDefault();
                        if (aadhaar.trim()) initiateM.mutate();
                      }}
                    >
                      <div className="flex-1">
                        <TextField id="kyc-aadhaar" label="Aadhaar number" value={aadhaar} onChange={setAadhaar} placeholder="XXXX XXXX 1234" />
                      </div>
                      <Button type="submit" variant="outline" disabled={initiateM.isPending}>
                        Send OTP
                      </Button>
                    </form>
                  )}
                  {c.aadhaarStatus !== "VERIFIED" && c.aadhaarTxnId && (
                    <form
                      className="flex items-end gap-2"
                      onSubmit={(e) => {
                        e.preventDefault();
                        if (otp.trim()) verifyM.mutate(c.aadhaarTxnId!);
                      }}
                    >
                      <div className="flex-1">
                        <TextField id="kyc-otp" label="OTP" value={otp} onChange={setOtp} placeholder="6-digit OTP" inputMode="numeric" />
                      </div>
                      <Button type="submit" disabled={verifyM.isPending}>
                        Verify
                      </Button>
                    </form>
                  )}
                  {c.aadhaarStatus === "VERIFIED" && (
                    <p className="text-xs text-muted-foreground">Aadhaar verified · ending {c.aadhaarLast4 ?? "••••"}</p>
                  )}
                  <FormError error={initiateM.error ?? verifyM.error} />
                </div>

                <div className="rounded-lg border p-3">
                  <div className="mb-2 flex items-center justify-between">
                    <p className="text-sm font-medium">PAN verification</p>
                    <Badge variant={statusVariant(c.panStatus)}>{c.panStatus.replaceAll("_", " ")}</Badge>
                  </div>
                  {c.panStatus !== "VERIFIED" ? (
                    <form
                      className="flex items-end gap-2"
                      onSubmit={(e) => {
                        e.preventDefault();
                        if (pan.trim()) panM.mutate();
                      }}
                    >
                      <div className="flex-1">
                        <TextField id="kyc-pan" label="PAN" value={pan} onChange={(v) => setPan(v.toUpperCase())} placeholder="ABCDE1234F" />
                      </div>
                      <Button type="submit" variant="outline" disabled={panM.isPending}>
                        Submit
                      </Button>
                    </form>
                  ) : (
                    <p className="text-xs text-muted-foreground">PAN verified · {c.pan}</p>
                  )}
                  <FormError error={panM.error} />
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
