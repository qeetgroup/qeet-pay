import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@qeetrix/ui";
import { Link } from "@tanstack/react-router";
import { ChevronsUpDownIcon, KeyRoundIcon, LogOutIcon, Settings2Icon } from "lucide-react";
import { getApiKey, signOut } from "@/lib/auth";

export function NavUser() {
  const { isMobile } = useSidebar();
  const apiKey = getApiKey();
  const keyPrefix = apiKey ? `${apiKey.slice(0, 12)}…` : "No key";
  const mode = apiKey?.startsWith("qp_live_")
    ? "Live"
    : apiKey?.startsWith("qp_test_")
      ? "Test"
      : "Operator";

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <SidebarMenuButton
                size="lg"
                className="data-open:bg-sidebar-accent data-open:text-sidebar-accent-foreground"
              />
            }
          >
            <div className="grid size-8 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary ring-1 ring-primary/15">
              <KeyRoundIcon className="size-4" />
            </div>
            <div className="grid flex-1 text-left leading-tight">
              <span className="truncate text-sm font-medium">{mode}</span>
              <span className="truncate font-mono text-[11px] text-muted-foreground">{keyPrefix}</span>
            </div>
            <ChevronsUpDownIcon className="ms-auto size-4 text-muted-foreground" />
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-(--radix-dropdown-menu-trigger-width) min-w-60 rounded-lg"
            side={isMobile ? "bottom" : "right"}
            align="end"
            sideOffset={6}
          >
            <DropdownMenuLabel className="p-0 font-normal">
              <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                <div className="grid size-8 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary ring-1 ring-primary/15">
                  <KeyRoundIcon className="size-4" />
                </div>
                <div className="grid flex-1 leading-tight">
                  <span className="truncate font-medium">{mode} operator</span>
                  <span className="truncate font-mono text-[11px] text-muted-foreground">
                    {keyPrefix}
                  </span>
                </div>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem render={<Link to={"/settings/api-keys" as never} />}>
              <KeyRoundIcon />
              API keys
            </DropdownMenuItem>
            <DropdownMenuItem render={<Link to={"/settings/merchant" as never} />}>
              <Settings2Icon />
              Merchant settings
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={signOut} variant="destructive">
              <LogOutIcon />
              Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
