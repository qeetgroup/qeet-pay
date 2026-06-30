package com.qeetgroup.qeetpay.merchants;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.security.ApiKey;
import com.qeetgroup.qeetpay.platform.security.ApiKeyRepository;
import com.qeetgroup.qeetpay.platform.security.ApiKeys;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Onboards merchants: persists the tenant, mints its first (admin-scoped) API key, and seeds the
 * default chart of accounts in the ledger. The raw API key is returned exactly once.
 */
@Service
public class MerchantService {

    private static final String DEFAULT_SCOPES = "pay:admin pay:developer pay:finance pay:viewer";
    private static final String DEFAULT_CURRENCY = "INR";

    private final MerchantRepository merchants;
    private final ApiKeyRepository apiKeys;
    private final LedgerService ledger;

    public MerchantService(
            MerchantRepository merchants, ApiKeyRepository apiKeys, LedgerService ledger) {
        this.merchants = merchants;
        this.apiKeys = apiKeys;
        this.ledger = ledger;
    }

    @Transactional
    public Onboarded create(String slug, String name) {
        if (merchants.findBySlug(slug).isPresent()) {
            throw new IllegalArgumentException("merchant slug already exists: " + slug);
        }
        Merchant merchant = merchants.save(new Merchant(slug, name));

        String rawKey = ApiKeys.generate(false); // test key for a new merchant
        apiKeys.save(
                new ApiKey(
                        merchant.getId(),
                        ApiKeys.hash(rawKey),
                        ApiKeys.prefix(rawKey),
                        DEFAULT_SCOPES));

        ledger.openDefaultChart(merchant.getId(), DEFAULT_CURRENCY);
        return new Onboarded(merchant, rawKey);
    }

    /** A freshly onboarded merchant plus its raw API key (shown once). */
    public record Onboarded(Merchant merchant, String apiKey) {}
}
