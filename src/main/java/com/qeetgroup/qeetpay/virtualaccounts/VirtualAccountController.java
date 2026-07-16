package com.qeetgroup.qeetpay.virtualaccounts;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Virtual-accounts API (PRD Module 01): mint a per-customer virtual account, read accounts + their
 * credits, ingest an inbound credit (auto-reconciled), and close an account.
 */
@Tag(
        name = "Virtual Accounts",
        description = "Mint per-customer virtual accounts, ingest auto-reconciled inbound credits, and read accounts with their credits.")
@RestController
@RequestMapping("/v1/virtual-accounts")
public class VirtualAccountController {

    private final VirtualAccountService vaService;

    public VirtualAccountController(VirtualAccountService vaService) {
        this.vaService = vaService;
    }

    @PostMapping
    public ResponseEntity<AccountView> mint(@Valid @RequestBody MintRequest req) {
        VirtualAccount va = vaService.mintAccount(MerchantContext.require(), req.customerRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountView.of(va));
    }

    @GetMapping
    public List<AccountView> list() {
        return vaService.listAccounts(MerchantContext.require()).stream().map(AccountView::of).toList();
    }

    @GetMapping("/{vaId}")
    public AccountWithCreditsView get(@PathVariable UUID vaId) {
        return AccountWithCreditsView.of(vaService.getAccount(MerchantContext.require(), vaId));
    }

    @PostMapping("/{vaId}/close")
    public AccountView close(@PathVariable UUID vaId) {
        return AccountView.of(vaService.closeAccount(MerchantContext.require(), vaId));
    }

    /** Ingest an inbound credit (typically driven by a bank webhook). Auto-reconciles to the ledger. */
    @PostMapping("/{vaId}/credits")
    public ResponseEntity<CreditView> credit(@PathVariable UUID vaId, @Valid @RequestBody CreditRequest req) {
        VirtualAccountCredit credit =
                vaService.ingestCredit(
                        MerchantContext.require(), vaId, req.amountMinor(), req.currency(), req.utr(),
                        req.payerName(), req.payerRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(CreditView.of(credit));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record MintRequest(@NotBlank String customerRef) {}

    public record CreditRequest(
            @NotNull @Positive Long amountMinor,
            @NotBlank String currency,
            @NotBlank String utr,
            String payerName,
            String payerRef) {}

    public record AccountView(
            String id, String customerRef, String vaNumber, String ifsc, String status,
            Instant createdAt, Instant closedAt) {
        static AccountView of(VirtualAccount v) {
            return new AccountView(
                    v.getId().toString(), v.getCustomerRef(), v.getVaNumber(), v.getIfsc(),
                    v.getStatus().name(), v.getCreatedAt(), v.getClosedAt());
        }
    }

    public record CreditView(
            String id, String vaId, long amountMinor, String currency, String utr,
            String payerName, String payerRef, String ledgerEntryId, Instant creditedAt) {
        static CreditView of(VirtualAccountCredit c) {
            return new CreditView(
                    c.getId().toString(), c.getVaId().toString(), c.getAmountMinor(), c.getCurrency(),
                    c.getUtr(), c.getPayerName(), c.getPayerRef(), c.getLedgerEntryId().toString(),
                    c.getCreditedAt());
        }
    }

    public record AccountWithCreditsView(AccountView account, List<CreditView> credits) {
        static AccountWithCreditsView of(VirtualAccountService.AccountWithCredits a) {
            return new AccountWithCreditsView(
                    AccountView.of(a.account()),
                    a.credits().stream().map(CreditView::of).toList());
        }
    }
}
