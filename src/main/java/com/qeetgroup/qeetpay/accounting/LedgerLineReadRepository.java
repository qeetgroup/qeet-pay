package com.qeetgroup.qeetpay.accounting;

import com.qeetgroup.qeetpay.ledger.JournalLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only view of a journal entry's lines for the export builder. Queries the exposed
 * {@link JournalLine} type of the (allowed-dependency) {@code ledger} module; never mutates it.
 */
public interface LedgerLineReadRepository extends JpaRepository<JournalLine, UUID> {

    List<JournalLine> findByEntryId(UUID entryId);
}
