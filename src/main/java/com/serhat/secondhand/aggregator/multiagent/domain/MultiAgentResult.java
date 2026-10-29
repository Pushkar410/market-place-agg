package com.serhat.secondhand.aggregator.multiagent.domain;

import java.util.List;
import java.util.Map;

/**
 * Full multi-agent analysis result: aggregated from all specialist agents.
 */
public record MultiAgentResult(

        // ---- Summary ----
        String query,
        String productName,
        String overallVerdict,      // "BUY", "CONSIDER", "AVOID"
        String verdictReason,       // plain-language explanation
        int confidenceScore,        // 0-100

        // ---- Best Deals ----
        List<DealResult> topDeals,

        // ---- Popular Picks (from PopularProductsAgent) ----
        List<ProductRanking> popularPicks,

        // ---- Community Listings (WhatsApp/Facebook) ----
        List<DealResult> socialListings,

        // ---- Scam Flags ----
        List<ScamFlag> scamFlags,
        boolean hasHighRiskListings,

        // ---- Battery Analysis ----
        BatteryAnalysis batteryAnalysis,

        // ---- Budget Analysis ----
        BudgetAnalysis budgetAnalysis,

        // ---- Agent Trace ----
        List<String> agentTrace,
        Map<String, String> agentStatuses,
        long tookMs
) {

    public record DealResult(
            int rank,
            String title,
            String source,          // "WhatsApp", "Facebook Marketplace", etc.
            Double price,
            String currency,
            String city,
            String condition,
            String url,
            List<String> imageUrls,
            int trustScore,
            String trustLevel,
            List<String> trustReasons,
            List<String> scamFlags,
            boolean isRecommended,
            String dealBadge        // "BEST DEAL", "SCAM RISK", "GREAT VALUE", etc.
    ) {}

    public record ProductRanking(
            int rank,
            String productName,
            String brand,
            Double price,
            String currency,
            Double rating,
            int reviewCount,
            String source,
            String url,
            List<String> imageUrls,
            String batteryInfo,
            String highlights,
            boolean isPopularChoice
    ) {}

    public record ScamFlag(
            String listingTitle,
            String source,
            String flagType,        // "ADVANCE_PAYMENT", "TOO_CHEAP", "FAKE_SPECS", etc.
            String description,
            int riskLevel           // 1-10
    ) {}

    public record BatteryAnalysis(
            boolean batteryImportant,
            Integer requestedMinMah,
            List<String> productsWithGoodBattery,
            String batteryAdvice
    ) {}

    public record BudgetAnalysis(
            Double minBudget,
            Double maxBudget,
            Double averageMarketPrice,
            Double bestDealPrice,
            String budgetAdvice,
            boolean budgetSufficient
    ) {}
}
