import { Card, CardDescription, CardHeader, CardTitle } from "@qeetrix/ui";
import { Link, Outlet, createFileRoute, useLocation } from "@tanstack/react-router";
import { BuildingIcon, ChevronRightIcon, KeyRoundIcon } from "lucide-react";

import { PageHeader } from "@/components/page-header";

export const Route = createFileRoute("/_app/settings")({ component: SettingsPage });

const SECTIONS = [
  {
    to: "/settings/api-keys",
    title: "API Keys",
    description: "View the API key this console uses, rotate it, or clear it.",
    icon: <KeyRoundIcon />,
  },
  {
    to: "/settings/merchant",
    title: "Merchant",
    description: "Your merchant identity, roles, and verification state.",
    icon: <BuildingIcon />,
  },
];

function SettingsPage() {
  const { pathname } = useLocation();
  const isRoot = pathname === "/settings" || pathname === "/settings/";

  return (
    <>
      {isRoot && (
        <div className="flex min-w-0 flex-col gap-4">
          <PageHeader description="Manage the credentials and merchant profile this console operates under." />
          <div className="grid gap-4 sm:grid-cols-2">
            {SECTIONS.map((s) => (
              <Link key={s.to} to={s.to as never} className="group">
                <Card className="h-full transition-colors group-hover:border-primary/40">
                  <CardHeader>
                    <div className="flex items-start gap-3">
                      <div className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary [&_svg]:size-5">
                        {s.icon}
                      </div>
                      <div className="min-w-0 flex-1">
                        <CardTitle className="flex items-center justify-between gap-2">
                          {s.title}
                          <ChevronRightIcon className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
                        </CardTitle>
                        <CardDescription className="mt-1">{s.description}</CardDescription>
                      </div>
                    </div>
                  </CardHeader>
                </Card>
              </Link>
            ))}
          </div>
        </div>
      )}
      <Outlet />
    </>
  );
}
