package com.qeetgroup.qeetpay.aml;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An append-only record of one sanctions/PEP screen of a party. {@code matches} holds the raw
 * watchlist hits as a JSON string; {@code result} is HIT when at least one match was found.
 */
@Entity
@Table(name = "sanction_screenings", schema = "aml")
public class SanctionScreening {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false)
    private PartyType partyType;

    @Column(name = "party_name", nullable = false)
    private String partyName;

    @Column private String identifier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScreeningResult result;

    @Column(name = "match_count", nullable = false)
    private int matchCount;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    /** JSON array of {@link SanctionMatch}. */
    @Column(columnDefinition = "TEXT")
    private String matches;

    @Column(name = "screened_at", nullable = false)
    private Instant screenedAt = Instant.now();

    protected SanctionScreening() {}

    public SanctionScreening(
            UUID merchantId,
            PartyType partyType,
            String partyName,
            String identifier,
            ScreeningResult result,
            int matchCount,
            int riskScore,
            String matches) {
        this.merchantId = merchantId;
        this.partyType = partyType;
        this.partyName = partyName;
        this.identifier = identifier;
        this.result = result;
        this.matchCount = matchCount;
        this.riskScore = riskScore;
        this.matches = matches;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public PartyType getPartyType() { return partyType; }
    public String getPartyName() { return partyName; }
    public String getIdentifier() { return identifier; }
    public ScreeningResult getResult() { return result; }
    public int getMatchCount() { return matchCount; }
    public int getRiskScore() { return riskScore; }
    public String getMatches() { return matches; }
    public Instant getScreenedAt() { return screenedAt; }
}
