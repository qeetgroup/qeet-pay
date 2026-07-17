import {
  Button,
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
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { PlusIcon, UmbrellaIcon } from "lucide-react";
import { useState, type FormEvent } from "react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import {
  CurrencySelect,
  DetailRow,
  FormError,
  MoneyField,
  SelectField,
  StatusBadge,
  TextField,
} from "@/features/finance/shared";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr, rupeesToMinor } from "@/lib/money";

export const Route = createFileRoute("/_app/insurance")({ component: InsurancePage });

const PRODUCTS = [
  { label: "Payment protection", value: "PAYMENT_PROTECTION" },
  { label: "Fraud cover", value: "FRAUD_COVER" },
  { label: "Subscription interruption", value: "SUBSCRIPTION_INTERRUPTION" },
];

type Policy = {
  id: string;
  product: string;
  holderRef: string;
  currency: string;
  premiumMinor: number;
  coverAmountMinor: number;
  status: string;
  createdAt: string;
  cancelledAt: string | null;
};

type Claim = {
  id: string;
  policyId: string;
  amountMinor: number;
  reason: string | null;
  status: string;
  payoutEntryId: string | null;
  createdAt: string;
  decidedAt: string | null;
};

type PolicyView = { policy: Policy; claims: Claim[] };

function productLabel(value: string): string {
  return PRODUCTS.find((p) => p.value === value)?.label ?? value.replaceAll("_", " ");
}

