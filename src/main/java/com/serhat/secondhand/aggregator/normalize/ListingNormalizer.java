package com.serhat.secondhand.aggregator.normalize;

import com.serhat.secondhand.aggregator.domain.Category;
import com.serhat.secondhand.aggregator.domain.NormalizedListing;
import com.serhat.secondhand.aggregator.domain.RawListing;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/** Step 3a: map every source's loose schema onto one clean {@link NormalizedListing}. */
@Component
public class ListingNormalizer {

    /** Approximate fixed demo rates to INR (a real deployment would use the platform's exchange-rate service). */
    static final Map<String, Double> RATES_TO_INR = Map.of("INR", 1.0, "USD", 83.0, "EUR", 90.0, "TRY", 2.4);

    /** Returns empty when the listing is unusable (no title). */
    public Optional<NormalizedListing> normalize(RawListing raw, String sourceName) {
        if (raw == null || raw.title() == null || raw.title().isBlank()) return Optional.empty();

        String title = cleanTitle(raw.title());
        String description = raw.description() == null ? "" : raw.description().replaceAll("\\s+", " ").trim();

        Set<String> tokens = tokens(title);
        Category category = resolveCategory(raw.category(), tokens, description);

        int days = raw.postedDaysAgo() == null ? 30 : Math.max(0, raw.postedDaysAgo());
        String postedAt = LocalDate.now().minusDays(days).toString();

        Double rawPrice = raw.price() != null && raw.price() > 0 ? raw.price() : null;
        String rawCurrency = raw.currency() == null || raw.currency().isBlank() ? "INR" : raw.currency().toUpperCase(Locale.ROOT);
        // Everything is normalised to INR so prices from different sources are comparable
        Double price = rawPrice == null ? null : Math.round(rawPrice * RATES_TO_INR.getOrDefault(rawCurrency, 1.0) * 100.0) / 100.0;
        String currency = "INR";
        String originalPrice = rawPrice == null || rawCurrency.equals("INR") ? null : rawCurrency + " " + Math.round(rawPrice);

        List<String> images = raw.imageUrls() == null ? List.of()
                : raw.imageUrls().stream().filter(s -> s != null && !s.isBlank()).distinct().toList();

        return Optional.of(new NormalizedListing(
                raw.sourceId() + ":" + raw.externalId(),
                raw.sourceId(),
                sourceName,
                title,
                description,
                price,
                currency,
                originalPrice,
                Vocabulary.canonicalCity(raw.city()),
                category,
                canonicalCondition(raw.condition(), title + " " + description),
                images,
                raw.url(),
                days,
                postedAt,
                raw.seller(),
                tokens));
    }

    static String cleanTitle(String t) {
        String s = t.replaceAll("[\\p{So}\\p{Cn}!]{1,}", " ").replaceAll("\\s+", " ").trim();
        // Shouting titles ("URGENT SALE LAPTOP") -> sentence-ish case
        long letters = s.chars().filter(Character::isLetter).count();
        long upper = s.chars().filter(Character::isUpperCase).count();
        if (letters > 8 && upper > letters * 0.7) {
            StringBuilder sb = new StringBuilder();
            for (String w : s.toLowerCase(Locale.ROOT).split(" ")) {
                if (w.isEmpty()) continue;
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
            s = sb.toString().trim();
        }
        return s;
    }

    /** Lower-cased alphanumeric tokens used for duplicate detection and matching. */
    public static Set<String> tokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() < 2) continue;
            if (Vocabulary.STOP_WORDS.contains(raw)) continue;
            out.add(raw);
        }
        return out;
    }

    private static Category resolveCategory(String declared, Set<String> titleTokens, String description) {
        String d = declared == null ? "" : declared.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        // Map the internal marketplace's ListingType names and common source labels
        switch (d) {
            case "ELECTRONICS", "ELECTRONIC" -> { return Category.ELECTRONICS; }
            case "BOOKS", "BOOK" -> { return Category.BOOKS; }
            case "FURNITURE", "HOME" -> { return Category.FURNITURE; }
            case "VEHICLE", "VEHICLES" -> { return Category.VEHICLES; }
            case "REAL_ESTATE", "HOUSING", "RENTAL" -> { return Category.HOUSING; }
            case "SPORTS" -> { return Category.SPORTS; }
            case "CLOTHING", "FASHION" -> { return Category.CLOTHING; }
            case "SERVICES", "SERVICE" -> { return Category.SERVICES; }
            default -> { }
        }
        Category inferred = Vocabulary.inferCategory(titleTokens);
        if (inferred == null) inferred = Vocabulary.inferCategory(tokens(description));
        return inferred == null ? Category.OTHER : inferred;
    }

    private static String canonicalCondition(String declared, String text) {
        if (declared != null && !declared.isBlank()) {
            String d = declared.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            return switch (d) {
                case "NEW", "BRAND_NEW" -> "NEW";
                case "LIKE_NEW", "EXCELLENT" -> "LIKE_NEW";
                case "GOOD", "USED", "VERY_GOOD" -> "GOOD";
                case "FAIR", "ACCEPTABLE", "WORN" -> "FAIR";
                default -> d;
            };
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("like new") || lower.contains("barely used")) return "LIKE_NEW";
        if (lower.contains("brand new") || lower.contains("sealed")) return "NEW";
        return null;
    }
}
