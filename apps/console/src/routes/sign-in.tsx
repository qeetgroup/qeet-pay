import { Button, Card, CardContent, Field, FieldError, FieldLabel, Input } from "@qeetrix/ui";
import { QeetLogo } from "@qeetrix/ui/brand";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { KeyRoundIcon, ShieldCheckIcon } from "lucide-react";
import { useState } from "react";

import { keyStore } from "@/lib/api";

export const Route = createFileRoute("/sign-in")({ component: SignInPage });

function SignInPage() {
  const navigate = useNavigate();
  const [apiKey, setApiKey] = useState("");
  const [error, setError] = useState("");

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = apiKey.trim();
    if (!trimmed) {
      setError("Enter your Qeet Pay API key to continue.");
      return;
    }
    keyStore.set(trimmed);
    navigate({ to: "/" as never, replace: true });
  }

  return (
    <div className="relative flex min-h-svh items-center justify-center overflow-hidden bg-background p-4">
      {/* Ambient brand glow + fine dotted texture for depth. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 qp-dotted opacity-60"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -top-40 left-1/2 size-168 -translate-x-1/2 rounded-full opacity-40 blur-3xl"
        style={{
          background:
            "radial-gradient(circle, color-mix(in oklab, var(--primary) 40%, transparent) 0%, transparent 65%)",
        }}
      />

      <div className="relative w-full max-w-sm qp-rise">
        <div className="mb-6 flex flex-col items-center gap-3 text-center">
          <QeetLogo className="h-8 w-auto" />
          <div className="space-y-1">
            <h1 className="font-heading text-2xl font-semibold tracking-tight">
              Sign in to Qeet Pay
            </h1>
            <p className="text-sm text-muted-foreground">
              Access the operator console with your API key.
            </p>
          </div>
        </div>

        <Card>
          <CardContent className="space-y-5 pt-6 pb-6">
            <form onSubmit={handleSubmit} className="space-y-4">
              <Field>
                <FieldLabel htmlFor="api-key">API key</FieldLabel>
                <div className="relative">
                  <KeyRoundIcon className="pointer-events-none absolute inset-s-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    id="api-key"
                    type="password"
                    placeholder="qp_live_…"
                    className="ps-9 font-mono"
                    value={apiKey}
                    onChange={(e) => {
                      setApiKey(e.target.value);
                      setError("");
                    }}
                    autoFocus
                    autoComplete="off"
                  />
                </div>
                {error && <FieldError>{error}</FieldError>}
              </Field>
              <Button type="submit" size="lg" className="w-full">
                Continue
              </Button>
            </form>

            <div className="flex items-start gap-2 rounded-lg bg-muted/50 p-3 text-xs text-muted-foreground">
              <ShieldCheckIcon className="mt-0.5 size-3.5 shrink-0 text-success" />
              <span>
                Your key is stored only in this browser and sent as{" "}
                <code className="font-mono">X-Api-Key</code> to the Qeet Pay API. Find it in Settings
                → API keys.
              </span>
            </div>
          </CardContent>
        </Card>

        <p className="mt-6 text-center text-xs text-muted-foreground">
          Qeet Pay · Operator Console
        </p>
      </div>
    </div>
  );
}
