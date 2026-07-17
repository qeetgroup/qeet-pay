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
  TimeSince,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { BuildingIcon, LandmarkIcon, ReceiptIcon, ShieldCheckIcon } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";

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

function statusVariant(status: string): "success" | "destructive" | "warning" | "muted" {
  switch (status) {
    case "VERIFIED":
      return "success";
    case "REJECTED":
      return "destructive";
    case "PENDING":
      return "warning";
    default:
      return "muted";
  }
}

function KybPage() {
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
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Verify your business identity — PAN, GSTIN and settlement bank account — to unlock live processing." />

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheckIcon className="size-5 text-muted-foreground" />
            Overall status
          </CardTitle>
          <CardDescription>All three checks must pass before the merchant is verified.</CardDescription>
        </CardHeader>
        <CardContent>
          <DataState
            isLoading={statusQ.isLoading}
            isError={statusQ.isError}
            error={statusQ.error}
            skeletonRows={1}
          >
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
                <div className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary [&_svg]:size-5">
                  {st.icon}
                </div>
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
            <SheetTitle>
              {step === "pan" ? "Submit PAN" : step === "gstin" ? "Submit GSTIN" : "Submit bank account"}
            </SheetTitle>
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
            <Button
              type="button"
              disabled={!canSubmit || submit.isPending}
              onClick={() => step && canSubmit && submit.mutate(step)}
            >
              {submit.isPending ? "Submitting…" : "Submit for verification"}
            </Button>
            <SheetClose
              render={
                <Button variant="outline" type="button">
                  Cancel
                </Button>
              }
            />
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </div>
  );
}
