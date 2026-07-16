// Branding for the Qeet Pay hosted checkout. The checkout is a first-party Qeet
// Pay surface, so unlike the hosted login (which themes per relying-party tenant
// from an API payload) it renders the fixed Qeet Pay brand and merely names the
// merchant collecting the payment. `brandingVars` is kept in the same shape as
// the login's so a future per-merchant theme (logo/colors on the checkout DTO)
// is a drop-in: map the colors onto the @qeetrix/ui design tokens and the whole
// surface — buttons, links, focus rings, brand panel — re-tints with no wiring.

import type { CSSProperties } from "react";

/** The default Qeet Pay brand identity shown on the checkout shell. */
export const BRAND = {
  name: "Qeet Pay",
  tagline: "Payments, done beautifully.",
  /** Qeet orange — Qeetrix token OD-DS-03; the whole suite's `color.brand`. */
  primaryColor: "#f26d0e",
  secondaryColor: "#d85301",
} as const;

export type Branding = {
  logoUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
};

// brandingVars maps brand colors onto the @qeetrix/ui design-token CSS custom
// properties, so buttons, links, focus rings, and the brand panel all pick up
// the color with no component changes. Applied via `style` on a wrapper element
// so it cascades to descendants and overrides both the light and dark token
// values for that subtree. Returns {} when there's nothing to override (the
// default Qeet Pay look, sourced from globals.css).
export function brandingVars(b?: Branding): CSSProperties {
  if (!b?.primaryColor) return {};
  const primary = b.primaryColor;
  const vars: Record<string, string> = {
    "--primary": primary,
    "--primary-foreground": readableForeground(primary),
    "--ring": primary,
    // Brand-panel gradient anchors (consumed by the checkout shell).
    "--qeet-brand": primary,
    "--qeet-brand-2": b.secondaryColor ?? primary,
  };
  return vars as CSSProperties;
}

// readableForeground returns near-black or white depending on the perceived
// luminance of a hex brand color, so label text on the brand color stays
// legible. Falls back to white for non-hex inputs (e.g. named/oklch colors),
// which is the safe default for saturated brand colors.
function readableForeground(color: string): string {
  const rgb = hexToRgb(color);
  if (!rgb) return "#ffffff";
  const [r, g, b] = rgb.map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  }) as [number, number, number];
  const luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  return luminance > 0.5 ? "#0a0a0a" : "#ffffff";
}

function hexToRgb(hex: string): [number, number, number] | null {
  const raw = hex.trim().replace(/^#/, "");
  const full =
    raw.length === 3
      ? raw
          .split("")
          .map((c) => c + c)
          .join("")
      : raw;
  if (!/^[0-9a-fA-F]{6}$/.test(full)) return null;
  return [
    parseInt(full.slice(0, 2), 16),
    parseInt(full.slice(2, 4), 16),
    parseInt(full.slice(4, 6), 16),
  ];
}