function InsurancePage() {
  const [issueOpen, setIssueOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const listQ = useQuery({
    queryKey: ["insurance-policies"],
    queryFn: () => api<Policy[]>("/v1/insurance/policies"),
  });

  const rows = listQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (p) => [p.id, p.holderRef, p.product, p.status],
    filterFields: { status: (p) => p.status, product: (p) => p.product },
    sortFields: {
      premium: (p) => p.premiumMinor,
      cover: (p) => p.coverAmountMinor,
      created: (p) => p.createdAt,
    },
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader
        description="Issue embedded protection policies and adjudicate claims against cover."
        actions={
          <Button onClick={() => setIssueOpen(true)}>
            <PlusIcon /> Issue policy
          </Button>
        }
      />

      <Card className="overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search holder, product, id…"
          filters={[
            {
              id: "product",
              label: "Product",
              value: lv.filters.product ?? "",
              options: PRODUCTS,
              onChange: (v) => lv.setFilter("product", v),
            },
            {
              id: "status",
              label: "Status",
              value: lv.filters.status ?? "",
              options: [
                { label: "Active", value: "ACTIVE" },
                { label: "Cancelled", value: "CANCELLED" },
                { label: "Expired", value: "EXPIRED" },
              ],
              onChange: (v) => lv.setFilter("status", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
          exportDisabled={lv.view.length === 0}
          onExport={(format) => {
            const cols = [
              { header: "Policy", value: (p: Policy) => p.id },
              { header: "Product", value: (p: Policy) => p.product },
              { header: "Holder", value: (p: Policy) => p.holderRef },
              { header: "Currency", value: (p: Policy) => p.currency },
              { header: "Premium", value: (p: Policy) => p.premiumMinor },
              { header: "Cover", value: (p: Policy) => p.coverAmountMinor },
              { header: "Status", value: (p: Policy) => p.status },
              { header: "Created", value: (p: Policy) => p.createdAt },
            ];
            if (format === "csv") exportToCsv("insurance-policies", lv.view, cols);
            else exportToJson("insurance-policies", lv.view);
          }}
        />
        <CardContent className="p-0">
          <DataState
            isLoading={listQ.isLoading}
            isError={listQ.isError}
            error={listQ.error}
            isEmpty={lv.view.length === 0}
            emptyIcon={UmbrellaIcon}
            emptyTitle="No policies yet"
            emptyDescription="Issue a protection policy to start collecting premium."
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead>Holder</TableHead>
                  <SortHeader columnKey="premium" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Premium
                  </SortHeader>
                  <SortHeader columnKey="cover" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Cover
                  </SortHeader>
                  <TableHead>Status</TableHead>
                  <SortHeader columnKey="created" sort={lv.sort} onToggle={lv.toggleSort}>
                    Created
                  </SortHeader>
                  <TableHead className="text-end">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell className="font-medium">{productLabel(p.product)}</TableCell>
                    <TableCell>{p.holderRef}</TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(p.premiumMinor, p.currency)}
                    </TableCell>
                    <TableCell className="text-end tabular-nums">
                      {formatInr(p.coverAmountMinor, p.currency)}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={p.status} />
                    </TableCell>
                    <TableCell>
                      <TimeSince value={p.createdAt} />
                    </TableCell>
                    <TableCell className="text-end">
                      <Button variant="outline" size="sm" onClick={() => setSelectedId(p.id)}>
                        Manage
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </DataState>
        </CardContent>
      </Card>

      <IssuePolicySheet open={issueOpen} onOpenChange={setIssueOpen} />
      <PolicyDetailSheet policyId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}

function IssuePolicySheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const qc = useQueryClient();
  const [product, setProduct] = useState("PAYMENT_PROTECTION");
  const [holderRef, setHolderRef] = useState("");
  const [premium, setPremium] = useState("");
  const [cover, setCover] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [premiumError, setPremiumError] = useState<string>();
  const [coverError, setCoverError] = useState<string>();

  const mut = useMutation({
    mutationFn: (vars: { premiumMinor: number; coverAmountMinor: number }) =>
      api<PolicyView>("/v1/insurance/policies", {
        method: "POST",
        body: { product, holderRef, currency, ...vars },
      }),
    meta: { successMessage: "Policy issued" },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["insurance-policies"] });
      setHolderRef("");
      setPremium("");
      setCover("");
      onOpenChange(false);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const premiumMinor = rupeesToMinor(premium);
    const coverAmountMinor = rupeesToMinor(cover);
    setPremiumError(premiumMinor === null || premiumMinor === 0 ? "Enter a valid premium." : undefined);
    setCoverError(coverAmountMinor === null || coverAmountMinor === 0 ? "Enter a valid cover amount." : undefined);
    if (premiumMinor === null || premiumMinor === 0 || coverAmountMinor === null || coverAmountMinor === 0) {
      return;
    }
    mut.mutate({ premiumMinor, coverAmountMinor });
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Issue policy</SheetTitle>
          <SheetDescription>The premium is collected into the insurance reserve on issue.</SheetDescription>
        </SheetHeader>
        <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
          <div className="flex-1 space-y-4 overflow-y-auto px-4">
            <SelectField id="product" label="Product" value={product} onChange={setProduct} options={PRODUCTS} />
            <TextField
              id="holder-ref"
              label="Holder reference"
              value={holderRef}
              onChange={setHolderRef}
              placeholder="cust_123"
              required
            />
            <MoneyField
              id="premium"
              label="Premium"
              value={premium}
              onChange={setPremium}
              required
              error={premiumError}
            />
            <MoneyField
              id="cover"
              label="Cover amount"
              value={cover}
              onChange={setCover}
              description="Maximum payout across claims."
              required
              error={coverError}
            />
            <CurrencySelect value={currency} onChange={setCurrency} />
            <FormError error={mut.error} />
          </div>
          <SheetFooter>
            <Button type="submit" disabled={mut.isPending}>
              {mut.isPending ? "Issuing…" : "Issue policy"}
            </Button>
            <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function PolicyDetailSheet({ policyId, onClose }: { policyId: string | null; onClose: () => void }) {
  const qc = useQueryClient();
  const [claimAmount, setClaimAmount] = useState("");
  const [claimReason, setClaimReason] = useState("");
  const [claimError, setClaimError] = useState<string>();

  const detailQ = useQuery({
    queryKey: ["insurance-policy", policyId],
    queryFn: () => api<PolicyView>(`/v1/insurance/policies/${policyId}`),
    enabled: policyId !== null,
  });

  function invalidate() {
    qc.invalidateQueries({ queryKey: ["insurance-policy", policyId] });
    qc.invalidateQueries({ queryKey: ["insurance-policies"] });
  }

  const fileM = useMutation({
    mutationFn: (amountMinor: number) =>
      api<Claim>(`/v1/insurance/policies/${policyId}/claims`, {
        method: "POST",
        body: { amountMinor, reason: claimReason || undefined },
      }),
    meta: { successMessage: "Claim filed" },
    onSuccess: () => {
      invalidate();
      setClaimAmount("");
      setClaimReason("");
    },
  });

  const decideM = useMutation({
    mutationFn: (vars: { claimId: string; decision: "approve" | "reject" }) =>
      api<Claim>(`/v1/insurance/claims/${vars.claimId}/${vars.decision}`, { method: "POST" }),
    meta: { successMessage: "Claim decision recorded" },
    onSuccess: invalidate,
  });

  const cancelM = useMutation({
    mutationFn: () => api<PolicyView>(`/v1/insurance/policies/${policyId}/cancel`, { method: "POST" }),
    meta: { successMessage: "Policy cancelled" },
    onSuccess: invalidate,
  });

  function submitClaim(e: FormEvent) {
    e.preventDefault();
    const minor = rupeesToMinor(claimAmount);
    if (minor === null || minor === 0) {
      setClaimError("Enter a valid claim amount.");
      return;
    }
    setClaimError(undefined);
    fileM.mutate(minor);
  }

  const policy = detailQ.data?.policy;

  return (
    <Sheet open={policyId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>{policy ? productLabel(policy.product) : "Policy"}</SheetTitle>
          <SheetDescription>Cover, premium, and claim adjudication.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto px-4">
          <DataState isLoading={detailQ.isLoading} isError={detailQ.isError} error={detailQ.error}>
            {policy && (
              <>
                <div>
                  <DetailRow label="Status" value={<StatusBadge status={policy.status} />} />
                  <DetailRow label="Holder" value={policy.holderRef} />
                  <DetailRow label="Premium" value={formatInr(policy.premiumMinor, policy.currency)} />
                  <DetailRow label="Cover" value={formatInr(policy.coverAmountMinor, policy.currency)} />
                </div>

                {policy.status === "ACTIVE" && (
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => cancelM.mutate()}
                      disabled={cancelM.isPending}
                    >
                      Cancel policy
                    </Button>
                  </div>
                )}

                {policy.status === "ACTIVE" && (
                  <form onSubmit={submitClaim} className="space-y-3 rounded-lg border p-3">
                    <p className="text-sm font-medium">File a claim</p>
                    <MoneyField
                      id="claim-amount"
                      label="Claim amount"
                      value={claimAmount}
                      onChange={setClaimAmount}
                      required
                      error={claimError}
                    />
                    <TextField
                      id="claim-reason"
                      label="Reason"
                      value={claimReason}
                      onChange={setClaimReason}
                      placeholder="Optional"
                    />
                    <FormError error={fileM.error} />
                    <Button type="submit" size="sm" disabled={fileM.isPending}>
                      {fileM.isPending ? "Filing…" : "File claim"}
                    </Button>
                  </form>
                )}

                <div>
                  <p className="mb-2 text-sm font-medium">Claims</p>
                  {detailQ.data && detailQ.data.claims.length > 0 ? (
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead className="text-end">Amount</TableHead>
                          <TableHead>Reason</TableHead>
                          <TableHead>Status</TableHead>
                          <TableHead className="text-end">Action</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {detailQ.data.claims.map((c) => (
                          <TableRow key={c.id}>
                            <TableCell className="text-end tabular-nums">
                              {formatInr(c.amountMinor, policy.currency)}
                            </TableCell>
                            <TableCell className="text-muted-foreground">{c.reason ?? "—"}</TableCell>
                            <TableCell>
                              <StatusBadge status={c.status} />
                            </TableCell>
                            <TableCell className="text-end">
                              {c.status === "FILED" && (
                                <div className="flex justify-end gap-1.5">
                                  <Button
                                    size="sm"
                                    onClick={() => decideM.mutate({ claimId: c.id, decision: "approve" })}
                                    disabled={decideM.isPending}
                                  >
                                    Approve
                                  </Button>
                                  <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => decideM.mutate({ claimId: c.id, decision: "reject" })}
                                    disabled={decideM.isPending}
                                  >
                                    Reject
                                  </Button>
                                </div>
                              )}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  ) : (
                    <p className="text-sm text-muted-foreground">No claims filed yet.</p>
                  )}
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
