package com.serhat.secondhand.aggregator.domain;

import java.util.List;

/**
 * The agent's decision about WHICH sources are worth querying for this request, and why.
 * {@code plannedBy} is "gemini" when the LLM chose, "rules" for the deterministic fallback.
 */
public record SearchPlan(List<String> selectedSourceIds, List<String> skippedSourceIds, String rationale, String plannedBy) {}
