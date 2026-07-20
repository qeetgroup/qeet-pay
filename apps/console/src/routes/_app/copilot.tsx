import {
  Badge,
  Button,
  Card,
  DataState,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
  TimeSince,
  cn,
} from "@qeetrix/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { PlusIcon, SendIcon, SparklesIcon } from "lucide-react";
import { useRef, useState } from "react";

import { PageHeader } from "@/components/page-header";
import { api } from "@/lib/api";
import { formatInr } from "@/lib/money";

export const Route = createFileRoute("/_app/copilot")({ component: CopilotPage });

type Surface = "treasury" | "reconciliation" | "query";

type Figure = { key: string; label: string; value: unknown; unit: string };
type CopilotAnswer = {
  conversationId: string;
  messageId: string;
  surface: string;
  question: string;
  answer: string;
  figures: Figure[];
  citations: string[];
  confidence: number;
  fellBack: boolean;
  requiresHumanReview: boolean;
  aiDecisionId: string | null;
  model: string;
  sandbox: boolean;
};
type ConversationSummary = {
  id: string;
  surface: string;
  title: string;
  createdAt: string;
  updatedAt: string;
};
type MessageView = {
  id: string;
  role: string;
  content: string;
  figuresJson: string | null;
  confidence: number | null;
  fellBack: boolean | null;
  aiDecisionId: string | null;
  createdAt: string;
};
type ConversationView = { id: string; surface: string; title: string; messages: MessageView[] };

type ChatMessage = {
  role: "USER" | "ASSISTANT";
  content: string;
  figures?: Figure[];
  citations?: string[];
  confidence?: number;
  fellBack?: boolean;
};

const SURFACES: { value: Surface; label: string; endpoint: string }[] = [
  { value: "treasury", label: "Treasury & cash-flow", endpoint: "/v1/copilot/treasury/ask" },
  { value: "reconciliation", label: "Reconciliation", endpoint: "/v1/copilot/reconciliation/ask" },
  { value: "query", label: "Natural-language query", endpoint: "/v1/copilot/query" },
];

const SUGGESTIONS: Record<Surface, string[]> = {
  treasury: ["What is my projected settlement balance?", "How much did I collect this month?"],
  reconciliation: ["Are there any settlement discrepancies?", "Summarise unreconciled settlements."],
  query: ["Show my top payment methods.", "What is my success rate this week?"],
};

function renderFigure(f: Figure): string {
  switch (f.unit) {
    case "paise":
      return formatInr(Number(f.value));
    case "percent":
      return `${Number(f.value).toFixed(1)}%`;
    case "count":
      return Number(f.value).toLocaleString("en-IN");
    default:
      return String(f.value);
  }
}

