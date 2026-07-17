package com.qeetgroup.qeetpay.checkout;

import com.qeetgroup.qeetpay.merchants.Merchant;
import com.qeetgroup.qeetpay.merchants.MerchantRepository;
import com.qeetgroup.qeetpay.paymentlinks.LinkPublicLookup;
import com.qeetgroup.qeetpay.paymentlinks.LinkPublicLookupRepository;
import com.qeetgroup.qeetpay.paymentlinks.PaymentLink;
import com.qeetgroup.qeetpay.paymentlinks.PaymentLinkService;
import com.qeetgroup.qeetpay.paymentlinks.PaymentLinkStatus;
import com.qeetgroup.qeetpay.payments.PaymentMethod;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hosted-checkout orchestration for the PUBLIC (unauthenticated) surface. There is no merchant in
 * context — the link {@code code} carried in the shareable URL is the only capability the caller holds.
 *
 * <p>Both operations resolve the code through the non-RLS {@link LinkPublicLookupRepository} routing map
 * (the one read that works before a tenant is known), then {@link com.qeetgroup.qeetpay.platform.tenancy.MerchantScope#apply(UUID)}
 * the resolved merchant so Postgres RLS is satisfied for every subsequent read/write, and delegate to the
 * {@code paymentlinks} module. The read path returns a deliberately narrow, checkout-safe view — it never
 * exposes the payment id, merchant reference, internal ids or any ledger detail.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final LinkPublicLookupRepository lookups;
    private final PaymentLinkService paymentLinks;
    private final MerchantRepository merchants;
    private final MerchantScope merchantScope;

    public CheckoutService(
            LinkPublicLookupRepository lookups,
            PaymentLinkService paymentLinks,
            MerchantRepository merchants,
            MerchantScope merchantScope) {
        this.lookups = lookups;
        this.paymentLinks = paymentLinks;
        this.merchants = merchants;
        this.merchantScope = merchantScope;
    }

    /**
     * Resolves a public link code to a checkout-safe view for rendering the hosted page. Works with no
     * merchant context: the code is resolved via the non-RLS lookup, then the merchant scope is applied
     * so the RLS-protected link row can be read. Unknown code → {@link CheckoutNotFoundException} (404).
     */
    @Transactional(readOnly = true)
    public PublicView getPublic(String code) {
        UUID merchantId = resolveMerchant(code);
        merchantScope.apply(merchantId);
        PaymentLink link = paymentLinks.getByCode(merchantId, code);
        String merchantName = merchants.findById(merchantId).map(Merchant::getName).orElse(null);
        return new PublicView(
                link.getCode(),
                link.getTitle(),
                link.getAmountMinor(),
                link.getCurrency(),
                link.getStatus().name(),
                merchantName,
                link.getExpiresAt());
    }

    /**
     * Pays a public link by its code. Resolves the tenant via the non-RLS lookup, applies the merchant
     * scope, then delegates to {@link PaymentLinkService#pay} which drives a real captured payment and
     * ledger posting. Domain outcomes (expired / cancelled / already-paid / open-link-needs-amount) surface
     * as the same {@code IllegalState}/{@code IllegalArgument} exceptions the module already throws — the
     * global handler maps them to RFC-7807 409/400.
     *
     * <p>{@code customerName}/{@code customerEmail} are accepted best-effort: the {@code payment_links}
     * schema has no field for them, so they are logged for support/audit and otherwise ignored.
     */
    @Transactional
    public PayResult pay(
            String code,
            PaymentMethod method,
            Long amountMinor,
            String customerName,
            String customerEmail) {
        UUID merchantId = resolveMerchant(code);
        merchantScope.apply(merchantId);
        if (customerName != null || customerEmail != null) {
            log.info(
                    "hosted checkout pay: code={} method={} customerName={} customerEmail={}",
                    code, method, customerName, customerEmail);
        }
        PaymentLink link = paymentLinks.pay(merchantId, code, method, amountMinor, false);
        return new PayResult(link.getCode(), link.getStatus().name(), link.getStatus() == PaymentLinkStatus.PAID);
    }

    /** The one lookup that must work with no merchant context (the routing map has no RLS). */
    private UUID resolveMerchant(String code) {
        return lookups
                .findByCode(code)
                .map(LinkPublicLookup::getMerchantId)
                .orElseThrow(() -> new CheckoutNotFoundException("no payment link '" + code + "'"));
    }

    // ── Public (checkout-safe) views ─────────────────────────────────────────────
    // Only fields safe to expose on an unauthenticated hosted page: NO payment id, reference, internal
    // ids or ledger info. {@code amountMinor} is null for an open (payer-entered) amount.

    public record PublicView(
            String code,
            String title,
            Long amountMinor,
            String currency,
            String status,
            String merchantName,
            Instant expiresAt) {}

    public record PayResult(String code, String status, boolean paid) {}
}
