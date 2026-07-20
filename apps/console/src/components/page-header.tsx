import type * as React from "react";
import { useLocation } from "@tanstack/react-router";

import { lookupNavTitle } from "@/config/navigation";

type PageHeaderProps = {
  /** Overrides the auto-detected title (useful for detail pages). */
  title?: string;
  /** One-line description shown below the title. */
  description?: string;
  /** Optional action area (buttons, dropdowns) shown on the right side. */
  actions?: React.ReactNode;
};

/**
 * Standard top-of-page header: an uppercase domain eyebrow, a display-face
 * title, and a supporting description, with an actions slot on the right. Title
 * auto-resolves from the navigation registry by pathname — override with the
 * `title` prop for detail screens whose path isn't in the static nav tree.
 */
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  const { pathname } = useLocation();
  const meta = lookupNavTitle(pathname);
  const eyebrow = meta.parent?.title ?? meta.group;

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex min-w-0 flex-col gap-1.5">
        {eyebrow && (
          <span className="text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
            {eyebrow}
          </span>
        )}
        <h1 className="font-heading text-2xl font-semibold tracking-tight text-balance">
          {title ?? meta.title}
        </h1>
        {description && (
          <p className="max-w-2xl text-sm leading-relaxed text-muted-foreground text-pretty">
            {description}
          </p>
        )}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
