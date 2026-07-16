import { ThemeProvider } from "@qeetrix/ui";
import type { Metadata } from "next";
import { OrganizationJsonLd, WebSiteJsonLd } from "@/components/marketing/structured-data";
import { SITE_URL } from "@/lib/links";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "Qeet Pay — One API for payments, billing & GST",
    template: "%s | Qeet Pay",
  },
  description:
    "Qeet Pay is India's unified payments platform. UPI, cards, NACH & net banking acceptance, payouts, subscription billing, and GST-compliant invoicing with IRN e-invoicing — one API, one dashboard, one reconciliation.",
  metadataBase: new URL(SITE_URL),
  alternates: {
    canonical: "/",
  },
  icons: {
    // Theme-adaptive favicon: dark artwork on light UI, light artwork on dark
    // UI. The .ico is the universal fallback for browsers without SVG support.
    icon: [
      {
        url: "/qeet-logo-on-light.svg",
        type: "image/svg+xml",
        media: "(prefers-color-scheme: light)",
      },
      {
        url: "/qeet-logo-on-dark.svg",
        type: "image/svg+xml",
        media: "(prefers-color-scheme: dark)",
      },
      { url: "/favicon.ico", sizes: "48x48" },
    ],
    shortcut: ["/favicon.ico"],
    apple: [{ url: "/apple-icon.png", type: "image/png", sizes: "180x180" }],
  },
  openGraph: {
    title: "Qeet Pay — One API for payments, billing & GST",
    description:
      "India-first payments, billing & GST infrastructure. UPI, cards, NACH, payouts, subscriptions, and IRN e-invoicing — in one API.",
    type: "website",
    siteName: "Qeet Pay",
    locale: "en_IN",
    url: SITE_URL,
  },
  twitter: {
    card: "summary_large_image",
    title: "Qeet Pay — One API for payments, billing & GST",
    description:
      "India-first payments, billing & GST infrastructure. UPI, cards, NACH, payouts, subscriptions, and IRN e-invoicing — in one API.",
    site: "@qeetpay",
    creator: "@qeetpay",
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-snippet": -1,
      "max-image-preview": "large",
      "max-video-preview": -1,
    },
  },
  keywords: [
    "payment gateway India",
    "UPI payments",
    "GST invoicing",
    "e-invoicing IRN",
    "subscription billing India",
    "payment orchestration",
    "Razorpay alternative",
    "Chargebee alternative",
    "NACH mandates",
    "payouts API",
  ],
  authors: [{ name: "Qeet Pay", url: SITE_URL }],
  creator: "Qeet Pay",
  publisher: "Qeet Pay",
};

const STORAGE_KEY = "qeetpay-web-theme";

const themeBootstrap = `(function(){try{var t=localStorage.getItem('${STORAGE_KEY}')||'system';var r=t==='system'?(window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light'):t;document.documentElement.classList.add(r);}catch(e){}})();`;

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      data-scroll-behavior="smooth"
      className="h-full antialiased"
      suppressHydrationWarning
    >
      <head>
        {/* Set theme class before first paint to avoid FOUC. */}
        <script dangerouslySetInnerHTML={{ __html: themeBootstrap }} />
      </head>
      <body className="font-sans">
        {/* Site-wide publisher + website schema. Emitted once in the root
            layout so it covers every route (marketing + any future surface). */}
        <OrganizationJsonLd />
        <WebSiteJsonLd />
        <ThemeProvider defaultTheme="system" storageKey={STORAGE_KEY}>
          {children}
        </ThemeProvider>
      </body>
    </html>
  );
}
