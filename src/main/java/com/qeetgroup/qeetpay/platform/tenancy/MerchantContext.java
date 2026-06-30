package com.qeetgroup.qeetpay.platform.tenancy;

import java.util.UUID;

/**
 * Per-request holder of the active merchant (the unit of multi-tenancy, TAD §6.1). Populated by
 * {@link MerchantFilter} from the validated JWT / API key (or an {@code X-Merchant-Id} header in
 * dev/test) and threaded to the persistence layer, where {@link MerchantScope} pushes it into the
 * DB session GUC {@code app.current_merchant_id} for RLS.
 */
public final class MerchantContext {

    private static final ThreadLocal<UUID> HOLDER = new ThreadLocal<>();

    private MerchantContext() {}

    public static void set(UUID merchantId) {
        HOLDER.set(merchantId);
    }

    public static UUID current() {
        return HOLDER.get();
    }

    public static UUID require() {
        UUID id = HOLDER.get();
        if (id == null) {
            throw new IllegalStateException("No merchant in context for the current request");
        }
        return id;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
