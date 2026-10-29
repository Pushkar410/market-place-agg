package com.serhat.secondhand.aggregator.multiagent.langchain;

import java.util.List;
import java.util.Map;

/**
 * Result produced by the LangChain ReAct Agent.
 */
public record LangChainAgentResult(
        String query,
        String productSearched,
        String verdict,               // BUY, CONSIDER, AVOID
        String finalAnswer,          // Full agent reasoning and narrative
        int confidenceScore,         // 0 to 100
        List<ReActStep> reActSteps,  // Thought -> Action -> Action Input -> Observation
        List<Map<String, Object>> topInternetProducts,
        List<Map<String, Object>> communityDeals,
        List<Map<String, Object>> detectedScams,
        boolean scamRiskDetected,
        String batteryAnalysis,
        String budgetAnalysis,
        long executionTimeMs
) {
    public record ReActStep(
            int stepNumber,
            String thought,
            String action,
            String actionInput,
            String observation
    ) {}
}
