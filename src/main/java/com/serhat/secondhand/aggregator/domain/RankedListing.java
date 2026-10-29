package com.serhat.secondhand.aggregator.domain;

import java.util.List;

public record RankedListing(
        int rank,
        NormalizedListing listing,
        TrustScore trust,
        double relevance,
        double priceScore,
        double locationScore,
        double preferenceScore,
        double finalScore,
        List<String> alsoListedOn,
        List<String> reasons
) {}
