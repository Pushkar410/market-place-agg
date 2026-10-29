package com.serhat.secondhand.aggregator.domain;

import java.util.List;
import java.util.Set;

/** Listing after the "Aggregate & Normalize" step: one consistent schema for all sources. */
public record NormalizedListing(
        String id,
        String sourceId,
        String sourceName,
        String title,
        String description,
        Double price,
        String currency,
        String originalPrice,
        String city,
        Category category,
        String condition,
        List<String> imageUrls,
        String url,
        int postedDaysAgo,
        String postedAt,
        RawListing.Seller seller,
        Set<String> titleTokens
) {}
