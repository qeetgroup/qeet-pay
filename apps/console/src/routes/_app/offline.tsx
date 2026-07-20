import {
  Badge,
  Button,
  DataState,
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Separator,
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
import { CheckIcon, CreditCardIcon, QrCodeIcon, SmartphoneIcon, WalletIcon } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import { ApiError, api } from "@/lib/api";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/offline")({ component: OfflinePage });

// ── Types (mirror the /v1/offline API views) ────────────────────────────────

type BharatQr = {
  id: string;
  amountMinor: number | null;
  dynamic: boolean;
  currency: string;
  merchantName: string;
  reference: string;
  payload: string;
  createdAt: string;
};

type Wallet = {
  id: string;
  customerRef: string;
  balanceMinor: number;
  currency: string;
  status: "ACTIVE" | "CLOSED";
  createdAt: string;
  closedAt: string | null;
};

type Txn = {
  id: string;
  walletId: string;
  type: "TOPUP" | "SPEND";
  amountMinor: number;
  currency: string;
  ledgerEntryId: string;
  createdAt: string;
};

type WalletWithTxns = { wallet: Wallet; txns: Txn[] };

type Intent = {
  id: string;
  payerMobile: string;
  amountMinor: number;
  currency: string;
  status: "CREATED" | "CONFIRMED" | "FAILED";
  ledgerEntryId: string | null;
  createdAt: string;
  confirmedAt: string | null;
};

type Device = {
  id: string;
  label: string;
  serialNo: string;
  status: "ACTIVE" | "INACTIVE";
  createdAt: string;
};

type PosTxn = {
  id: string;
  deviceId: string;
  amountMinor: number;
  currency: string;
  method: string;
  rrn: string;
  ledgerEntryId: string;
  createdAt: string;
};

function errMsg(e: unknown): string {
  return e instanceof ApiError ? e.message : e instanceof Error ? e.message : "Something went wrong.";
}

function OfflinePage() {
  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="India-first offline & rural rails (sandbox): Bharat QR, UPI Lite wallets, UPI 123Pay, and POS / Tap-to-Pay." />
      <BharatQrCard />
      <UpiLiteCard />
      <Pay123Card />
      <PosCard />
    </div>
  );
}

// ── Bharat QR ────────────────────────────────────────────────────────────────

function BharatQrCard() {
  const qc = useQueryClient();
  const [amount, setAmount] = useState("");
  const [merchantName, setMerchantName] = useState("");
  const [reference, setReference] = useState("");

  const listQ = useQuery({
    queryKey: ["offline", "bharat-qr"],
    queryFn: () => api<BharatQr[]>("/v1/offline/bharat-qr"),
    staleTime: 15_000,
  });

  const genM = useMutation({
    mutationFn: () => {
      const minor = amount.trim() ? rupeesToMinor(amount) : null;
      return api<BharatQr>("/v1/offline/bharat-qr", {
        method: "POST",
        body: {
          amountMinor: minor ?? undefined,
          currency: "INR",
          merchantName: merchantName.trim() || undefined,
          reference: reference.trim() || undefined,
        },
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["offline", "bharat-qr"] });
      setAmount("");
      setReference("");
    },
  });

  const rows = listQ.data ?? [];

  return (
    <SectionCard
      title="Bharat QR"
      icon={QrCodeIcon}
      description="Generate a single unified QR accepting UPI + RuPay + Visa + Mastercard. Leave the amount blank for a static/open-amount QR."
    >
      <div className="space-y-4">
        <form
          className="flex flex-col gap-3 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            genM.mutate();
          }}
        >
          <Field className="sm:w-40">
            <FieldLabel htmlFor="qr-amount">Amount (₹)</FieldLabel>
            <Input id="qr-amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Optional" />
          </Field>
          <Field className="flex-1">
            <FieldLabel htmlFor="qr-name">Merchant name</FieldLabel>
            <Input id="qr-name" value={merchantName} onChange={(e) => setMerchantName(e.target.value)} placeholder="Qeet Merchant" />
          </Field>
          <Field className="flex-1">
            <FieldLabel htmlFor="qr-ref">Reference</FieldLabel>
            <Input id="qr-ref" value={reference} onChange={(e) => setReference(e.target.value)} placeholder="order id (optional)" />
          </Field>
          <Button type="submit" disabled={genM.isPending}>
            {genM.isPending ? "Generating…" : "Generate QR"}
          </Button>
        </form>
        {genM.isError && <p className="text-sm text-destructive">{errMsg(genM.error)}</p>}

        <DataState
          isLoading={listQ.isLoading}
          isError={listQ.isError}
          error={listQ.error}
          isEmpty={rows.length === 0}
          emptyIcon={QrCodeIcon}
          emptyTitle="No QRs generated"
          emptyDescription="Generate a Bharat QR to display it on POS material, invoices, or WhatsApp."
          skeletonRows={3}
        >
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Reference</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead>Payload</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="font-medium">{r.reference}</TableCell>
                    <TableCell>
                      <Badge variant={r.dynamic ? "success" : "muted"}>{r.dynamic ? "Dynamic" : "Static"}</Badge>
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {r.amountMinor == null ? "Open" : formatInr(r.amountMinor, r.currency)}
                    </TableCell>
                    <TableCell className="max-w-[22rem] truncate font-mono text-xs text-muted-foreground">{r.payload}</TableCell>
                    <TableCell className="text-muted-foreground">
                      <TimeSince value={r.createdAt} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </DataState>
      </div>
    </SectionCard>
  );
}

