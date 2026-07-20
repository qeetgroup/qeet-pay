package com.qeetgroup.qeetpay.ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyRecord;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ledger API (the walking-skeleton vertical): list accounts, post a balanced journal entry
 * (idempotent), and read an account balance. The active merchant comes from {@link MerchantContext}
 * (set by the API-key or JWT auth, or the dev {@code X-Merchant-Id} header).
 */
@Tag(
        name = "Ledger",
        description = "Double-entry ledger — list accounts, post a balanced journal entry (idempotent), and read account balances.")
@RestController
@RequestMapping("/v1/ledger")
public class LedgerController {

    private final LedgerService ledger;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public LedgerController(
            LedgerService ledger, IdempotencyService idempotency, ObjectMapper objectMapper) {
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/accounts")
    public List<AccountView> accounts() {
        return ledger.accountsOf(MerchantContext.require()).stream()
                .map(a -> new AccountView(a.getId().toString(), a.getCode(), a.getType().name(), a.getCurrency()))
                .toList();
    }

    @GetMapping("/accounts/{id}/balance")
    public BalanceView balance(@PathVariable UUID id) {
        UUID merchantId = MerchantContext.require();
        return new BalanceView(id.toString(), ledger.balanceMinor(merchantId, id));
    }

    @PostMapping("/entries")
    public ResponseEntity<?> postEntry(
            @Valid @RequestBody PostEntryRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey)
            throws JsonProcessingException {
        UUID merchantId = MerchantContext.require();

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> prior = idempotency.lookup(merchantId, idempotencyKey);
            if (prior.isPresent()) {
                return ResponseEntity.status(prior.get().getResponseStatus())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(prior.get().getResponseBody());
            }
        }

        List<LedgerLineInput> lines =
                request.lines().stream()
                        .map(l -> new LedgerLineInput(l.accountId(), l.direction(), l.amountMinor()))
                        .toList();
        UUID entryId = ledger.postEntry(merchantId, request.description(), request.currency(), lines);
        PostEntryResponse response = new PostEntryResponse(entryId.toString());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(
                    merchantId,
                    idempotencyKey,
                    HttpStatus.CREATED.value(),
                    objectMapper.writeValueAsString(response));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public record PostEntryRequest(
            @NotEmpty String description,
            @NotEmpty String currency,
            @NotEmpty List<LineDto> lines) {}

    @Schema(name = "LedgerLineDto")
    public record LineDto(
            @NotNull UUID accountId, @NotNull Direction direction, @Positive long amountMinor) {}

    public record PostEntryResponse(String entryId) {}

    @Schema(name = "LedgerAccountView")
    public record AccountView(String id, String code, String type, String currency) {}

    public record BalanceView(String accountId, long balanceMinor) {}
}
