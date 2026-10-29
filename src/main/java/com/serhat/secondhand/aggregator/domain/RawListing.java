package com.serhat.secondhand.aggregator.domain;

import java.util.List;

/** A listing exactly as a source adapter returned it (loose, possibly incomplete). */
public record RawListing(
        String sourceId,
        String externalId,
        String title,
        String description,
        Double price,
        String currency,
        String city,
        String category,
        String condition,
        List<String> imageUrls,
        String url,
        Integer postedDaysAgo,
        Seller seller
) {
    public record Seller(
            String name,
            boolean verified,
            Double rating,
            Integer reviewCount,
            Integer accountAgeDays,
            boolean phoneProvided,
            String contactHash
    ) {}
}
