import { Separator, SidebarInset, SidebarProvider, SidebarTrigger } from "@qeetrix/ui";
import { Outlet, createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";

import { AppSidebar } from "@/components/app-sidebar";
import { DynamicBreadcrumb } from "@/components/dynamic-breadcrumb";
import { EnvBadge } from "@/components/env-badge";
import { ThemeToggle } from "@/components/theme-toggle";
import { keyStore } from "@/lib/api";

export const Route = createFileRoute("/_app")({ component: AppLayout });

function AppLayout() {
  const navigate = useNavigate();

  // Guard: require an API key stored locally (set on the sign-in screen).
  useEffect(() => {
    if (!keyStore.get()) {
      navigate({ to: "/sign-in" as never, replace: true });
    }
  }, [navigate]);

  return (
    <SidebarProvider>
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:inset-s-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-background focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:shadow-md focus:ring-2 focus:ring-ring focus:outline-none"
      >
        Skip to main content
      </a>
      <AppSidebar />
      <SidebarInset>
        <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b border-border/70 bg-background/80 px-3 backdrop-blur-md sm:px-4">
          <div className="flex min-w-0 items-center gap-2">
            <SidebarTrigger className="-ml-1" />
            <Separator orientation="vertical" className="mr-1 hidden h-4 lg:block" />
            <DynamicBreadcrumb />
          </div>
          <div className="ml-auto flex shrink-0 items-center gap-1">
            <EnvBadge />
            <Separator orientation="vertical" className="mx-1 hidden h-5 sm:block" />
            <ThemeToggle />
          </div>
        </header>
        <main
          id="main-content"
          tabIndex={-1}
          className="flex min-w-0 flex-1 flex-col focus:outline-none"
        >
          <div className="qp-rise mx-auto flex w-full max-w-[92rem] flex-1 flex-col gap-5 p-4 sm:p-6">
            <Outlet />
          </div>
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
