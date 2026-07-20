import { cn } from "@qeetrix/ui";
import { useEffect, useState } from "react";

import { getApiKey } from "@/lib/auth";

// A small header pill that surfaces whether the console is talking to the API
// with a live (qp_live_) or test (qp_test_) key — the kind of always-visible
// environment cue operators rely on. Client-only (reads localStorage) behind a
// mounted guard so it never causes an SSR hydration mismatch.

type Mode = "live" | "test" | null;

function modeFromKey(key: string | null): Mode {
  if (!key) return null;
  if (key.startsWith("qp_live_")) return "live";
  if (key.startsWith("qp_test_")) return "test";
  return null;
}

export function EnvBadge() {
  const [mode, setMode] = useState<Mode>(null);
  useEffect(() => {
    setMode(modeFromKey(getApiKey()));
  }, []);

  if (!mode) return null;

  const isLive = mode === "live";
  return (
    <span
      className={cn(
        "hidden items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-medium sm:inline-flex",
        isLive
          ? "border-success/30 bg-success/10 text-success"
          : "border-warning/30 bg-warning/10 text-warning",
      )}
      title={isLive ? "Connected with a live API key" : "Connected with a test API key"}
    >
      <span className={cn("size-1.5 rounded-full", isLive ? "bg-success" : "bg-warning")} aria-hidden />
      {isLive ? "Live" : "Test mode"}
    </span>
  );
}
