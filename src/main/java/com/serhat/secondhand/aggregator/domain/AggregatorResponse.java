package com.serhat.secondhand.aggregator.domain;

import java.util.List;

public record AggregatorResponse(
        String query,
        SearchIntent intent,
        List<SourceReport> sources,
        int totalFetched,
        int duplicatesRemoved,
        int totalRanked,
        List<RankedListing> results,
        String summary,
        List<String> agentTrace,
        long tookMs,
        /** Which sources the agent chose to query (and skipped) for this request; null if it asked a question instead. */
        SearchPlan plan,
        /** Non-null when the request was too vague: the agent asks this question instead of guessing. */
        String clarification,
        /** Short LLM-written comparison of the top results, grounded only in computed data; null without an LLM. */
        String aiExplanation,
        /** "gemini" when aiExplanation was produced, otherwise null. */
        String explainedBy
) {}
