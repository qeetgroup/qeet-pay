"use client";

import { Card, CardContent, cn } from "@qeetrix/ui";
import { QeetLogo } from "@qeetrix/ui/brand";
import type { ReactNode } from "react";

type CheckoutCardProps = {
  title: ReactNode;
  subtitle?: ReactNode;
  children?: ReactNode;
  className?: string;
};

/**
 * The consistent card frame for every hosted-checkout screen: a centered Qeet
 * mark + display-font title + subtitle, then the body (the pay form, a receipt,
 * or a status message). Lives inside <CheckoutShell>'s right column, and owns
 * the logo so the Qeet Pay mark shows on the light card surface — including on
 * mobile, where the brand panel is hidden.
 */
export function CheckoutCard({ title, subtitle, children, className }: CheckoutCardProps) {
  return (
    <Card
      className={cn(
        "w-full max-w-sm border-border/60 shadow-xl shadow-black/5 backdrop-blur-sm",
        className,
      )}
    >
      <CardContent className="space-y-6 pt-7 pb-7">
        <div className="space-y-4 text-center">
          <div className="flex justify-center">
            <QeetLogo size={40} />
          </div>
          <div className="space-y-1.5">
            <h1 className="pay-title text-2xl font-semibold tracking-tight">{title}</h1>
            {subtitle ? <p className="text-muted-foreground text-sm">{subtitle}</p> : null}
          </div>
        </div>
        {children}
      </CardContent>
    </Card>
  );
}
