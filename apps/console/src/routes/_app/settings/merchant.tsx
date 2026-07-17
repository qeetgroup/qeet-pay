import {
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  DataState,
  Separator,
  TimeSince,
} from "@qeetrix/ui";
import { useQuery } from "@tanstack/react-query";
import { Link, createFileRoute } from "@tanstack/react-router";
import { BuildingIcon, ShieldCheckIcon } from "lucide-react";

import { PageHeader } from "@/components/page-header";
import { api } from "@/lib/api";

export const Route = createFileRoute("/_app/settings/merchant")({ component: MerchantPage });

type Me = { merchantId: string | null; subject: string | null; roles: string[]; authenticated: boolean };
type KybStatus = {
  merchantId: string;
  overallStatus: string;
  panStatus: string;
  gstinStatus: string;
  bankStatus: string;
  verifiedAt: string | null;
};

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

function MerchantPage() {
  const meQ = useQuery({
    queryKey: ["me"],
    queryFn: () => api<Me>("/v1/me"),
    staleTime: 60_000,
  });
  const kybQ = useQuery({
    queryKey: ["kyb-status"],
    queryFn: () => api<KybStatus>("/v1/merchants/kyb/status"),
    staleTime: 30_000,
  });

  const kyb = kybQ.data;

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="The merchant tenant and identity this console is scoped to." />

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <BuildingIcon className="size-5 text-muted-foreground" />
            Identity
          </CardTitle>
          <CardDescription>Resolved from the current credential (GET /v1/me).</CardDescription>
        </CardHeader>
        <CardContent>
          <DataState isLoading={meQ.isLoading} isError={meQ.isError} error={meQ.error} skeletonRows={2}>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-muted-foreground">Merchant ID</dt>
                <dd className="font-mono">{meQ.data?.merchantId ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Subject</dt>
                <dd className="font-mono">{meQ.data?.subject ?? "— (API key)"}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Authentication</dt>
                <dd>
                  <Badge variant={meQ.data?.authenticated ? "success" : "muted"}>
                    {meQ.data?.authenticated ? "OIDC (JWT)" : "API key"}
                  </Badge>
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Roles</dt>
                <dd className="flex flex-wrap gap-1">
                  {(meQ.data?.roles ?? []).length === 0 ? (
                    <span className="text-muted-foreground">—</span>
                  ) : (
                    meQ.data?.roles.map((r) => (
                      <Badge key={r} variant="secondary">
                        {r}
                      </Badge>
                    ))
                  )}
                </dd>
              </div>
            </dl>
          </DataState>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheckIcon className="size-5 text-muted-foreground" />
            Verification
          </CardTitle>
          <CardDescription>KYB status for this merchant.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <DataState isLoading={kybQ.isLoading} isError={kybQ.isError} error={kybQ.error} skeletonRows={1}>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={statusVariant(kyb?.overallStatus ?? "")}>Overall: {kyb?.overallStatus ?? "—"}</Badge>
              <Badge variant={statusVariant(kyb?.panStatus ?? "")}>PAN: {kyb?.panStatus ?? "—"}</Badge>
              <Badge variant={statusVariant(kyb?.gstinStatus ?? "")}>GSTIN: {kyb?.gstinStatus ?? "—"}</Badge>
              <Badge variant={statusVariant(kyb?.bankStatus ?? "")}>Bank: {kyb?.bankStatus ?? "—"}</Badge>
            </div>
            {kyb?.verifiedAt && (
              <p className="text-sm text-muted-foreground">
                Verified <TimeSince value={kyb.verifiedAt} />
              </p>
            )}
          </DataState>

          <Separator />

          <Button variant="outline" size="sm" render={<Link to={"/kyb" as never} />}>
            Manage KYB
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
