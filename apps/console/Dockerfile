# syntax=docker/dockerfile:1

# ==============================================================================
# Qeet Pay — operator console (TanStack Start + Vite + Nitro) production image.
#
# Multi-stage:
#   1. build   — node:22-alpine + pnpm (via corepack). Emits a Nitro node-server
#                bundle under .output/ (preset: node-server, see vite.config.ts).
#   2. runtime — node:22-alpine, runs `node .output/server/index.mjs` as NON-root.
#
# Port 3201 (see CLAUDE.md). VITE_API_URL is baked at BUILD time — Vite inlines
# import.meta.env.VITE_API_URL into the browser bundle (src/lib/api.ts), so it
# must be the URL the BROWSER uses to reach the backend, not an internal name.
# ==============================================================================

# --- Stage 1: install deps + build the SSR/Nitro bundle ----------------------
FROM node:22-alpine AS build
WORKDIR /app

# pnpm via corepack, pinned to the pnpm 9 line (pnpm-lock.yaml is lockfileVersion 9.0).
# COREPACK_ENABLE_DOWNLOAD_PROMPT=0 keeps the non-interactive build from stalling.
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

# Install from the lockfile first for a cacheable dependency layer.
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# Browser API base URL — inlined into the client bundle at build time.
ARG VITE_API_URL=http://localhost:4201
ENV VITE_API_URL=${VITE_API_URL}

# Build the app; Nitro's node-server preset writes the standalone bundle to .output/.
COPY . .
RUN pnpm build

# --- Stage 2: minimal runtime (only the Nitro output) ------------------------
FROM node:22-alpine AS runtime
WORKDIR /app

# PORT/HOST are read by the Nitro node-server; bind all interfaces on 3201.
ENV NODE_ENV=production \
    PORT=3201 \
    HOST=0.0.0.0

# The node-server output is self-contained (deps bundled) — no node_modules needed.
COPY --from=build --chown=node:node /app/.output ./.output

# node:22-alpine ships an unprivileged `node` user (uid 1000).
USER node

EXPOSE 3201

# Nitro serves the SSR app on / ; Node 22's global fetch keeps the check dep-free.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||3201)+'/').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", ".output/server/index.mjs"]