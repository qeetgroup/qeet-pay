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
  FieldDescription,
  FieldGroup,
  FieldLabel,
  Input,
  Separator,
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  Switch,
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
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
  BuildingIcon,
  LandmarkIcon,
  ReceiptIcon,
  ShieldCheckIcon,
  UsersRoundIcon,
  VideoIcon,
} from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/page-header";
import { DetailRow, FormError } from "@/features/finance/shared";
import { FormSheet } from "@/features/gst/ui";
import { ApiError, api } from "@/lib/api";
import { formatBps } from "@/lib/money";

export const Route = createFileRoute("/_app/kyb")({ component: KybPage });

type KybStatus = {
  merchantId: string;
  overallStatus: string;
  panStatus: string;
  gstinStatus: string;
  bankStatus: string;
  verifiedAt: string | null;
};

type Step = "pan" | "gstin" | "bank";

function statusVariant(status: string): "success" | "destructive" | "warning" | "muted" | "secondary" {
  switch (status) {
    case "VERIFIED":
    case "COMPLETED":
      return "success";
    case "REJECTED":
    case "FAILED":
      return "destructive";
    case "PENDING":
    case "IN_PROGRESS":
      return "warning";
    case "SCHEDULED":
      return "secondary";
    default:
      return "muted";
  }
}