// ── UPI Lite ─────────────────────────────────────────────────────────────────

function UpiLiteCard() {
  const qc = useQueryClient();
  const [customerRef, setCustomerRef] = useState("");
  const [selected, setSelected] = useState<string | null>(null);

  const walletsQ = useQuery({
    queryKey: ["offline", "upi-lite"],
    queryFn: () => api<Wallet[]>("/v1/offline/upi-lite/wallets"),
    staleTime: 15_000,
  });

  const createM = useMutation({
    mutationFn: () =>
      api<Wallet>("/v1/offline/upi-lite/wallets", {
        method: "POST",
        body: { customerRef: customerRef.trim(), currency: "INR" },
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["offline", "upi-lite"] });
      setCustomerRef("");
    },
  });

  const wallets = walletsQ.data ?? [];

  return (
    <SectionCard
      title="UPI Lite"
      icon={WalletIcon}
      description="On-device low-value wallets. Spends are capped at ₹500 per transaction and ₹2,000 per day."
    >
      <div className="space-y-4">
        <form
          className="flex flex-col gap-2 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            if (customerRef.trim()) createM.mutate();
          }}
        >
          <Field className="flex-1">
            <FieldLabel htmlFor="ul-customer">Customer reference</FieldLabel>
            <Input id="ul-customer" value={customerRef} onChange={(e) => setCustomerRef(e.target.value)} placeholder="cust_ACME_42" />
          </Field>
          <Button type="submit" disabled={!customerRef.trim() || createM.isPending}>
            {createM.isPending ? "Creating…" : "Create wallet"}
          </Button>
        </form>
        {createM.isError && <p className="text-sm text-destructive">{errMsg(createM.error)}</p>}

        <DataState
          isLoading={walletsQ.isLoading}
          isError={walletsQ.isError}
          error={walletsQ.error}
          isEmpty={wallets.length === 0}
          emptyIcon={WalletIcon}
          emptyTitle="No UPI Lite wallets"
          emptyDescription="Create a wallet to top up and spend low-value amounts offline."
          skeletonRows={3}
        >
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Customer</TableHead>
                  <TableHead>Balance</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {wallets.map((w) => (
                  <TableRow key={w.id}>
                    <TableCell className="font-medium">{w.customerRef}</TableCell>
                    <TableCell className="tabular-nums">{formatInr(w.balanceMinor, w.currency)}</TableCell>
                    <TableCell>
                      <Badge variant={w.status === "ACTIVE" ? "success" : "muted"}>{w.status === "ACTIVE" ? "Active" : "Closed"}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button variant="outline" size="sm" onClick={() => setSelected(selected === w.id ? null : w.id)}>
                        {selected === w.id ? "Hide" : "Manage"}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </DataState>

        {selected && <WalletDetail walletId={selected} />}
      </div>
    </SectionCard>
  );
}

function WalletDetail({ walletId }: { walletId: string }) {
  const qc = useQueryClient();
  const [topup, setTopup] = useState("");
  const [spend, setSpend] = useState("");

  const detailQ = useQuery({
    queryKey: ["offline", "upi-lite", walletId],
    queryFn: () => api<WalletWithTxns>(`/v1/offline/upi-lite/wallets/${walletId}`),
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["offline", "upi-lite"] });
    qc.invalidateQueries({ queryKey: ["offline", "upi-lite", walletId] });
  };

  const topupM = useMutation({
    mutationFn: (minor: number) =>
      api<Txn>(`/v1/offline/upi-lite/wallets/${walletId}/topup`, { method: "POST", body: { amountMinor: minor } }),
    onSuccess: () => {
      invalidate();
      setTopup("");
    },
  });

  const spendM = useMutation({
    mutationFn: (minor: number) =>
      api<Txn>(`/v1/offline/upi-lite/wallets/${walletId}/spend`, { method: "POST", body: { amountMinor: minor } }),
    onSuccess: () => {
      invalidate();
      setSpend("");
    },
  });

  const txns = detailQ.data?.txns ?? [];

  return (
    <div className="rounded-lg border bg-muted/30 p-4">
      <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error} skeletonRows={3}>
        <div className="grid gap-3 sm:grid-cols-2">
          <form
            className="flex items-end gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              const m = rupeesToMinor(topup);
              if (m && m > 0) topupM.mutate(m);
            }}
          >
            <Field className="flex-1">
              <FieldLabel htmlFor="ul-topup">Top up (₹)</FieldLabel>
              <Input id="ul-topup" inputMode="decimal" value={topup} onChange={(e) => setTopup(e.target.value)} placeholder="1000.00" />
            </Field>
            <Button type="submit" variant="outline" disabled={topupM.isPending}>
              Top up
            </Button>
          </form>
          <form
            className="flex items-end gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              const m = rupeesToMinor(spend);
              if (m && m > 0) spendM.mutate(m);
            }}
          >
            <Field className="flex-1">
              <FieldLabel htmlFor="ul-spend">Spend (₹, ≤ 500)</FieldLabel>
              <Input id="ul-spend" inputMode="decimal" value={spend} onChange={(e) => setSpend(e.target.value)} placeholder="250.00" />
            </Field>
            <Button type="submit" disabled={spendM.isPending}>
              Spend
            </Button>
          </form>
        </div>
        {(topupM.isError || spendM.isError) && (
          <p className="mt-2 text-sm text-destructive">{errMsg(topupM.error ?? spendM.error)}</p>
        )}

        <Separator className="my-3" />
        <p className="mb-2 text-sm font-medium">Movements ({txns.length})</p>
        {txns.length === 0 ? (
          <p className="text-sm text-muted-foreground">No movements yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Type</TableHead>
                <TableHead>Amount</TableHead>
                <TableHead>When</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {txns.map((t) => (
                <TableRow key={t.id}>
                  <TableCell>
                    <Badge variant={t.type === "TOPUP" ? "secondary" : "muted"}>{t.type}</Badge>
                  </TableCell>
                  <TableCell className="tabular-nums">{formatInr(t.amountMinor, t.currency)}</TableCell>
                  <TableCell className="text-muted-foreground">
                    <TimeSince value={t.createdAt} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DataState>
    </div>
  );
}

// ── UPI 123Pay ───────────────────────────────────────────────────────────────

function Pay123Card() {
  const qc = useQueryClient();
  const [payerMobile, setPayerMobile] = useState("");
  const [amount, setAmount] = useState("");

  const listQ = useQuery({
    queryKey: ["offline", "123pay"],
    queryFn: () => api<Intent[]>("/v1/offline/123pay/intents"),
    staleTime: 15_000,
  });

  const createM = useMutation({
    mutationFn: () => {
      const minor = rupeesToMinor(amount);
      return api<Intent>("/v1/offline/123pay/intents", {
        method: "POST",
        body: { payerMobile: payerMobile.trim(), amountMinor: minor ?? 0, currency: "INR" },
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["offline", "123pay"] });
      setPayerMobile("");
      setAmount("");
    },
  });

  const confirmM = useMutation({
    mutationFn: (id: string) => api<Intent>(`/v1/offline/123pay/intents/${id}/confirm`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["offline", "123pay"] }),
  });

  const rows = listQ.data ?? [];

  return (
    <SectionCard
      title="UPI 123Pay"
      icon={SmartphoneIcon}
      description="Feature-phone / IVR payment intents. Create an intent, then confirm it (simulated IVR completion) to post the payment."
    >
      <div className="space-y-4">
        <form
          className="flex flex-col gap-2 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            if (payerMobile.trim() && rupeesToMinor(amount)) createM.mutate();
          }}
        >
          <Field className="flex-1">
            <FieldLabel htmlFor="p123-mobile">Payer mobile</FieldLabel>
            <Input id="p123-mobile" value={payerMobile} onChange={(e) => setPayerMobile(e.target.value)} placeholder="+9198765xxxxx" />
          </Field>
          <Field className="sm:w-40">
            <FieldLabel htmlFor="p123-amount">Amount (₹)</FieldLabel>
            <Input id="p123-amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="420.00" />
          </Field>
          <Button type="submit" disabled={createM.isPending}>
            {createM.isPending ? "Creating…" : "Create intent"}
          </Button>
        </form>
        {createM.isError && <p className="text-sm text-destructive">{errMsg(createM.error)}</p>}

        <DataState
          isLoading={listQ.isLoading}
          isError={listQ.isError}
          error={listQ.error}
          isEmpty={rows.length === 0}
          emptyIcon={SmartphoneIcon}
          emptyTitle="No 123Pay intents"
          emptyDescription="Create an intent for a feature-phone payer."
          skeletonRows={3}
        >
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Payer</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="font-medium">{r.payerMobile}</TableCell>
                    <TableCell className="tabular-nums">{formatInr(r.amountMinor, r.currency)}</TableCell>
                    <TableCell>
                      <IntentStatus status={r.status} />
                    </TableCell>
                    <TableCell className="text-right">
                      {r.status === "CREATED" ? (
                        <Button size="sm" disabled={confirmM.isPending} onClick={() => confirmM.mutate(r.id)}>
                          <CheckIcon /> Confirm
                        </Button>
                      ) : (
                        <span className="text-xs text-muted-foreground">
                          {r.confirmedAt ? <TimeSince value={r.confirmedAt} /> : "—"}
                        </span>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </DataState>
      </div>
    </SectionCard>
  );
}

function IntentStatus({ status }: { status: Intent["status"] }) {
  const variant = status === "CONFIRMED" ? "success" : status === "FAILED" ? "destructive" : "muted";
  return <Badge variant={variant}>{status}</Badge>;
}

// ── POS / Tap-to-Pay ─────────────────────────────────────────────────────────

function PosCard() {
  const qc = useQueryClient();
  const [label, setLabel] = useState("");
  const [serialNo, setSerialNo] = useState("");
  const [deviceId, setDeviceId] = useState("");
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState("TAP");

  const devicesQ = useQuery({
    queryKey: ["offline", "pos-devices"],
    queryFn: () => api<Device[]>("/v1/offline/pos/devices"),
    staleTime: 15_000,
  });

  const registerM = useMutation({
    mutationFn: () =>
      api<Device>("/v1/offline/pos/devices", { method: "POST", body: { label: label.trim(), serialNo: serialNo.trim() } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["offline", "pos-devices"] });
      setLabel("");
      setSerialNo("");
    },
  });

  const captureM = useMutation({
    mutationFn: () => {
      const minor = rupeesToMinor(amount);
      return api<PosTxn>("/v1/offline/pos/transactions", {
        method: "POST",
        body: { deviceId, amountMinor: minor ?? 0, currency: "INR", method },
      });
    },
    onSuccess: () => setAmount(""),
  });

  const devices = devicesQ.data ?? [];

  return (
    <SectionCard
      title="POS / Tap-to-Pay"
      icon={CreditCardIcon}
      description="Register in-person devices and capture Tap-to-Pay / card / QR transactions (sandbox)."
    >
      <div className="space-y-4">
        <form
          className="flex flex-col gap-2 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            if (label.trim() && serialNo.trim()) registerM.mutate();
          }}
        >
          <Field className="flex-1">
            <FieldLabel htmlFor="pos-label">Device label</FieldLabel>
            <Input id="pos-label" value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Counter 1" />
          </Field>
          <Field className="flex-1">
            <FieldLabel htmlFor="pos-serial">Serial no.</FieldLabel>
            <Input id="pos-serial" value={serialNo} onChange={(e) => setSerialNo(e.target.value)} placeholder="SN-0001" />
          </Field>
          <Button type="submit" variant="outline" disabled={registerM.isPending}>
            Register device
          </Button>
        </form>
        {registerM.isError && <p className="text-sm text-destructive">{errMsg(registerM.error)}</p>}

        <DataState
          isLoading={devicesQ.isLoading}
          isError={devicesQ.isError}
          error={devicesQ.error}
          isEmpty={devices.length === 0}
          emptyIcon={CreditCardIcon}
          emptyTitle="No POS devices"
          emptyDescription="Register a device to start capturing in-person payments."
          skeletonRows={2}
        >
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Label</TableHead>
                  <TableHead>Serial</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Registered</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {devices.map((d) => (
                  <TableRow key={d.id}>
                    <TableCell className="font-medium">{d.label}</TableCell>
                    <TableCell className="tabular-nums text-muted-foreground">{d.serialNo}</TableCell>
                    <TableCell>
                      <Badge variant={d.status === "ACTIVE" ? "success" : "muted"}>{d.status}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      <TimeSince value={d.createdAt} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </DataState>

        <FieldGroup>
          <p className="text-sm font-medium">Capture a transaction</p>
          <form
            className="flex flex-col gap-2 sm:flex-row sm:items-end"
            onSubmit={(e) => {
              e.preventDefault();
              if (deviceId && rupeesToMinor(amount)) captureM.mutate();
            }}
          >
            <Field className="flex-1">
              <FieldLabel htmlFor="pos-device">Device</FieldLabel>
              <Select value={deviceId} onValueChange={(v) => setDeviceId(v as string)}>
                <SelectTrigger id="pos-device">
                  <SelectValue placeholder="Select a device" />
                </SelectTrigger>
                <SelectContent>
                  {devices
                    .filter((d) => d.status === "ACTIVE")
                    .map((d) => (
                      <SelectItem key={d.id} value={d.id}>
                        {d.label}
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
            </Field>
            <Field className="sm:w-40">
              <FieldLabel htmlFor="pos-amount">Amount (₹)</FieldLabel>
              <Input id="pos-amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="750.00" />
            </Field>
            <Field className="sm:w-36">
              <FieldLabel htmlFor="pos-method">Method</FieldLabel>
              <Select value={method} onValueChange={(v) => setMethod(v as string)}>
                <SelectTrigger id="pos-method">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="TAP">Tap-to-Pay</SelectItem>
                  <SelectItem value="SWIPE">Card swipe</SelectItem>
                  <SelectItem value="QR">Scan QR</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Button type="submit" disabled={!deviceId || captureM.isPending}>
              {captureM.isPending ? "Capturing…" : "Capture"}
            </Button>
          </form>
          <FieldDescription>A capture posts money-in to the ledger, exactly like an online payment capture.</FieldDescription>
          {captureM.isError && <p className="text-sm text-destructive">{errMsg(captureM.error)}</p>}
          {captureM.isSuccess && captureM.data && (
            <p className="text-sm text-success">
              Captured {formatInr(captureM.data.amountMinor, captureM.data.currency)} · RRN {captureM.data.rrn}
            </p>
          )}
        </FieldGroup>
      </div>
    </SectionCard>
  );
}
