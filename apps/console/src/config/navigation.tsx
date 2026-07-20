import {
  BanknoteIcon,
  BarChart3Icon,
  BuildingIcon,
  CalendarClockIcon,
  CreditCardIcon,
  FileCheck2Icon,
  FileTextIcon,
  GaugeIcon,
  GlobeIcon,
  HandCoinsIcon,
  LandmarkIcon,
  LayoutDashboardIcon,
  LeafIcon,
  LinkIcon,
  MessagesSquareIcon,
  PiggyBankIcon,
  ReceiptIcon,
  RefreshCwIcon,
  RepeatIcon,
  RouteIcon,
  ScaleIcon,
  Settings2Icon,
  ShieldCheckIcon,
  SplitIcon,
  TrendingUpIcon,
  UmbrellaIcon,
  UsersIcon,
  WebhookIcon,
} from "lucide-react";
import type { ReactNode } from "react";

export type NavItem = {
  title: string;
  url: string;
  icon?: ReactNode;
  isActive?: boolean;
  items?: { title: string; url: string }[];
};

export type NavGroup = {
  label: string;
  items: NavItem[];
};

// Domain-grouped navigation registry. Only routes whose files exist today are
// listed; the grouping establishes stable slots so new backend modules drop
// into the right section without reshuffling. `url`s must match the file-based
// routes under src/routes/_app.
export const navGroups: NavGroup[] = [
  {
    label: "Overview",
    items: [
      { title: "Dashboard", url: "/", icon: <LayoutDashboardIcon /> },
      { title: "Analytics", url: "/analytics", icon: <BarChart3Icon /> },
      { title: "Cash Flow", url: "/cash-flow", icon: <TrendingUpIcon /> },
    ],
  },
  {
    label: "Payments",
    items: [
      { title: "Payments", url: "/payments", icon: <CreditCardIcon /> },
      { title: "Payment Links", url: "/payment-links", icon: <LinkIcon /> },
      { title: "Refunds", url: "/refunds", icon: <RefreshCwIcon /> },
      { title: "Orchestration", url: "/orchestration", icon: <RouteIcon /> },
    ],
  },
  {
    label: "Payouts & Ledger",
    items: [
      { title: "Payouts", url: "/payouts", icon: <BanknoteIcon /> },
      { title: "Bulk Batches", url: "/payout-batches", icon: <UsersIcon /> },
      { title: "Ledger", url: "/ledger", icon: <ScaleIcon /> },
      { title: "Reconciliation", url: "/reconciliation", icon: <FileCheck2Icon /> },
      { title: "Revenue Recognition", url: "/revrec", icon: <CalendarClockIcon /> },
    ],
  },
  {
    label: "Billing",
    items: [
      { title: "Plans", url: "/plans", icon: <GaugeIcon /> },
      { title: "Subscriptions", url: "/subscriptions", icon: <RepeatIcon /> },
      { title: "Invoices", url: "/invoices", icon: <ReceiptIcon /> },
      { title: "Mandates", url: "/mandates", icon: <FileTextIcon /> },
      { title: "Dunning", url: "/dunning", icon: <RefreshCwIcon /> },
    ],
  },
  {
    label: "Tax & GST",
    items: [
      { title: "GST Invoices", url: "/gst-invoices", icon: <ReceiptIcon /> },
      { title: "E-Invoicing (IRN)", url: "/einvoicing", icon: <FileCheck2Icon /> },
      { title: "GST Returns", url: "/gst-returns", icon: <FileTextIcon /> },
      { title: "ITC / GSTR-2B", url: "/itc", icon: <ScaleIcon /> },
      { title: "TDS / TCS", url: "/tds", icon: <LandmarkIcon /> },
    ],
  },
  {
    label: "Embedded Finance",
    items: [
      { title: "Lending", url: "/lending", icon: <HandCoinsIcon /> },
      { title: "BNPL", url: "/bnpl", icon: <CalendarClockIcon /> },
      { title: "Cards", url: "/cards", icon: <CreditCardIcon /> },
      { title: "Insurance", url: "/insurance", icon: <UmbrellaIcon /> },
      { title: "Escrow", url: "/escrow", icon: <PiggyBankIcon /> },
    ],
  },
  {
    label: "Collections",
    items: [
      { title: "Virtual Accounts", url: "/virtual-accounts", icon: <BuildingIcon /> },
      { title: "Cross-Border", url: "/crossborder", icon: <GlobeIcon /> },
    ],
  },
  {
    label: "Marketplace",
    items: [
      { title: "Marketplace", url: "/marketplace", icon: <SplitIcon /> },
      { title: "Messaging", url: "/messaging", icon: <MessagesSquareIcon /> },
      { title: "ESG / Carbon", url: "/esg", icon: <LeafIcon /> },
    ],
  },
  {
    label: "Risk & Compliance",
    items: [{ title: "KYB", url: "/kyb", icon: <ShieldCheckIcon /> }],
  },
  {
    label: "Developer",
    items: [{ title: "Webhooks", url: "/webhooks", icon: <WebhookIcon /> }],
  },
  {
    label: "Settings",
    items: [
      {
        title: "Settings",
        url: "/settings",
        icon: <Settings2Icon />,
        items: [
          { title: "API Keys", url: "/settings/api-keys" },
          { title: "Merchant", url: "/settings/merchant" },
        ],
      },
    ],
  },
];

export type NavTitleLookup = {
  group?: string;
  parent?: { title: string; url: string };
  title: string;
};

function titleFromSlug(slug: string) {
  return slug
    .split("-")
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
}

export function lookupNavTitle(pathname: string): NavTitleLookup {
  for (const group of navGroups) {
    for (const item of group.items) {
      if (item.url === pathname) {
        return { group: group.label, title: item.title };
      }
      const sub = item.items?.find((s) => s.url === pathname);
      if (sub) {
        return {
          group: group.label,
          parent: { title: item.title, url: item.url },
          title: sub.title,
        };
      }
    }
  }
  const segments = pathname.split("/").filter(Boolean);
  return { title: titleFromSlug(segments[segments.length - 1] ?? "Page") };
}
