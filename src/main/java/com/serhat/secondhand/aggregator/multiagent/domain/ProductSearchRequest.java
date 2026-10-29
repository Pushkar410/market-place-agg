package com.serhat.secondhand.aggregator.multiagent.domain;

import java.util.List;

/**
 * Structured request from user fed into the multi-agent system.
 * Supports mobile-specific fields (battery, budget), online/offline mode toggle.
 */
public record ProductSearchRequest(
        String rawQuery,
        String productName,
        Double maxBudget,
        Double minBudget,
        /** Minimum battery capacity in mAh - important for mobile devices */
        Integer minBatteryMah,
        /** Category hint: MOBILE, LAPTOP, TABLET, etc. */
        String category,
        String preferredBrand,
        String city,
        String condition,
        /** true = search internet live; false = use only offline/local dataset */
        boolean onlineSearch,
        /** Search community sources: WhatsApp groups, Facebook Marketplace */
        boolean includeSocialSources,
        /** Exclude OLX from results */
        boolean excludeOlx,
        int maxResults
) {
    public ProductSearchRequest withDefaults() {
        String derivedName = productName != null && !productName.isBlank()
                ? productName
                : (rawQuery != null ? com.serhat.secondhand.aggregator.multiagent.langchain.InternetProductSearchTool.extractSearchKeyword(rawQuery) : "product");

        return new ProductSearchRequest(
                rawQuery,
                derivedName,
                maxBudget,
                minBudget,
                minBatteryMah,
                category != null ? category.toUpperCase() : null,
                preferredBrand,
                city,
                condition,
                onlineSearch,
                includeSocialSources,
                true, // always exclude OLX by default
                maxResults > 0 ? maxResults : 10
        );
    }
}
