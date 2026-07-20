package com.qeetgroup.qeetpay.accounting;

import com.qeetgroup.qeetpay.ledger.JournalEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only view of the {@code ledger} module's journal entries for the export builder. A separate
 * repository (not a modification of the ledger module) that queries the exposed {@link JournalEntry}
 * type — {@code ledger} is an allowed dependency. RLS still scopes rows; the explicit
 * {@code merchant_id} filter keeps it correct under the dev superuser too.
 */
public interface LedgerEntryReadRepository extends JpaRepository<JournalEntry, UUID> {

    /** Entries created in the half-open window {@code [from, to)}, oldest first. */
    List<JournalEntry> findByMerchantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAt(
            UUID merchantId, Instant from, Instant to);
}
