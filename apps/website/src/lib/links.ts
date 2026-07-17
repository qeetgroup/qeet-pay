// Cross-surface links from the Qeet Pay marketing site. This is a pure
// marketing site: it never calls the backend — it links out to the operator
// console (which hosts the real sign-in / sign-up), the product docs, and the
// developer / API portal. Override any base with the matching NEXT_PUBLIC_*
// env var (see .env.example); the defaults below are the local-dev / prod
// targets.

/** Canonical marketing origin (used by metadata, robots, sitemap, JSON-LD). */
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://pay.qeet.in";

/** Operator console — hosts sign-in / sign-up. Local dev: http://localhost:3201. */
export const CONSOLE_URL = process.env.NEXT_PUBLIC_CONSOLE_URL ?? "http://localhost:3201";

/** Product documentation (docs.qeet.in/pay). */
export const DOCS_URL = process.env.NEXT_PUBLIC_DOCS_URL ?? "https://docs.qeet.in/pay";

/** Developer / API portal (apis.qeet.in). */
export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "https://apis.qeet.in";

/** GitHub org for Qeet Pay. */
export const GITHUB_URL = "https://github.com/qeetgroup/qeet-pay";

export const SIGN_IN_URL = `${CONSOLE_URL}/sign-in`;
export const SIGN_UP_URL = `${CONSOLE_URL}/sign-up`;
