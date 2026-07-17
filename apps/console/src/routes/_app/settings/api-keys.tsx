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
} from "@qeetrix/ui";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { KeyRoundIcon } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/page-header";
import { api, keyStore } from "@/lib/api";

export const Route = createFileRoute("/_app/settings/api-keys")({ component: ApiKeysPage });

type Me = { merchantId: string | null; subject: string | null; roles: string[]; authenticated: boolean };

function maskKey(k: string): string {
  if (k.length <= 8) return "•".repeat(k.length);
  return `${k.slice(0, 6)}${"•".repeat(8)}${k.slice(-4)}`;
}

function ApiKeysPage() {
  const qc = useQueryClient();
  const [currentKey, setCurrentKey] = useState<string | null>(() => keyStore.get());
  const [draft, setDraft] = useState("");

  const meQ = useQuery({
    queryKey: ["me", currentKey],
    queryFn: () => api<Me>("/v1/me"),
    enabled: Boolean(currentKey),
    staleTime: 60_000,
  });

  function save() {
    const trimmed = draft.trim();
    if (!trimmed) return;
    keyStore.set(trimmed);
    setCurrentKey(trimmed);
    setDraft("");
    qc.invalidateQueries({ queryKey: ["me"] });
  }

  function clear() {
    keyStore.clear();
    setCurrentKey(null);
    qc.removeQueries({ queryKey: ["me"] });
  }

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="The API key this console sends as X-Api-Key on every request." />

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <KeyRoundIcon className="size-5 text-muted-foreground" />
            Console API key
          </CardTitle>
          <CardDescription>
            Keys are minted by the backend during merchant onboarding and are shown only once — Qeet Pay never returns
            them again. Paste your key here to authenticate this console.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="flex flex-wrap items-center gap-3">
            <span className="text-sm text-muted-foreground">Current key</span>
            {currentKey ? (
              <>
                <code className="rounded bg-muted px-2 py-1 font-mono text-sm">{maskKey(currentKey)}</code>
                <Badge variant="success">Set</Badge>
              </>
            ) : (
              <Badge variant="warning">Not set</Badge>
            )}
          </div>

          <Separator />

          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="api-key">
                {currentKey ? "Replace key" : "Add key"}
              </FieldLabel>
              <Input
                id="api-key"
                type="password"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                placeholder="qp_live_…"
                autoComplete="off"
              />
              <FieldDescription>Stored locally in this browser only; it is never sent anywhere except the API.</FieldDescription>
            </Field>
          </FieldGroup>

          <div className="flex flex-wrap gap-2">
            <Button onClick={save} disabled={draft.trim() === ""}>
              Save key
            </Button>
            <Button variant="outline" onClick={clear} disabled={!currentKey}>
              Clear key
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Resolved identity</CardTitle>
          <CardDescription>What the backend reports for the current key (GET /v1/me).</CardDescription>
        </CardHeader>
        <CardContent>
          <DataState
            isLoading={meQ.isLoading}
            isError={meQ.isError}
            error={meQ.error}
            isEmpty={!currentKey}
            emptyIcon={KeyRoundIcon}
            emptyTitle="No key configured"
            emptyDescription="Add an API key above to resolve the current identity."
            skeletonRows={2}
          >
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
                <dt className="text-muted-foreground">Authenticated</dt>
                <dd>
                  <Badge variant={meQ.data?.authenticated ? "success" : "muted"}>
                    {meQ.data?.authenticated ? "Yes (JWT)" : "API key"}
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
    </div>
  );
}