function KybPage() {
  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Verify your business identity — PAN, GSTIN and settlement bank account — plus video-KYC (V-CIP) of a signatory and the ultimate-beneficial-owner registry (RBI Master Directions)." />
      <Tabs defaultValue="business">
        <TabsList>
          <TabsTrigger value="business">
            <ShieldCheckIcon /> Business KYB
          </TabsTrigger>
          <TabsTrigger value="vcip">
            <VideoIcon /> V-CIP
          </TabsTrigger>
          <TabsTrigger value="ubo">
            <UsersRoundIcon /> UBO
          </TabsTrigger>
        </TabsList>
        <TabsContent value="business" className="mt-4">
          <BusinessKybTab />
        </TabsContent>
        <TabsContent value="vcip" className="mt-4">
          <VcipTab />
        </TabsContent>
        <TabsContent value="ubo" className="mt-4">
          <UboTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

// ── Business KYB ──────────────────────────────────────────────────────────────

function BusinessKybTab() {
  const qc = useQueryClient();
  const [step, setStep] = useState<Step | null>(null);
  const [pan, setPan] = useState("");
  const [gstin, setGstin] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [ifsc, setIfsc] = useState("");

  const statusQ = useQuery({
    queryKey: ["kyb-status"],
    queryFn: () => api<KybStatus>("/v1/merchants/kyb/status"),
    staleTime: 15_000,
  });

  const submit = useMutation({
    mutationFn: (s: Step) => {
      if (s === "pan") return api<KybStatus>("/v1/merchants/kyb/pan", { method: "POST", body: { pan } });
      if (s === "gstin") return api<KybStatus>("/v1/merchants/kyb/gstin", { method: "POST", body: { gstin } });
      return api<KybStatus>("/v1/merchants/kyb/bank", { method: "POST", body: { accountNumber, ifsc } });
    },
    onSuccess: (data) => {
      qc.setQueryData(["kyb-status"], data);
      qc.invalidateQueries({ queryKey: ["kyb-status"] });
      closeSheet();
    },
  });

  function closeSheet() {
    setStep(null);
    submit.reset();
  }

  const s = statusQ.data;
  const steps: { key: Step; title: string; icon: React.ReactNode; status?: string; description: string }[] = [
    { key: "pan", title: "PAN", icon: <ReceiptIcon />, status: s?.panStatus, description: "Business Permanent Account Number" },
    { key: "gstin", title: "GSTIN", icon: <BuildingIcon />, status: s?.gstinStatus, description: "GST identification number" },
    { key: "bank", title: "Bank account", icon: <LandmarkIcon />, status: s?.bankStatus, description: "Settlement bank account + IFSC" },
  ];

  const canSubmit =
    step === "pan" ? pan.trim() !== "" : step === "gstin" ? gstin.trim() !== "" : accountNumber.trim() !== "" && ifsc.trim() !== "";

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheckIcon className="size-5 text-muted-foreground" />
            Overall status
          </CardTitle>
          <CardDescription>All three checks must pass before the merchant is verified.</CardDescription>
        </CardHeader>
        <CardContent>
          <DataState isLoading={statusQ.isLoading} isError={statusQ.isError} error={statusQ.error} skeletonRows={1}>
            <div className="flex flex-wrap items-center gap-3">
              <Badge variant={statusVariant(s?.overallStatus ?? "")}>{s?.overallStatus ?? "UNKNOWN"}</Badge>
              {s?.verifiedAt ? (
                <span className="text-sm text-muted-foreground">
                  Verified <TimeSince value={s.verifiedAt} />
                </span>
              ) : (
                <span className="text-sm text-muted-foreground">Not yet fully verified</span>
              )}
            </div>
          </DataState>
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-3">
        {steps.map((st) => (
          <Card key={st.key}>
            <CardContent className="flex flex-col gap-3 pt-6">
              <div className="flex items-center gap-3">
                <div className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary [&_svg]:size-5">{st.icon}</div>
                <div className="min-w-0">
                  <p className="font-medium">{st.title}</p>
                  <p className="truncate text-xs text-muted-foreground">{st.description}</p>
                </div>
              </div>
              <div className="flex items-center justify-between">
                <Badge variant={statusVariant(st.status ?? "")}>{st.status ?? "—"}</Badge>
                <Button variant="outline" size="sm" onClick={() => setStep(st.key)}>
                  {st.status === "VERIFIED" ? "Resubmit" : "Submit"}
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <Sheet open={step !== null} onOpenChange={(open) => (open ? undefined : closeSheet())}>
        <SheetContent side="right">
          <SheetHeader>
            <SheetTitle>{step === "pan" ? "Submit PAN" : step === "gstin" ? "Submit GSTIN" : "Submit bank account"}</SheetTitle>
            <SheetDescription>Details are verified with the KYB provider before the check transitions.</SheetDescription>
          </SheetHeader>

          <form
            className="flex flex-1 flex-col gap-4 overflow-y-auto px-4"
            onSubmit={(e) => {
              e.preventDefault();
              if (step && canSubmit) submit.mutate(step);
            }}
          >
            <FieldGroup>
              {step === "pan" && (
                <Field>
                  <FieldLabel htmlFor="pan">PAN</FieldLabel>
                  <Input id="pan" value={pan} onChange={(e) => setPan(e.target.value.toUpperCase())} placeholder="AAAAA0000A" autoFocus />
                  <FieldDescription>10-character business PAN.</FieldDescription>
                </Field>
              )}
              {step === "gstin" && (
                <Field>
                  <FieldLabel htmlFor="gstin">GSTIN</FieldLabel>
                  <Input id="gstin" value={gstin} onChange={(e) => setGstin(e.target.value.toUpperCase())} placeholder="22AAAAA0000A1Z5" autoFocus />
                  <FieldDescription>15-character GST identification number.</FieldDescription>
                </Field>
              )}
              {step === "bank" && (
                <>
                  <Field>
                    <FieldLabel htmlFor="account">Account number</FieldLabel>
                    <Input id="account" value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} placeholder="000123456789" autoFocus />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="ifsc">IFSC</FieldLabel>
                    <Input id="ifsc" value={ifsc} onChange={(e) => setIfsc(e.target.value.toUpperCase())} placeholder="HDFC0000123" />
                  </Field>
                </>
              )}
            </FieldGroup>

            {submit.isError && (
              <p className="text-sm text-destructive">
                {submit.error instanceof ApiError ? submit.error.message : "Submission failed. Please try again."}
              </p>
            )}
          </form>

          <Separator />
          <SheetFooter>
            <Button type="button" disabled={!canSubmit || submit.isPending} onClick={() => step && canSubmit && submit.mutate(step)}>
              {submit.isPending ? "Submitting…" : "Submit for verification"}
            </Button>
            <SheetClose render={<Button variant="outline" type="button">Cancel</Button>} />
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </div>
  );
}

// ── V-CIP ─────────────────────────────────────────────────────────────────────

type VcipSession = {
  id: string;
  subjectName: string;
  subjectRef: string | null;
  status: string;
  agentId: string | null;
  scheduledAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  livenessScore: number | null;
  geoTag: string | null;
  retentionExpiresAt: string | null;
  failureReason: string | null;
};

function VcipTab() {
  const qc = useQueryClient();
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [completeId, setCompleteId] = useState<string | null>(null);
  const [subjectName, setSubjectName] = useState("");
  const [subjectRef, setSubjectRef] = useState("");
  const [agentId, setAgentId] = useState("");

  const listQ = useQuery({
    queryKey: ["vcip-sessions"],
    queryFn: () => api<VcipSession[]>("/v1/merchants/kyb/vcip"),
    staleTime: 15_000,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["vcip-sessions"] });

  const scheduleM = useMutation({
    mutationFn: () =>
      api<VcipSession>("/v1/merchants/kyb/vcip", {
        method: "POST",
        body: { subjectName: subjectName.trim(), subjectRef: subjectRef.trim() || undefined, agentId: agentId.trim() || undefined },
      }),
    meta: { successMessage: "Session scheduled" },
    onSuccess: () => {
      invalidate();
      setScheduleOpen(false);
      setSubjectName("");
      setSubjectRef("");
      setAgentId("");
    },
  });

  const startM = useMutation({
    mutationFn: (id: string) => api<VcipSession>(`/v1/merchants/kyb/vcip/${id}/start`, { method: "POST" }),
    meta: { successMessage: "Session started" },
    onSuccess: invalidate,
  });
  const failM = useMutation({
    mutationFn: (id: string) => api<VcipSession>(`/v1/merchants/kyb/vcip/${id}/fail`, { method: "POST", body: { reason: "Failed from console" } }),
    meta: { successMessage: "Session failed" },
    onSuccess: invalidate,
  });

  const rows = listQ.data ?? [];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Button onClick={() => setScheduleOpen(true)}>
          <VideoIcon /> Schedule V-CIP
        </Button>
      </div>

      <Card className="gap-0 py-0">
        <div className="border-b p-4">
          <p className="text-sm font-medium">Video-KYC sessions</p>
        </div>
        <DataState
          isLoading={listQ.isLoading}
          isError={listQ.isError}
          error={listQ.error}
          isEmpty={rows.length === 0}
          emptyIcon={VideoIcon}
          emptyTitle="No V-CIP sessions"
          emptyDescription="Schedule a video-KYC session for a signatory to satisfy RBI V-CIP requirements."
          skeletonRows={3}
          className="p-6"
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Subject</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Liveness</TableHead>
                <TableHead>Scheduled</TableHead>
                <TableHead className="text-end">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((v) => (
                <TableRow key={v.id}>
                  <TableCell>
                    <div className="font-medium">{v.subjectName}</div>
                    {v.subjectRef && <div className="font-mono text-xs text-muted-foreground">{v.subjectRef}</div>}
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(v.status)}>{v.status.replaceAll("_", " ")}</Badge>
                  </TableCell>
                  <TableCell className="tabular-nums text-muted-foreground">{v.livenessScore != null ? `${v.livenessScore}%` : "—"}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {v.scheduledAt ? <TimeSince value={v.scheduledAt} /> : "—"}
                  </TableCell>
                  <TableCell className="text-end">
                    <div className="flex items-center justify-end gap-2">
                      {v.status === "SCHEDULED" && (
                        <Button size="sm" variant="outline" disabled={startM.isPending} onClick={() => startM.mutate(v.id)}>
                          Start
                        </Button>
                      )}
                      {v.status === "IN_PROGRESS" && (
                        <>
                          <Button size="sm" onClick={() => setCompleteId(v.id)}>
                            Complete
                          </Button>
                          <Button size="sm" variant="ghost" disabled={failM.isPending} onClick={() => failM.mutate(v.id)}>
                            Fail
                          </Button>
                        </>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </Card>

      <FormSheet
        open={scheduleOpen}
        onOpenChange={setScheduleOpen}
        title="Schedule V-CIP session"
        description="Book a video-KYC session for a merchant signatory."
        submitLabel="Schedule session"
        submitting={scheduleM.isPending}
        disabled={subjectName.trim() === ""}
        onSubmit={() => scheduleM.mutate()}
      >
        <Field>
          <FieldLabel htmlFor="vc-name">Subject name</FieldLabel>
          <Input id="vc-name" value={subjectName} onChange={(e) => setSubjectName(e.target.value)} placeholder="Signatory full name" />
        </Field>
        <Field>
          <FieldLabel htmlFor="vc-ref">Subject reference</FieldLabel>
          <Input id="vc-ref" value={subjectRef} onChange={(e) => setSubjectRef(e.target.value)} placeholder="Optional" />
        </Field>
        <Field>
          <FieldLabel htmlFor="vc-agent">Agent ID</FieldLabel>
          <Input id="vc-agent" value={agentId} onChange={(e) => setAgentId(e.target.value)} placeholder="Optional KYC agent" />
        </Field>
        <FormError error={scheduleM.error} />
      </FormSheet>

      <CompleteVcipSheet sessionId={completeId} onClose={() => setCompleteId(null)} onDone={invalidate} />
    </div>
  );
}

function CompleteVcipSheet({ sessionId, onClose, onDone }: { sessionId: string | null; onClose: () => void; onDone: () => void }) {
  const [biometricRef, setBiometricRef] = useState("");
  const [livenessScore, setLivenessScore] = useState("92");
  const [geoTag, setGeoTag] = useState("");

  const mut = useMutation({
    mutationFn: () =>
      api<VcipSession>(`/v1/merchants/kyb/vcip/${sessionId}/complete`, {
        method: "POST",
        body: { biometricRef: biometricRef.trim(), livenessScore: Number(livenessScore), geoTag: geoTag.trim() || undefined },
      }),
    meta: { successMessage: "Session completed" },
    onSuccess: () => {
      onDone();
      onClose();
      setBiometricRef("");
      setGeoTag("");
    },
  });

  return (
    <FormSheet
      open={sessionId !== null}
      onOpenChange={(o) => !o && onClose()}
      title="Complete V-CIP session"
      description="Record the biometric reference and liveness result to complete the session."
      submitLabel="Complete session"
      submitting={mut.isPending}
      disabled={biometricRef.trim() === ""}
      onSubmit={() => mut.mutate()}
    >
      <Field>
        <FieldLabel htmlFor="vc-bio">Biometric reference</FieldLabel>
        <Input id="vc-bio" value={biometricRef} onChange={(e) => setBiometricRef(e.target.value)} placeholder="bio_ref_…" />
      </Field>
      <Field>
        <FieldLabel htmlFor="vc-live">Liveness score (0–100)</FieldLabel>
        <Input id="vc-live" type="number" min={0} max={100} value={livenessScore} onChange={(e) => setLivenessScore(e.target.value)} />
      </Field>
      <Field>
        <FieldLabel htmlFor="vc-geo">Geo-tag</FieldLabel>
        <Input id="vc-geo" value={geoTag} onChange={(e) => setGeoTag(e.target.value)} placeholder="Optional (lat,long)" />
      </Field>
      <FormError error={mut.error} />
    </FormSheet>
  );
}

// ── UBO ───────────────────────────────────────────────────────────────────────

type BeneficialOwner = {
  id: string;
  name: string;
  pan: string | null;
  din: string | null;
  nationality: string | null;
  ownershipBps: number;
  controlPerson: boolean;
  panStatus: string;
  createdAt: string;
};

function UboTab() {
  const qc = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);
  const [name, setName] = useState("");
  const [pan, setPan] = useState("");
  const [din, setDin] = useState("");
  const [nationality, setNationality] = useState("IN");
  const [ownershipPct, setOwnershipPct] = useState("");
  const [controlPerson, setControlPerson] = useState(false);

  const listQ = useQuery({
    queryKey: ["ubo-owners"],
    queryFn: () => api<BeneficialOwner[]>("/v1/merchants/kyb/ubo"),
    staleTime: 15_000,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["ubo-owners"] });

  const addM = useMutation({
    mutationFn: () =>
      api<BeneficialOwner>("/v1/merchants/kyb/ubo", {
        method: "POST",
        body: {
          name: name.trim(),
          pan: pan.trim() || undefined,
          din: din.trim() || undefined,
          nationality: nationality.trim() || undefined,
          ownershipBps: Math.round(Number(ownershipPct) * 100),
          controlPerson,
        },
      }),
    meta: { successMessage: "Beneficial owner added" },
    onSuccess: () => {
      invalidate();
      setAddOpen(false);
      setName("");
      setPan("");
      setDin("");
      setOwnershipPct("");
      setControlPerson(false);
    },
  });

  const removeM = useMutation({
    mutationFn: (id: string) => api<void>(`/v1/merchants/kyb/ubo/${id}`, { method: "DELETE" }),
    meta: { successMessage: "Owner removed" },
    onSuccess: invalidate,
  });

  const pctNum = Number(ownershipPct);
  const validOwnership = Number.isFinite(pctNum) && pctNum > 10 && pctNum <= 100;
  const rows = listQ.data ?? [];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Button onClick={() => setAddOpen(true)}>
          <UsersRoundIcon /> Add beneficial owner
        </Button>
      </div>

      <Card className="gap-0 py-0">
        <div className="border-b p-4">
          <p className="text-sm font-medium">Ultimate beneficial owners</p>
          <p className="text-xs text-muted-foreground">Natural persons holding &gt; 10% equity (RBI Master Directions).</p>
        </div>
        <DataState
          isLoading={listQ.isLoading}
          isError={listQ.isError}
          error={listQ.error}
          isEmpty={rows.length === 0}
          emptyIcon={UsersRoundIcon}
          emptyTitle="No beneficial owners"
          emptyDescription="Register natural persons holding more than 10% equity."
          skeletonRows={3}
          className="p-6"
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>PAN</TableHead>
                <TableHead className="text-end">Ownership</TableHead>
                <TableHead>Control</TableHead>
                <TableHead>PAN status</TableHead>
                <TableHead className="text-end">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((o) => (
                <TableRow key={o.id}>
                  <TableCell>
                    <div className="font-medium">{o.name}</div>
                    {o.nationality && <div className="text-xs text-muted-foreground">{o.nationality}</div>}
                  </TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">{o.pan ?? "—"}</TableCell>
                  <TableCell className="text-end tabular-nums">{formatBps(o.ownershipBps)}</TableCell>
                  <TableCell>{o.controlPerson ? <Badge variant="secondary">Control person</Badge> : <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(o.panStatus)}>{o.panStatus.replaceAll("_", " ")}</Badge>
                  </TableCell>
                  <TableCell className="text-end">
                    <Button variant="ghost" size="sm" disabled={removeM.isPending} onClick={() => removeM.mutate(o.id)}>
                      Remove
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </Card>

      <FormSheet
        open={addOpen}
        onOpenChange={setAddOpen}
        title="Add beneficial owner"
        description="A natural person holding more than 10% equity, or a control person."
        submitLabel="Add owner"
        submitting={addM.isPending}
        disabled={name.trim() === "" || !validOwnership}
        onSubmit={() => addM.mutate()}
      >
        <Field>
          <FieldLabel htmlFor="ubo-name">Full name</FieldLabel>
          <Input id="ubo-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Natural person name" />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field>
            <FieldLabel htmlFor="ubo-pan">PAN</FieldLabel>
            <Input id="ubo-pan" value={pan} onChange={(e) => setPan(e.target.value.toUpperCase())} placeholder="ABCDE1234F" />
          </Field>
          <Field>
            <FieldLabel htmlFor="ubo-din">DIN</FieldLabel>
            <Input id="ubo-din" value={din} onChange={(e) => setDin(e.target.value)} placeholder="Optional" />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field>
            <FieldLabel htmlFor="ubo-nat">Nationality</FieldLabel>
            <Input id="ubo-nat" value={nationality} onChange={(e) => setNationality(e.target.value.toUpperCase())} placeholder="IN" />
          </Field>
          <Field>
            <FieldLabel htmlFor="ubo-own">Ownership (%)</FieldLabel>
            <Input id="ubo-own" type="number" min={10.01} max={100} step={0.01} value={ownershipPct} onChange={(e) => setOwnershipPct(e.target.value)} placeholder="25" />
            <FieldDescription>Must be greater than 10%.</FieldDescription>
          </Field>
        </div>
        <label className="flex items-center justify-between gap-3 rounded-lg border p-3 text-sm">
          <span>
            <span className="font-medium">Control person</span>
            <span className="block text-xs text-muted-foreground">Exercises control regardless of shareholding.</span>
          </span>
          <Switch checked={controlPerson} onCheckedChange={setControlPerson} />
        </label>
        <FormError error={addM.error} />
      </FormSheet>
    </div>
  );
}
