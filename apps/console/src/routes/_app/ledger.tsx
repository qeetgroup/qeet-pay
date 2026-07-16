import {
  Badge,
  Card,
  CardContent,
  DataState,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@qeetrix/ui";
import { useQueries, useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { ScaleIcon } from "lucide-react";

import { ListToolbar, SortHeader } from "@/components/data-table";
import { PageHeader } from "@/components/page-header";
import { api } from "@/lib/api";
import { exportToCsv, exportToJson } from "@/lib/export";
import { useListView } from "@/lib/list-view";
import { formatInr } from "@/lib/money";

export const Route = createFileRoute("/_app/ledger")({ component: LedgerPage });

type AccountView = { id: string; code: string; type: string; currency: string };
type BalanceView = { accountId: string; balanceMinor: number };
type AccountRow = AccountView & { balanceMinor: number; balanceLoading: boolean };

const ACCOUNT_TYPES = [
  "SETTLEMENT",
  "BANK",
  "FEE_EXPENSE",
  "REVENUE",
  "LIABILITY",
  "TAX_PAYABLE",
  "DEFERRED_REVENUE",
];

function LedgerPage() {
  const accountsQ = useQuery({
    queryKey: ["ledger-accounts"],
    queryFn: () => api<AccountView[]>("/v1/ledger/accounts"),
  });

  const accounts = accountsQ.data ?? [];

  const balanceQs = useQueries({
    queries: accounts.map((a) => ({
      queryKey: ["ledger-balance", a.id],
      queryFn: () => api<BalanceView>(`/v1/ledger/accounts/${a.id}/balance`),
      staleTime: 15_000,
    })),
  });

  const rows: AccountRow[] = accounts.map((a, i) => ({
    ...a,
    balanceMinor: balanceQs[i]?.data?.balanceMinor ?? 0,
    balanceLoading: balanceQs[i]?.isLoading ?? false,
  }));

  const lv = useListView(rows, {
    searchFields: (a) => [a.code, a.type, a.currency],
    filterFields: { type: (a) => a.type },
    sortFields: {
      code: (a) => a.code,
      type: (a) => a.type,
      balance: (a) => a.balanceMinor,
    },
  });

  const csvColumns = [
    { header: "Account ID", value: (a: AccountRow) => a.id },
    { header: "Code", value: (a: AccountRow) => a.code },
    { header: "Type", value: (a: AccountRow) => a.type },
    { header: "Currency", value: (a: AccountRow) => a.currency },
    { header: "Balance (minor)", value: (a: AccountRow) => a.balanceMinor },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <PageHeader description="The merchant's chart of accounts and live balances from the append-only double-entry ledger. Read-only — corrections are made with offsetting journal entries." />

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardContent className="pt-6">
            <p className="text-2xl font-semibold tabular-nums">{accounts.length}</p>
            <p className="text-sm text-muted-foreground">Accounts</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-2xl font-semibold tabular-nums">
              {new Set(accounts.map((a) => a.type)).size}
            </p>
            <p className="text-sm text-muted-foreground">Account types</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-2xl font-semibold tabular-nums">
              {new Set(accounts.map((a) => a.currency)).size}
            </p>
            <p className="text-sm text-muted-foreground">Currencies</p>
          </CardContent>
        </Card>
      </div>

      <Card className="gap-0 overflow-hidden py-0">
        <ListToolbar
          search={lv.search}
          onSearchChange={lv.setSearch}
          searchPlaceholder="Search accounts…"
          filters={[
            {
              id: "type",
              label: "Type",
              value: lv.filters.type ?? "",
              options: ACCOUNT_TYPES.map((t) => ({ label: t, value: t })),
              onChange: (v) => lv.setFilter("type", v),
            },
          ]}
          density={lv.density}
          onDensityChange={lv.setDensity}
          onExport={(fmt) =>
            fmt === "csv"
              ? exportToCsv("ledger-accounts", lv.view, csvColumns)
              : exportToJson("ledger-accounts", lv.view)
          }
          exportDisabled={lv.view.length === 0}
          hasActiveFilters={lv.hasActiveFilters}
          onClear={lv.clear}
        />

        <DataState
          isLoading={accountsQ.isLoading}
          isError={accountsQ.isError}
          error={accountsQ.error}
          isEmpty={lv.view.length === 0}
          emptyIcon={ScaleIcon}
          emptyTitle="No accounts"
          emptyDescription="This merchant's chart of accounts is seeded during onboarding."
          className="p-6"
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <SortHeader columnKey="code" sort={lv.sort} onToggle={lv.toggleSort}>
                    Code
                  </SortHeader>
                  <SortHeader columnKey="type" sort={lv.sort} onToggle={lv.toggleSort}>
                    Type
                  </SortHeader>
                  <TableHead>Currency</TableHead>
                  <SortHeader columnKey="balance" sort={lv.sort} onToggle={lv.toggleSort} className="text-end">
                    Balance
                  </SortHeader>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lv.view.map((a) => (
                  <TableRow key={a.id} className={lv.density === "compact" ? "[&>td]:py-1.5" : ""}>
                    <TableCell className="font-medium">{a.code}</TableCell>
                    <TableCell>
                      <Badge variant="secondary">{a.type}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{a.currency}</TableCell>
                    <TableCell className="text-end font-medium tabular-nums">
                      {a.balanceLoading ? (
                        <span className="inline-block h-4 w-20 animate-pulse rounded bg-muted align-middle" />
                      ) : (
                        formatInr(a.balanceMinor, a.currency)
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </DataState>
      </Card>
    </div>
  );
}
