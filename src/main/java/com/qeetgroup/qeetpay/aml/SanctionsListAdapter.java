package com.qeetgroup.qeetpay.aml;

import java.util.List;

/**
 * Pluggable sanctions/PEP screening backend — sandbox or a live provider (e.g. Dow Jones / Refinitiv
 * World-Check / OFAC SDN feed). Returns every watchlist hit for the given party name + optional
 * identifier (PAN / GSTIN / account); an empty list means CLEAR.
 */
public interface SanctionsListAdapter {

    List<SanctionMatch> screen(String partyName, String identifier);
}
