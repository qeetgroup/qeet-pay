import {
  Button,
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
  Input,
} from "@qeetrix/ui";
import { QeetLogo } from "@qeetrix/ui/brand";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
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
      setError("API key is required");
      return;
    }
    keyStore.set(trimmed);
    navigate({ to: "/" as never, replace: true });
  }

  return (
    <div className="flex min-h-svh items-center justify-center bg-background">
      <div className="w-full max-w-sm space-y-6 px-4">
        <div className="flex flex-col items-center gap-2 text-center">
          <QeetLogo className="h-8 w-auto" />
          <h1 className="text-xl font-semibold">Sign in to Pay</h1>
          <p className="text-sm text-muted-foreground">
            Enter your Qeet Pay API key to access the console.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <FieldGroup>
            <Field>
              <FieldLabel>API Key</FieldLabel>
              <Input
                type="password"
                placeholder="qp_live_…"
                value={apiKey}
                onChange={(e) => {
                  setApiKey(e.target.value);
                  setError("");
                }}
                autoFocus
              />
              {error && <FieldError>{error}</FieldError>}
            </Field>
          </FieldGroup>
          <Button type="submit" className="w-full">
            Continue
          </Button>
        </form>

        <p className="text-center text-xs text-muted-foreground">
          Find your API key in Settings → API Keys.
        </p>
      </div>
    </div>
  );
}
