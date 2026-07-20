import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  cn,
} from "@qeetrix/ui";
import type { ReactNode } from "react";

// A consistent section container for the console. Wraps the design-system Card
// with a standard header (title + optional description + action) and body, so
// every dashboard/detail panel reads the same. Use `flush` for surfaces that
// host a full-bleed table or toolbar (no inner padding).

type SectionCardProps = {
  title?: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  /** Remove content padding + top gap — for tables/toolbars that bleed to the edge. */
  flush?: boolean;
  /** Icon shown to the left of the title. */
  icon?: React.ComponentType<{ className?: string }>;
  className?: string;
  contentClassName?: string;
};

export function SectionCard({
  title,
  description,
  action,
  children,
  flush,
  icon: Icon,
  className,
  contentClassName,
}: SectionCardProps) {
  const hasHeader = title != null || description != null || action != null;
  return (
    <Card className={cn(flush && "gap-0 overflow-hidden py-0", className)}>
      {hasHeader && (
        <CardHeader className={cn(flush && "border-b py-4")}>
          {title != null && (
            <CardTitle className="flex items-center gap-2">
              {Icon && <Icon className="size-4 text-muted-foreground" />}
              {title}
            </CardTitle>
          )}
          {description != null && <CardDescription>{description}</CardDescription>}
          {action != null && <CardAction>{action}</CardAction>}
        </CardHeader>
      )}
      {flush ? children : <CardContent className={contentClassName}>{children}</CardContent>}
    </Card>
  );
}
