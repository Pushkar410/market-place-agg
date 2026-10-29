package com.serhat.secondhand.aggregator.multiagent.agent;

import com.serhat.secondhand.aggregator.multiagent.domain.MultiAgentResult;
import com.serhat.secondhand.aggregator.multiagent.domain.ProductSearchRequest;

import java.util.List;

/**
 * Base contract for all specialist agents in the multi-agent system.
 * Each agent is independently runnable and reports its own results.
 */
public interface SpecialistAgent {

    /** Short name shown in the agent trace (e.g. "PopularProductsAgent") */
    String agentName();

    /**
     * Run the agent. Returns a partial result; the orchestrator merges all partials.
     * Implementations MUST never throw - catch internally and return a fallback.
     */
    AgentOutput run(ProductSearchRequest request);

    /** Partial output returned by a single agent; the orchestrator merges these. */
    record AgentOutput(
            String agentName,
            String status,   // "OK", "FALLBACK", "ERROR"
            List<MultiAgentResult.ProductRanking> popularPicks,
            List<MultiAgentResult.DealResult> deals,
            List<MultiAgentResult.ScamFlag> scamFlags,
            String agentInsight,    // narrative from this agent
            List<String> trace
    ) {
        /** Convenience empty-OK output */
        public static AgentOutput ok(String name, String insight, List<String> trace) {
            return new AgentOutput(name, "OK", List.of(), List.of(), List.of(), insight, trace);
        }
        public static AgentOutput error(String name, String message) {
            return new AgentOutput(name, "ERROR", List.of(), List.of(), List.of(), message, List.of("[" + name + "] " + message));
        }
    }
}
