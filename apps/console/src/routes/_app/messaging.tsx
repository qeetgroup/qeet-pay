import {
  Badge,
  Button,
  Card,
  CardContent,
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
  Textarea,
  TimeSince,
} from "@qeetrix/ui";
import {
  type UseQueryResult,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
  FileTextIcon,
  PlusIcon,
  SendIcon,
  Trash2Icon,
} from "lucide-react";
import { useState } from "react";

import { ListToolbar } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";

export const Route = createFileRoute("/_app/messaging")({
  component: MessagingPage,
});

const CHANNELS = ["WHATSAPP", "SMS", "EMAIL"] as const;
type Channel = (typeof CHANNELS)[number];

type Template = {
  id: string;
  templateKey: string;
  channel: Channel;
  body: string;
  active: boolean;
  createdAt: string;
};

type Dispatch = {
  id: string;
  templateKey: string;
  channel: Channel;
  recipient: string;
  renderedBody: string;
  status: "QUEUED" | "SENT" | "FAILED";
  providerRef: string | null;
  relatedRef: string | null;
  failureReason: string | null;
  createdAt: string;
  sentAt: string | null;
};

function channelBadge(channel: Channel) {
  const variant = channel === "WHATSAPP" ? "success" : channel === "SMS" ? "secondary" : "outline";
  return <Badge variant={variant}>{channel}</Badge>;
}

function dispatchBadge(status: Dispatch["status"]) {
  if (status === "SENT") return <Badge variant="success">Sent</Badge>;
  if (status === "FAILED") return <Badge variant="destructive">Failed</Badge>;
  return <Badge variant="warning">Queued</Badge>;
}

function errMsg(e: unknown): string {
  return e instanceof ApiError ? e.message : e instanceof Error ? e.message : "Something went wrong.";
}

function MessagingPage() {
  const [templateOpen, setTemplateOpen] = useState(false);
  const [dispatchOpen, setDispatchOpen] = useState(false);

  const templatesQ = useQuery({
    queryKey: ["messaging-templates"],
    queryFn: () => api<Template[]>("/v1/messaging/templates"),
    staleTime: 15_000,
  });

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Configure WhatsApp / SMS / email templates and dispatch rendered messages through qeet-notify." />

      <TemplatesSection templatesQ={templatesQ} onUpsert={() => setTemplateOpen(true)} />
      <DispatchSection templates={templatesQ.data ?? []} onDispatch={() => setDispatchOpen(true)} />

      <TemplateSheet open={templateOpen} onOpenChange={setTemplateOpen} />
      <DispatchSheet open={dispatchOpen} onOpenChange={setDispatchOpen} templates={templatesQ.data ?? []} />
    </div>
  );
}

// ── Templates ──────────────────────────────────────────────────────────────

function TemplatesSection({
  templatesQ,
  onUpsert,
}: {
  templatesQ: UseQueryResult<Template[]>;
  onUpsert: () => void;
}) {
  const rows = templatesQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (r) => [r.templateKey, r.body, r.channel],
    filterFields: { channel: (r) => r.channel },
  });

  return (
    <Card className="py-0">
      <div className="flex items-center justify-between border-b p-3">
        <h2 className="flex items-center gap-2 text-sm font-medium">
          <FileTextIcon className="size-4 text-muted-foreground" /> Templates
        </h2>
        <Button size="sm" onClick={onUpsert}>
          <PlusIcon /> New template
        </Button>
      </div>

      <ListToolbar
        search={lv.search}
        onSearchChange={lv.setSearch}
        searchPlaceholder="Search template key or body…"
        filters={[
          {
            id: "channel",
            label: "Channel",
            value: lv.filters.channel ?? "",
            options: CHANNELS.map((c) => ({ label: c, value: c })),
            onChange: (v) => lv.setFilter("channel", v),
          },
        ]}
        hasActiveFilters={lv.hasActiveFilters}
        onClear={lv.clear}
        exportDisabled={lv.view.length === 0}
        onExport={(fmt) =>
          fmt === "csv"
            ? exportToCsv("messaging-templates", lv.view, [
                { header: "Template Key", value: (r) => r.templateKey },
                { header: "Channel", value: (r) => r.channel },
                { header: "Body", value: (r) => r.body },
                { header: "Active", value: (r) => r.active },
              ])
            : exportToJson("messaging-templates", lv.view)
        }
      />

      <CardContent className="p-0">
        <DataState
          isLoading={templatesQ.isLoading}
          isError={templatesQ.isError}
          error={templatesQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={FileTextIcon}
          emptyTitle="No templates"
          emptyDescription="Create a template with {{variables}} before dispatching messages."
          skeletonRows={4}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Template Key</TableHead>
                <TableHead>Channel</TableHead>
                <TableHead>Body</TableHead>
                <TableHead>Active</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="py-2 font-medium tabular-nums">{r.templateKey}</TableCell>
                  <TableCell className="py-2">{channelBadge(r.channel)}</TableCell>
                  <TableCell className="max-w-md truncate py-2 text-muted-foreground">{r.body}</TableCell>
                  <TableCell className="py-2">
                    {r.active ? <Badge variant="success">Active</Badge> : <Badge variant="muted">Inactive</Badge>}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </CardContent>
    </Card>
  );
}

function TemplateSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient();
  const [templateKey, setTemplateKey] = useState("");
  const [channel, setChannel] = useState<Channel>("WHATSAPP");
  const [body, setBody] = useState("");

  const upsertM = useMutation({
    mutationFn: (payload: { templateKey: string; channel: Channel; body: string }) =>
      api<Template>("/v1/messaging/templates", { method: "PUT", body: payload }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["messaging-templates"] });
      onOpenChange(false);
      setTemplateKey("");
      setChannel("WHATSAPP");
      setBody("");
    },
  });

  const canSubmit = templateKey.trim() !== "" && body.trim() !== "";

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="sm:max-w-lg">
        <form
          className="flex h-full flex-col"
          onSubmit={(e) => {
            e.preventDefault();
            if (canSubmit) upsertM.mutate({ templateKey: templateKey.trim(), channel, body });
          }}
        >
          <SheetHeader>
            <SheetTitle>Upsert template</SheetTitle>
            <SheetDescription>
              Templates are keyed by (template key + channel). Re-saving an existing key updates its body.
              Use {"{{variable}}"} placeholders substituted at dispatch.
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 overflow-y-auto px-4">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="t-key">Template key</FieldLabel>
                <Input id="t-key" value={templateKey} onChange={(e) => setTemplateKey(e.target.value)} placeholder="payment_success" autoFocus required />
              </Field>
              <Field>
                <FieldLabel htmlFor="t-channel">Channel</FieldLabel>
                <Select value={channel} onValueChange={(v) => setChannel(v as Channel)}>
                  <SelectTrigger id="t-channel">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CHANNELS.map((c) => (
                      <SelectItem key={c} value={c}>
                        {c}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="t-body">Body</FieldLabel>
                <Textarea
                  id="t-body"
                  value={body}
                  onChange={(e) => setBody(e.target.value)}
                  rows={6}
                  placeholder="Hi {{name}}, your payment of {{amount}} was received."
                />
                <FieldDescription>Reference variables with double braces, e.g. {"{{name}}"}.</FieldDescription>
              </Field>
            </FieldGroup>
            {upsertM.isError && <p className="mt-3 text-sm text-destructive">{errMsg(upsertM.error)}</p>}
          </div>
          <SheetFooter>
            <div className="flex justify-end gap-2">
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
              <Button type="submit" disabled={!canSubmit || upsertM.isPending}>
                {upsertM.isPending ? "Saving…" : "Save template"}
              </Button>
            </div>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}

// ── Dispatch ─────────────────────────────────────────────────────────────

function DispatchSection({
  templates,
  onDispatch,
}: {
  templates: Template[];
  onDispatch: () => void;
}) {
  const dispatchesQ = useQuery({
    queryKey: ["messaging-dispatches"],
    queryFn: () => api<Dispatch[]>("/v1/messaging/dispatches"),
    staleTime: 15_000,
  });

  const rows = dispatchesQ.data ?? [];
  const lv = useListView(rows, {
    searchFields: (r) => [r.templateKey, r.recipient, r.relatedRef, r.renderedBody],
    filterFields: { status: (r) => r.status, channel: (r) => r.channel },
  });

  return (
    <Card className="py-0">
      <div className="flex items-center justify-between border-b p-3">
        <h2 className="flex items-center gap-2 text-sm font-medium">
          <SendIcon className="size-4 text-muted-foreground" /> Dispatches
        </h2>
        <Button size="sm" onClick={onDispatch} disabled={templates.length === 0}>
          <SendIcon /> Dispatch message
        </Button>
      </div>

      <ListToolbar
        search={lv.search}
        onSearchChange={lv.setSearch}
        searchPlaceholder="Search recipient, template, related ref…"
        filters={[
          {
            id: "status",
            label: "Status",
            value: lv.filters.status ?? "",
            options: [
              { label: "Queued", value: "QUEUED" },
              { label: "Sent", value: "SENT" },
              { label: "Failed", value: "FAILED" },
            ],
            onChange: (v) => lv.setFilter("status", v),
          },
          {
            id: "channel",
            label: "Channel",
            value: lv.filters.channel ?? "",
            options: CHANNELS.map((c) => ({ label: c, value: c })),
            onChange: (v) => lv.setFilter("channel", v),
          },
        ]}
        density={lv.density}
        onDensityChange={lv.setDensity}
        hasActiveFilters={lv.hasActiveFilters}
        onClear={lv.clear}
        exportDisabled={lv.view.length === 0}
        onExport={(fmt) =>
          fmt === "csv"
            ? exportToCsv("messaging-dispatches", lv.view, [
                { header: "Template Key", value: (r) => r.templateKey },
                { header: "Channel", value: (r) => r.channel },
                { header: "Recipient", value: (r) => r.recipient },
                { header: "Status", value: (r) => r.status },
                { header: "Related Ref", value: (r) => r.relatedRef },
                { header: "Created", value: (r) => r.createdAt },
              ])
            : exportToJson("messaging-dispatches", lv.view)
        }
      />

      <CardContent className="p-0">
        <DataState
          isLoading={dispatchesQ.isLoading}
          isError={dispatchesQ.isError}
          error={dispatchesQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={SendIcon}
          emptyTitle="No dispatches"
          emptyDescription="Dispatch a rendered template to send it through qeet-notify."
          skeletonRows={4}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Recipient</TableHead>
                <TableHead>Template</TableHead>
                <TableHead>Channel</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Related</TableHead>
                <TableHead>Created</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lv.view.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="py-2 font-medium tabular-nums">{r.recipient}</TableCell>
                  <TableCell className="py-2 tabular-nums text-muted-foreground">{r.templateKey}</TableCell>
                  <TableCell className="py-2">{channelBadge(r.channel)}</TableCell>
                  <TableCell className="py-2">
                    <span title={r.failureReason ?? undefined}>{dispatchBadge(r.status)}</span>
                  </TableCell>
                  <TableCell className="py-2 tabular-nums text-muted-foreground">{r.relatedRef ?? "—"}</TableCell>
                  <TableCell className="py-2 text-muted-foreground">
                    <TimeSince value={r.createdAt} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DataState>
      </CardContent>
    </Card>
  );
}

type VarDraft = { k: string; v: string };

function DispatchSheet({
  open,
  onOpenChange,
  templates,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  templates: Template[];
}) {
  const qc = useQueryClient();
  const [templateKey, setTemplateKey] = useState("");
  const [channel, setChannel] = useState<Channel>("WHATSAPP");
  const [recipient, setRecipient] = useState("");
  const [relatedRef, setRelatedRef] = useState("");
  const [vars, setVars] = useState<VarDraft[]>([{ k: "", v: "" }]);

  const reset = () => {
    setTemplateKey("");
    setChannel("WHATSAPP");
    setRecipient("");
    setRelatedRef("");
    setVars([{ k: "", v: "" }]);
  };

  const dispatchM = useMutation({
    mutationFn: (payload: {
      templateKey: string;
      channel: Channel;
      recipient: string;
      variables: Record<string, string>;
      relatedRef?: string;
    }) => api<Dispatch>("/v1/messaging/dispatch", { method: "POST", body: payload }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["messaging-dispatches"] });
      onOpenChange(false);
      reset();
    },
  });

  const onPickTemplate = (key: string) => {
    setTemplateKey(key);
    const t = templates.find((x) => x.templateKey === key);
    if (t) setChannel(t.channel);
  };

  const variables = Object.fromEntries(
    vars.filter((p) => p.k.trim() !== "").map((p) => [p.k.trim(), p.v]),
  );
  const canSubmit = templateKey.trim() !== "" && recipient.trim() !== "";
  const uniqueKeys = Array.from(new Set(templates.map((t) => t.templateKey)));

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="sm:max-w-lg">
        <form
          className="flex h-full flex-col"
          onSubmit={(e) => {
            e.preventDefault();
            if (canSubmit) {
              dispatchM.mutate({
                templateKey: templateKey.trim(),
                channel,
                recipient: recipient.trim(),
                variables,
                relatedRef: relatedRef.trim() || undefined,
              });
            }
          }}
        >
          <SheetHeader>
            <SheetTitle>Dispatch message</SheetTitle>
            <SheetDescription>
              Render a template with variables and queue it for delivery through qeet-notify.
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 space-y-4 overflow-y-auto px-4">
            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel htmlFor="d-template">Template</FieldLabel>
                <Select value={templateKey} onValueChange={(v) => onPickTemplate(v as string)}>
                  <SelectTrigger id="d-template">
                    <SelectValue placeholder="Select template" />
                  </SelectTrigger>
                  <SelectContent>
                    {uniqueKeys.map((k) => (
                      <SelectItem key={k} value={k}>
                        {k}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="d-channel">Channel</FieldLabel>
                <Select value={channel} onValueChange={(v) => setChannel(v as Channel)}>
                  <SelectTrigger id="d-channel">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CHANNELS.map((c) => (
                      <SelectItem key={c} value={c}>
                        {c}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="d-recipient">Recipient</FieldLabel>
              <Input
                id="d-recipient"
                value={recipient}
                onChange={(e) => setRecipient(e.target.value)}
                placeholder={channel === "EMAIL" ? "user@example.com" : "+9198XXXXXXXX"}
                required
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="d-related">Related reference</FieldLabel>
              <Input id="d-related" value={relatedRef} onChange={(e) => setRelatedRef(e.target.value)} placeholder="Optional (e.g. payment id)" />
            </Field>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <p className="text-sm font-medium">Variables</p>
                <Button type="button" variant="outline" size="sm" onClick={() => setVars((p) => [...p, { k: "", v: "" }])}>
                  <PlusIcon /> Add
                </Button>
              </div>
              <div className="space-y-2">
                {vars.map((row, idx) => (
                  <div key={idx} className="flex items-center gap-2">
                    <Input
                      value={row.k}
                      onChange={(e) => setVars((p) => p.map((r, i) => (i === idx ? { ...r, k: e.target.value } : r)))}
                      placeholder="name"
                      className="flex-1"
                    />
                    <Input
                      value={row.v}
                      onChange={(e) => setVars((p) => p.map((r, i) => (i === idx ? { ...r, v: e.target.value } : r)))}
                      placeholder="Aditi"
                      className="flex-1"
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label="Remove variable"
                      disabled={vars.length === 1}
                      onClick={() => setVars((p) => p.filter((_, i) => i !== idx))}
                    >
                      <Trash2Icon />
                    </Button>
                  </div>
                ))}
              </div>
            </div>

            {dispatchM.isError && <p className="text-sm text-destructive">{errMsg(dispatchM.error)}</p>}
          </div>
          <SheetFooter>
            <div className="flex justify-end gap-2">
              <SheetClose render={<Button type="button" variant="outline">Cancel</Button>} />
              <Button type="submit" disabled={!canSubmit || dispatchM.isPending}>
                {dispatchM.isPending ? "Dispatching…" : "Dispatch"}
              </Button>
            </div>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}
