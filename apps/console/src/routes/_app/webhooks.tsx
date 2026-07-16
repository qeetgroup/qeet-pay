import {
  Badge,
  Button,
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  DataState,
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
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
import { PlusIcon, SendIcon, WebhookIcon } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";

export const Route = createFileRoute("/_app/webhooks")({ component: WebhooksPage });

type Endpoint = { id: string; url: string; events: string; status: string };
type Delivery = {
  id: string;
  eventType: string;
  status: string;
  attemptCount: number;
  lastResponseCode: number | null;
  lastError: string | null;
  deliveredAt: string | null;
};

function endpointVariant(status: string): "success" | "muted" {
  return status === "ACTIVE" ? "success" : "muted";
}
function deliveryVariant(status: string): "success" | "destructive" | "warning" | "muted" {
  switch (status) {
    case "DELIVERED":
      return "success";
    case "FAILED":
      return "destructive";
    case "PENDING":
      return "warning";
    default:
      return "muted";
  }
}

function WebhooksPage() {
  const qc = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);
  const [url, setUrl] = useState("");
  const [events, setEvents] = useState("");
  const [signingSecret, setSigningSecret] = useState("");
  const [deliveriesFor, setDeliveriesFor] = useState<Endpoint | null>(null);
  const [disableTarget, setDisableTarget] = useState<Endpoint | null>(null);

  const endpointsQ = useQuery({
    queryKey: ["webhook-endpoints"],
    queryFn: () => api<Endpoint[]>("/v1/webhooks/endpoints"),
    staleTime: 15_000,
  });

  const deliveriesQ = useQuery({
    queryKey: ["webhook-deliveries", deliveriesFor?.id],
    queryFn: () => api<Delivery[]>(`/v1/webhooks/endpoints/${deliveriesFor?.id}/deliveries`),
    enabled: Boolean(deliveriesFor),
    staleTime: 10_000,
  });

  const addM = useMutation({
    mutationFn: () => api<Endpoint>("/v1/webhooks/endpoints", { method: "POST", body: { url, events, signingSecret } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["webhook-endpoints"] });
      closeAdd();
    },
  });

  const disableM = useMutation({
    mutationFn: (id: string) => api<void>(`/v1/webhooks/endpoints/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["webhook-endpoints"] });
      setDisableTarget(null);
    },
  });

  function closeAdd() {
    setAddOpen(false);
    setUrl("");
    setEvents("");
    setSigningSecret("");
    addM.reset();
  }

  const endpoints = endpointsQ.data ?? [];
  const canAdd = url.trim() !== "" && signingSecret.trim() !== "";
  const deliveries = deliveriesQ.data ?? [];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Register HTTPS endpoints to receive signed event callbacks, and inspect their delivery history." />

      <Card>
        <CardHeader>
          <CardTitle>Endpoints</CardTitle>
          <CardDescription>Signed event callbacks are retried until delivered or exhausted.</CardDescription>
          <CardAction>
            <Button size="sm" onClick={() => setAddOpen(true)}>
              <PlusIcon /> Add endpoint
            </Button>
          </CardAction>
        </CardHeader>
        <CardContent>
          <DataState
            isLoading={endpointsQ.isLoading}
            isError={endpointsQ.isError}
            error={endpointsQ.error}
            isEmpty={endpoints.length === 0}
            empty={
              <EmptyState
                icon={WebhookIcon}
                title="No webhook endpoints"
                description="Add an endpoint to start receiving signed event callbacks."
                action={
                  <Button size="sm" onClick={() => setAddOpen(true)}>
                    <PlusIcon /> Add endpoint
                  </Button>
                }
              />
            }
            skeletonRows={4}
          >
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Endpoint URL</TableHead>
                    <TableHead>Events</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {endpoints.map((e) => (
                    <TableRow key={e.id}>
                      <TableCell className="max-w-xs truncate font-medium">{e.url}</TableCell>
                      <TableCell className="text-muted-foreground">{e.events || "all"}</TableCell>
                      <TableCell>
                        <Badge variant={endpointVariant(e.status)}>{e.status}</Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button variant="outline" size="sm" onClick={() => setDeliveriesFor(e)}>
                            Deliveries
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={e.status !== "ACTIVE"}
                            onClick={() => setDisableTarget(e)}
                          >
                            Disable
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </DataState>
        </CardContent>
      </Card>

      {/* Add endpoint */}
      <Sheet open={addOpen} onOpenChange={(open) => (open ? setAddOpen(true) : closeAdd())}>
        <SheetContent side="right">
          <SheetHeader>
            <SheetTitle>Add endpoint</SheetTitle>
            <SheetDescription>Qeet Pay signs every payload with your signing secret.</SheetDescription>
          </SheetHeader>

          <form
            className="flex flex-1 flex-col gap-4 overflow-y-auto px-4"
            onSubmit={(e) => {
              e.preventDefault();
              if (canAdd) addM.mutate();
            }}
          >
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="url">Endpoint URL</FieldLabel>
                <Input id="url" type="url" value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://example.com/webhooks/qeet-pay" autoFocus />
              </Field>
              <Field>
                <FieldLabel htmlFor="events">Event types</FieldLabel>
                <Input id="events" value={events} onChange={(e) => setEvents(e.target.value)} placeholder="payment.captured, payout.processed" />
                <FieldDescription>Comma-separated event types. Leave blank to receive all events.</FieldDescription>
              </Field>
              <Field>
                <FieldLabel htmlFor="secret">Signing secret</FieldLabel>
                <Input id="secret" value={signingSecret} onChange={(e) => setSigningSecret(e.target.value)} placeholder="whsec_…" />
                <FieldDescription>Used to compute the HMAC signature on each delivery.</FieldDescription>
              </Field>
            </FieldGroup>

            {addM.isError && (
              <p className="text-sm text-destructive">
                {addM.error instanceof ApiError ? addM.error.message : "Could not register the endpoint."}
              </p>
            )}
          </form>

          <Separator />
          <SheetFooter>
            <Button type="button" disabled={!canAdd || addM.isPending} onClick={() => canAdd && addM.mutate()}>
              {addM.isPending ? "Saving…" : "Register endpoint"}
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

      {/* Deliveries */}
      <Sheet open={deliveriesFor !== null} onOpenChange={(open) => (open ? undefined : setDeliveriesFor(null))}>
        <SheetContent side="right" className="sm:max-w-lg">
          <SheetHeader>
            <SheetTitle>Delivery attempts</SheetTitle>
            <SheetDescription className="truncate">{deliveriesFor?.url}</SheetDescription>
          </SheetHeader>
          <div className="flex-1 overflow-y-auto px-4">
            <DataState
              isLoading={deliveriesQ.isLoading}
              isError={deliveriesQ.isError}
              error={deliveriesQ.error}
              isEmpty={deliveries.length === 0}
              emptyIcon={SendIcon}
              emptyTitle="No deliveries yet"
              emptyDescription="No events have been dispatched to this endpoint."
              skeletonRows={4}
            >
              <ul className="space-y-3">
                {deliveries.map((d) => (
                  <li key={d.id} className="rounded-lg border p-3">
                    <div className="flex items-center justify-between gap-2">
                      <span className="truncate font-medium">{d.eventType}</span>
                      <Badge variant={deliveryVariant(d.status)}>{d.status}</Badge>
                    </div>
                    <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                      <span>Attempts: {d.attemptCount}</span>
                      {d.lastResponseCode != null && <span>HTTP {d.lastResponseCode}</span>}
                      {d.deliveredAt && (
                        <span>
                          Delivered <TimeSince value={d.deliveredAt} />
                        </span>
                      )}
                    </div>
                    {d.lastError && <p className="mt-1 text-xs text-destructive">{d.lastError}</p>}
                  </li>
                ))}
              </ul>
            </DataState>
          </div>
          <Separator />
          <SheetFooter>
            <SheetClose
              render={
                <Button variant="outline" type="button">
                  Close
                </Button>
              }
            />
          </SheetFooter>
        </SheetContent>
      </Sheet>

      {/* Disable confirm */}
      <Dialog open={disableTarget !== null} onOpenChange={(open) => (open ? undefined : setDisableTarget(null))}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Disable endpoint?</DialogTitle>
            <DialogDescription className="truncate">
              {disableTarget?.url} will stop receiving events. This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose
              render={
                <Button variant="outline" type="button">
                  Cancel
                </Button>
              }
            />
            <Button
              variant="destructive"
              type="button"
              disabled={disableM.isPending}
              onClick={() => disableTarget && disableM.mutate(disableTarget.id)}
            >
              {disableM.isPending ? "Disabling…" : "Disable"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