function CopilotPage() {
  const qc = useQueryClient();
  const [surface, setSurface] = useState<Surface>("treasury");
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  const conversationsQ = useQuery({
    queryKey: ["copilot-conversations"],
    queryFn: () => api<ConversationSummary[]>("/v1/copilot/conversations"),
    staleTime: 15_000,
  });

  const endpoint = SURFACES.find((s) => s.value === surface)!.endpoint;

  const askM = useMutation({
    mutationFn: (q: string) =>
      api<CopilotAnswer>(endpoint, { method: "POST", body: { conversationId: conversationId ?? undefined, question: q } }),
    onSuccess: (ans) => {
      setConversationId(ans.conversationId);
      setMessages((prev) => [
        ...prev,
        {
          role: "ASSISTANT",
          content: ans.answer,
          figures: ans.figures,
          citations: ans.citations,
          confidence: ans.confidence,
          fellBack: ans.fellBack,
        },
      ]);
      qc.invalidateQueries({ queryKey: ["copilot-conversations"] });
      requestAnimationFrame(() => scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight }));
    },
  });

  function submit(q: string) {
    const trimmed = q.trim();
    if (!trimmed || askM.isPending) return;
    setMessages((prev) => [...prev, { role: "USER", content: trimmed }]);
    setQuestion("");
    askM.mutate(trimmed);
    requestAnimationFrame(() => scrollRef.current?.scrollTo({ top: scrollRef.current!.scrollHeight }));
  }

  function newChat() {
    setConversationId(null);
    setMessages([]);
    askM.reset();
  }

  async function openConversation(id: string) {
    const view = await api<ConversationView>(`/v1/copilot/conversations/${id}`);
    setConversationId(view.id);
    setSurface((view.surface.toLowerCase() as Surface) || "treasury");
    setMessages(
      view.messages.map((m) => {
        let figures: Figure[] | undefined;
        try {
          figures = m.figuresJson ? (JSON.parse(m.figuresJson) as Figure[]) : undefined;
        } catch {
          figures = undefined;
        }
        return {
          role: m.role === "USER" ? "USER" : "ASSISTANT",
          content: m.content,
          figures,
          confidence: m.confidence ?? undefined,
          fellBack: m.fellBack ?? undefined,
        };
      }),
    );
  }

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="Ask about treasury & cash-flow, reconciliation, or your data in plain English. Every answer cites the figures it used and routes through the audited AI gateway." />

      <div className="grid min-h-0 gap-4 lg:grid-cols-[18rem_1fr]">
        {/* Conversations rail */}
        <Card className="flex h-fit max-h-[72vh] flex-col gap-0 overflow-hidden py-0 lg:sticky lg:top-20">
          <div className="flex items-center justify-between border-b p-3">
            <span className="text-sm font-medium">Conversations</span>
            <Button size="sm" variant="outline" onClick={newChat}>
              <PlusIcon /> New
            </Button>
          </div>
          <div className="flex-1 overflow-y-auto p-2">
            <DataState
              isLoading={conversationsQ.isLoading}
              isError={conversationsQ.isError}
              error={conversationsQ.error}
              isEmpty={(conversationsQ.data ?? []).length === 0}
              emptyIcon={SparklesIcon}
              emptyTitle="No conversations"
              emptyDescription="Ask a question to start one."
              skeletonRows={3}
            >
              <ul className="space-y-1">
                {(conversationsQ.data ?? []).map((c) => (
                  <li key={c.id}>
                    <button
                      type="button"
                      onClick={() => openConversation(c.id)}
                      className={cn(
                        "w-full rounded-lg px-3 py-2 text-left transition-colors hover:bg-muted/60",
                        conversationId === c.id && "bg-muted",
                      )}
                    >
                      <p className="truncate text-sm font-medium">{c.title}</p>
                      <p className="text-xs text-muted-foreground">
                        {c.surface.toLowerCase()} · <TimeSince value={c.updatedAt} />
                      </p>
                    </button>
                  </li>
                ))}
              </ul>
            </DataState>
          </div>
        </Card>

        {/* Chat surface */}
        <Card className="flex h-[72vh] flex-col gap-0 overflow-hidden py-0">
          <div className="flex items-center justify-between gap-2 border-b p-3">
            <span className="text-sm font-medium">Copilot</span>
            <Select value={surface} onValueChange={(v) => v && setSurface(v as Surface)}>
              <SelectTrigger size="sm" className="w-auto min-w-52">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {SURFACES.map((s) => (
                  <SelectItem key={s.value} value={s.value}>
                    {s.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div ref={scrollRef} className="flex-1 space-y-4 overflow-y-auto p-4">
            {messages.length === 0 && !askM.isPending && (
              <div className="flex h-full flex-col items-center justify-center gap-4 text-center">
                <span className="grid size-12 place-items-center rounded-2xl bg-primary/10 text-primary [&_svg]:size-6">
                  <SparklesIcon />
                </span>
                <div>
                  <p className="font-heading text-lg font-semibold">Ask the {SURFACES.find((s) => s.value === surface)!.label} copilot</p>
                  <p className="mt-1 text-sm text-muted-foreground">Answers cite the underlying figures.</p>
                </div>
                <div className="flex flex-wrap justify-center gap-2">
                  {SUGGESTIONS[surface].map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => submit(s)}
                      className="rounded-full border px-3 py-1.5 text-sm text-muted-foreground transition-colors hover:border-border hover:bg-muted/60 hover:text-foreground"
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((m, i) => (
              <MessageBubble key={i} message={m} />
            ))}

            {askM.isPending && (
              <div className="flex justify-start">
                <div className="max-w-[85%] rounded-2xl rounded-bl-sm bg-muted px-4 py-2.5 text-sm text-muted-foreground">
                  Thinking…
                </div>
              </div>
            )}
            {askM.isError && (
              <p className="text-sm text-destructive">
                {askM.error instanceof Error ? askM.error.message : "Something went wrong."}
              </p>
            )}
          </div>

          <form
            className="flex items-end gap-2 border-t p-3"
            onSubmit={(e) => {
              e.preventDefault();
              submit(question);
            }}
          >
            <Textarea
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  submit(question);
                }
              }}
              placeholder="Ask a question…"
              rows={1}
              className="max-h-32 min-h-10 flex-1 resize-none"
            />
            <Button type="submit" size="icon" disabled={question.trim() === "" || askM.isPending} aria-label="Send">
              <SendIcon />
            </Button>
          </form>
        </Card>
      </div>
    </div>
  );
}

function MessageBubble({ message }: { message: ChatMessage }) {
  if (message.role === "USER") {
    return (
      <div className="flex justify-end">
        <div className="max-w-[85%] rounded-2xl rounded-br-sm bg-primary px-4 py-2.5 text-sm text-primary-foreground">
          {message.content}
        </div>
      </div>
    );
  }
  return (
    <div className="flex justify-start">
      <div className="max-w-[90%] space-y-3 rounded-2xl rounded-bl-sm bg-muted px-4 py-3">
        <p className="text-sm leading-relaxed whitespace-pre-wrap">{message.content}</p>

        {message.figures && message.figures.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {message.figures.map((f) => (
              <span key={f.key} className="inline-flex items-baseline gap-1.5 rounded-lg bg-background px-2.5 py-1 text-xs ring-1 ring-foreground/10">
                <span className="text-muted-foreground">{f.label}</span>
                <span className="font-semibold tabular-nums">{renderFigure(f)}</span>
              </span>
            ))}
          </div>
        )}

        <div className="flex flex-wrap items-center gap-2">
          {message.confidence != null && (
            <Badge variant={message.fellBack ? "warning" : "success"}>
              {message.fellBack ? "Deterministic" : `${Math.round((message.confidence ?? 0) * 100)}% confidence`}
            </Badge>
          )}
          {message.citations && message.citations.length > 0 && (
            <span className="text-[11px] text-muted-foreground">Sources: {message.citations.join(", ")}</span>
          )}
        </div>
      </div>
    </div>
  );
}
