package com.qeetgroup.qeetpay.gst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The deterministic HSN/SAC classification fallback (PRD Module 05, §6.4 "fail-closed to a
 * deterministic path"). Pure + stateless — no Spring/DB — so it is unit-testable in isolation and safe
 * to run offline. A curated keyword → HSN/SAC map ranks candidates by how many of an entry's keywords
 * the description hits; confidence grows with match count. No keyword match yields a low-confidence
 * residual-services code (SAC 9997) that the classifier flags for human review.
 *
 * <p>This is intentionally a small, honest stand-in — the shipped substrate. When a real model client
 * ({@code liveAiModelClient}) is registered, {@code HsnClassifier} prefers the model's structured
 * output and uses this map only as the fail-closed fallback.
 */
public final class HsnCatalog {

    private HsnCatalog() {}

    static final String KIND_HSN = "HSN";
    static final String KIND_SAC = "SAC";

    /** Residual services code used when nothing matches; deliberately low-confidence → human review. */
    static final HsnSuggestion RESIDUAL =
            new HsnSuggestion("9997", KIND_SAC, 18, 0.20, "Other services n.e.c. (residual)");

    private record Entry(String code, String kind, int rate, String label, List<String> keywords) {}

    // Curated best-effort map. Codes/rates are representative for a stand-in classifier, not tax advice.
    private static final List<Entry> ENTRIES =
            List.of(
                    new Entry("998314", KIND_SAC, 18, "IT software & development services",
                            List.of("software", "saas", "subscription", "license", "licence", "cloud",
                                    "hosting", "api", "developer", "it service", "web development")),
                    new Entry("998311", KIND_SAC, 18, "Management & professional consulting",
                            List.of("consulting", "consultancy", "advisory", "professional service",
                                    "audit", "accounting", "bookkeeping", "legal")),
                    new Entry("998361", KIND_SAC, 18, "Advertising & marketing services",
                            List.of("advertising", "marketing", "promotion", "ad campaign", "branding")),
                    new Entry("999293", KIND_SAC, 18, "Education, training & coaching services",
                            List.of("education", "training", "course", "tuition", "coaching", "workshop")),
                    new Entry("996311", KIND_SAC, 12, "Hotel & accommodation services",
                            List.of("hotel", "accommodation", "lodging", "guesthouse", "room booking")),
                    new Entry("996331", KIND_SAC, 5, "Restaurant & catering services",
                            List.of("restaurant", "dining", "meal", "catering", "food service", "cafe")),
                    new Entry("996511", KIND_SAC, 18, "Road transport, freight & logistics",
                            List.of("transport", "freight", "logistics", "courier", "shipping", "delivery")),
                    new Entry("997133", KIND_SAC, 18, "Insurance services",
                            List.of("insurance", "policy cover", "premium cover")),
                    new Entry("995411", KIND_SAC, 18, "Construction & works-contract services",
                            List.of("construction", "works contract", "civil work", "building work")),
                    new Entry("8517", KIND_HSN, 18, "Telephones & mobile phones",
                            List.of("mobile", "smartphone", "cellphone", "handset")),
                    new Entry("8471", KIND_HSN, 18, "Computers, laptops & data-processing machines",
                            List.of("laptop", "computer", "notebook", "desktop", "server hardware")),
                    new Entry("6109", KIND_HSN, 12, "Apparel & textiles (t-shirts, garments)",
                            List.of("apparel", "garment", "clothing", "shirt", "tshirt", "textile", "fabric")),
                    new Entry("6403", KIND_HSN, 18, "Footwear",
                            List.of("footwear", "shoe", "sandal", "sneaker")),
                    new Entry("4901", KIND_HSN, 0, "Printed books (exempt)",
                            List.of("book", "textbook", "printed book")),
                    new Entry("3004", KIND_HSN, 12, "Medicaments / pharmaceuticals",
                            List.of("medicine", "pharma", "drug", "tablet", "capsule", "medicament")),
                    new Entry("9403", KIND_HSN, 18, "Furniture",
                            List.of("furniture", "sofa", "wardrobe", "cupboard", "office chair")),
                    new Entry("2523", KIND_HSN, 28, "Cement",
                            List.of("cement", "clinker")),
                    new Entry("8703", KIND_HSN, 28, "Motor cars & vehicles",
                            List.of("car", "automobile", "motorcycle", "scooter", "sedan")),
                    new Entry("7113", KIND_HSN, 3, "Gold & jewellery",
                            List.of("gold", "jewellery", "jewelry", "ornament")),
                    new Entry("1701", KIND_HSN, 5, "Sugar",
                            List.of("sugar")),
                    new Entry("0401", KIND_HSN, 0, "Milk & fresh dairy (exempt)",
                            List.of("milk", "curd", "fresh dairy")));

    /** A ranked classification: the (non-empty) candidate list, plus an explanation for the primary. */
    public record Ranked(List<HsnSuggestion> suggestions, String explanation, boolean matched) {}

    /**
     * Classifies a product/service description into ranked HSN/SAC candidates (up to three). Falls back
     * to the residual code with a review-triggering low confidence when nothing matches.
     */
    public static Ranked classify(String description) {
        String norm =
                (description == null ? "" : description)
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9 ]", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
        Set<String> tokens =
                norm.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(norm.split(" ")));
        String haystack = " " + norm + " ";

        record Scored(Entry entry, int matches, List<String> hits) {}
        List<Scored> scored = new ArrayList<>();
        for (Entry e : ENTRIES) {
            List<String> hits = new ArrayList<>();
            for (String kw : e.keywords()) {
                boolean hit =
                        kw.contains(" ") ? haystack.contains(" " + kw + " ") || norm.contains(kw)
                                : tokens.contains(kw);
                if (hit) {
                    hits.add(kw);
                }
            }
            if (!hits.isEmpty()) {
                scored.add(new Scored(e, hits.size(), hits));
            }
        }

        if (scored.isEmpty()) {
            return new Ranked(
                    List.of(HsnCatalog.RESIDUAL),
                    "No curated keyword matched \"" + safe(description)
                            + "\"; defaulting to residual services (SAC 9997). Human review recommended.",
                    false);
        }

        scored.sort((a, b) -> Integer.compare(b.matches(), a.matches()));
        List<HsnSuggestion> suggestions = new ArrayList<>();
        for (Scored s : scored.subList(0, Math.min(3, scored.size()))) {
            suggestions.add(
                    new HsnSuggestion(
                            s.entry().code(), s.entry().kind(), s.entry().rate(),
                            confidence(s.matches()), s.entry().label()));
        }
        Scored top = scored.get(0);
        String explanation =
                "Matched keyword(s) " + top.hits() + " → " + top.entry().label() + " ("
                        + top.entry().kind() + " " + top.entry().code() + ", " + top.entry().rate() + "% GST).";
        return new Ranked(suggestions, explanation, true);
    }

    /** Confidence grows with the number of distinct keywords hit, capped at 0.95. */
    private static double confidence(int matches) {
        return Math.min(0.95, 0.62 + 0.11 * matches);
    }

    private static String safe(String s) {
        String v = s == null ? "" : s.trim();
        return v.length() > 60 ? v.substring(0, 60) + "…" : v;
    }
}
