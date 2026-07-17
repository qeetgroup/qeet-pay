import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactCompiler: true,
  transpilePackages: ["@qeetrix/ui"],
  // Marketing site uses raster images (avatars) via @qeetrix/ui's <Avatar>
  // (plain <img>, not next/image) plus SVG/PNG icons — so on-the-fly image
  // optimization (sharp) isn't required. Declining sharp's native build in
  // pnpm-workspace.yaml keeps install/`pnpm build` prompt-free; `unoptimized`
  // makes any future next/image usage fall back gracefully with no sharp gate.
  images: { unoptimized: true },
  allowedDevOrigins: ["pay.qeet.localhost"],
};

export default nextConfig;
