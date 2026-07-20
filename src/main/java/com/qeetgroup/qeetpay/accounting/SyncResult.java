package com.qeetgroup.qeetpay.accounting;

/**
 * The outcome of a connector push: whether it succeeded, how many records it carried, the external
 * reference the target returned (provider id / filename), a detail message, and the generated
 * document (Tally XML / JSON body) retained for re-download.
 */
public record SyncResult(boolean success, int recordCount, String externalRef, String detail, String document) {

    public static SyncResult ok(int recordCount, String externalRef, String document) {
        return new SyncResult(true, recordCount, externalRef, null, document);
    }

    public static SyncResult failure(String detail) {
        return new SyncResult(false, 0, null, detail, null);
    }
}
