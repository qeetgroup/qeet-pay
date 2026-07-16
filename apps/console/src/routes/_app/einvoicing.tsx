import {
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  DataState,
  Input,
  Separator,
  Textarea,
  TimeSince,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { FileCheck2Icon, QrCodeIcon, SearchIcon, XCircleIcon } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/page-header";
import { FormSheet, KeyValue, StatusBadge } from "@/features/gst/ui";
import { api } from "@/lib/api";

export const Route = createFileRoute("/_app/einvoicing")({ component: EInvoicingPage });

type IrnView = {
  invoiceId: string;
  invoiceNumber: string;
  irnStatus: string;
  irn: string | null;
  ackNo: string | null;
  ackDate: string | null;
  signedQrCode: string | null;
  generatedAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
};

function EInvoicingPage() {
  const qc = useQueryClient();
  const [idInput, setIdInput] = useState("");
  const [activeId, setActiveId] = useState<string | null>(null);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");

  const key = ["einvoice", activeId] as const;

  const einvoiceQ = useQuery({
    queryKey: key,
    queryFn: () => api<IrnView>(`/v1/gst/invoices/${activeId}/irn`),
    enabled: activeId !== null,
    retry: false,
  });

  const generateMut = useMutation({
    mutationFn: () => api<IrnView>(`/v1/gst/invoices/${activeId}/irn`, { method: "POST" }),
    meta: { successMessage: "IRN generated" },
    onSuccess: (data) => qc.setQueryData(key, data),
  });

  const cancelMut = useMutation({
    mutationFn: (reason: string) =>
      api<IrnView>(`/v1/gst/invoices/${activeId}/irn/cancel`, {
        method: "POST",
        body: { reason },
      }),
    meta: { successMessage: "IRN cancelled" },
    onSuccess: (data) => {
      qc.setQueryData(key, data);
      setCancelOpen(false);
      setCancelReason("");
    },
  });

  const einvoice = einvoiceQ.data;

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Register an issued GST invoice at the IRP to obtain its IRN and signed QR, then cancel within the regulatory window." />

      <Card>
        <CardHeader>
          <CardTitle>Look up an e-invoice</CardTitle>
          <CardDescription>Enter the GST invoice id to view or manage its IRN.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="flex flex-col gap-2 sm:flex-row"
            onSubmit={(e) => {
              e.preventDefault();
              if (idInput.trim()) setActiveId(idInput.trim());
            }}
          >
            <div className="relative flex-1">
              <SearchIcon className="pointer-events-none absolute inset-s-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={idInput}
                onChange={(e) => setIdInput(e.target.value)}
                placeholder="GST invoice id (UUID)"
                className="ps-9"
                aria-label="GST invoice id"
              />
            </div>
            <Button type="submit" disabled={!idInput.trim()}>
              Load e-invoice
            </Button>
          </form>
        </CardContent>
      </Card>

      {activeId && (
        <Card>
          <CardHeader className="flex flex-row items-start justify-between gap-3">
            <div>
              <CardTitle>{einvoice?.invoiceNumber ?? "E-invoice"}</CardTitle>
              <CardDescription>IRN registration status and details.</CardDescription>
            </div>
            {einvoice && (
              <div className="flex items-center gap-2">
                {einvoice.irnStatus === "NONE" && (
                  <Button
                    size="sm"
                    disabled={generateMut.isPending}
                    onClick={() => generateMut.mutate()}
                  >
                    <FileCheck2Icon /> Generate IRN
                  </Button>
                )}
                {einvoice.irnStatus === "GENERATED" && (
                  <Button
                    size="sm"
                    variant="destructive"
                    onClick={() => setCancelOpen(true)}
                    disabled={cancelMut.isPending}
                  >
                    <XCircleIcon /> Cancel IRN
                  </Button>
                )}
              </div>
            )}
          </CardHeader>
          <CardContent>
            <DataState
              isLoading={einvoiceQ.isLoading}
              isError={einvoiceQ.isError}
              error={einvoiceQ.error}
              skeletonRows={4}
            >
              {einvoice && (
                <div className="space-y-4">
                  <div className="flex flex-wrap items-center gap-3">
                    <StatusBadge value={einvoice.irnStatus} />
                    {einvoice.generatedAt && (
                      <span className="text-xs text-muted-foreground">
                        Generated <TimeSince value={einvoice.generatedAt} />
                      </span>
                    )}
                    {einvoice.cancelledAt && (
                      <span className="text-xs text-muted-foreground">
                        Cancelled <TimeSince value={einvoice.cancelledAt} />
                      </span>
                    )}
                  </div>

                  {einvoice.irnStatus === "NONE" ? (
                    <p className="rounded-md bg-muted/50 p-3 text-sm text-muted-foreground">
                      This invoice has not been registered at the IRP yet. Generate an IRN to obtain
                      its unique reference number and signed QR code.
                    </p>
                  ) : (
                    <>
                      <div className="space-y-1.5">
                        <KeyValue label="Ack no.">{einvoice.ackNo ?? "—"}</KeyValue>
                        <KeyValue label="Ack date">
                          {einvoice.ackDate ? <TimeSince value={einvoice.ackDate} /> : "—"}
                        </KeyValue>
                        {einvoice.cancelReason && (
                          <KeyValue label="Cancel reason">{einvoice.cancelReason}</KeyValue>
                        )}
                      </div>

                      <div className="space-y-1.5">
                        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          IRN (64-char)
                        </p>
                        <pre className="overflow-x-auto rounded-md bg-muted p-3 font-mono text-xs break-all whitespace-pre-wrap">
                          {einvoice.irn ?? "—"}
                        </pre>
                      </div>

                      <div className="space-y-1.5">
                        <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          <QrCodeIcon className="size-3.5" /> Signed QR payload
                        </p>
                        <pre className="max-h-40 overflow-auto rounded-md bg-muted p-3 font-mono text-xs break-all whitespace-pre-wrap">
                          {einvoice.signedQrCode ?? "—"}
                        </pre>
                      </div>
                    </>
                  )}
                </div>
              )}
            </DataState>
          </CardContent>
        </Card>
      )}

      <FormSheet
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        title="Cancel IRN"
        description="Cancelling an IRN is only permitted within the IRP's regulatory window."
        submitLabel="Cancel IRN"
        submitting={cancelMut.isPending}
        disabled={cancelReason.trim().length === 0}
        onSubmit={() => cancelMut.mutate(cancelReason.trim())}
      >
        <Separator />
        <div className="space-y-1.5">
          <label htmlFor="cancelReason" className="text-sm font-medium">
            Reason
          </label>
          <Textarea
            id="cancelReason"
            value={cancelReason}
            onChange={(e) => setCancelReason(e.target.value)}
            placeholder="e.g. Wrong recipient GSTIN"
            rows={3}
          />
        </div>
      </FormSheet>
    </div>
  );
}
