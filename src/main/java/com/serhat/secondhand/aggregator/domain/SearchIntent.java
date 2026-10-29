package com.serhat.secondhand.aggregator.domain;

import java.util.List;

/**
 * Structured representation of what the user wants, produced by the
 * "Understand Query" step (LLM when configured, rule-based otherwise).
 */
public record SearchIntent(
        String rawQuery,
        List<String> keywords,
        Category category,
        Double minPrice,
        Double maxPrice,
        String city,
        String condition,
        Preference preference,
        String interpretedBy
) {
    public enum Preference { BALANCED, TRUSTED, CHEAPEST, NEARBY }

    public SearchIntent withoutCity() {
        return new SearchIntent(rawQuery, keywords, category, minPrice, maxPrice, null, condition, preference, interpretedBy);
    }

    public SearchIntent withoutCondition() {
        return new SearchIntent(rawQuery, keywords, category, minPrice, maxPrice, city, null, preference, interpretedBy);
    }

    public SearchIntent withoutCategory() {
        return new SearchIntent(rawQuery, keywords, null, minPrice, maxPrice, city, condition, preference, interpretedBy);
    }

    public SearchIntent withMaxPrice(Double newMax) {
        return new SearchIntent(rawQuery, keywords, category, minPrice, newMax, city, condition, preference, interpretedBy);
    }
}
