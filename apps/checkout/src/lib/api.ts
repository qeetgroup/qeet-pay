// Browser HTTP client for the public, payer-facing hosted checkout. Unlike the
// operator console (X-Api-Key) and the hosted login (SSO cookie + CSRF), this
// app is stateless and unauthenticated: the payment-link *code* in the URL is
// the capability, so there is no auth header, no cookie, and no CSRF token —
// which makes this client the simplest of the three. Errors come back as
// RFC-7807 problem+json ({ title, detail, status }); we surface them as ApiError.

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:4201";

export class ApiError extends Error {
  status: number;
  code: string;
  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

function apiURL(path: string): string {
  return new URL(path.replace(/^\//, ""), `${API_BASE_URL}/`).toString();
}

async function parse<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? safeParse(text) : null;
  if (!res.ok) {
    // qeet-pay returns RFC-7807 problem+json: { type, title, status, detail }.
    const pd = data as { title?: string; detail?: string; status?: number } | null;
    throw new ApiError(
      res.status,
      `http_${res.status}`,
      pd?.detail ?? pd?.title ?? res.statusText ?? "Request failed",
    );
  }
  return data as T;
}

function safeParse(s: string): unknown {
  try {
    return JSON.parse(s);
  } catch {
    return s;
  }
}

export async function apiGet<T = unknown>(path: string): Promise<T> {
  const res = await fetch(apiURL(path), {
    method: "GET",
    headers: { Accept: "application/json" },
  });
  return parse<T>(res);
}

export async function apiPost<T = unknown>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(apiURL(path), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return parse<T>(res);
}
