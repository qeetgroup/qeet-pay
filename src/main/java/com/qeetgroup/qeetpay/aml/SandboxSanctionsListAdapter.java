package com.qeetgroup.qeetpay.aml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Sandbox sanctions/PEP screening — an in-memory sample of OFAC / UN / PEP entries plus a few blocked
 * identifiers. A party <b>hits</b> when its (case-insensitive, trimmed) name equals or <i>starts
 * with</i> a seeded entry, or when its identifier (PAN / account) equals or starts with a seeded
 * value. Active whenever no {@code liveSanctionsListAdapter} bean is present (mirrors
 * {@code SandboxKybAdapter}).
 */
@Component
@ConditionalOnMissingBean(name = "liveSanctionsListAdapter")
public class SandboxSanctionsListAdapter implements SanctionsListAdapter {

    /** Seeded name watchlists, keyed by list name. Names are stored upper-cased for prefix matching. */
    private static final Map<String, List<String>> NAME_LISTS =
            Map.of(
                    "OFAC", List.of("OSAMA BIN", "VIKTOR BOUT", "DUBIOUS TRADING", "SHELL EXPORTS"),
                    "UN", List.of("KIM JONG", "AL QAEDA", "ISIL", "TALIBAN FINANCE"),
                    "PEP", List.of("RAJESH MINISTER", "NARENDRA POLITICIAN", "SENATOR SMITH"));

    /** Seeded blocked identifiers (PAN / account prefixes or exact values). */
    private static final List<String> BLOCKED_IDENTIFIERS =
            List.of("SANCTION", "BLOCKED", "AAASA9999S");

    @Override
    public List<SanctionMatch> screen(String partyName, String identifier) {
        List<SanctionMatch> matches = new ArrayList<>();

        if (partyName != null && !partyName.isBlank()) {
            String name = partyName.trim().toUpperCase();
            for (Map.Entry<String, List<String>> list : NAME_LISTS.entrySet()) {
                for (String entry : list.getValue()) {
                    if (name.equals(entry) || name.startsWith(entry)) {
                        matches.add(
                                new SanctionMatch(
                                        list.getKey(), "NAME", entry, name.equals(entry) ? 100 : 90));
                    }
                }
            }
        }

        if (identifier != null && !identifier.isBlank()) {
            String id = identifier.trim().toUpperCase();
            for (String blocked : BLOCKED_IDENTIFIERS) {
                if (id.equals(blocked) || id.startsWith(blocked)) {
                    matches.add(
                            new SanctionMatch(
                                    "OFAC", "IDENTIFIER", blocked, id.equals(blocked) ? 100 : 95));
                }
            }
        }

        return matches;
    }
}
