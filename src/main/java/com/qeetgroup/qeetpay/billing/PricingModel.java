package com.qeetgroup.qeetpay.billing;

/** Determines how an invoice amount is calculated from plan configuration + optional usage. */
public enum PricingModel {
    /** Fixed recurring amount regardless of usage. */
    FLAT,
    /** Per-unit pricing: unit_price × quantity metered in the period. */
    PER_UNIT,
    /** Tiered: total = Σ (units in tier × tier_price), applied in sequence. */
    TIERED,
    /** Volume: all units priced at the single tier that contains total volume. */
    VOLUME,
    /** Flat base fee + per-unit overage above an included quantity. */
    HYBRID
}
