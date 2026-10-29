package com.serhat.secondhand.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serhat.secondhand.aggregator.domain.*;
import com.serhat.secondhand.aggregator.explain.ResultExplainer;
import com.serhat.secondhand.aggregator.intent.GeminiIntentParser;
import com.serhat.secondhand.aggregator.intent.IntentService;
import com.serhat.secondhand.aggregator.intent.RuleBasedIntentParser;
import com.serhat.secondhand.aggregator.llm.LlmTextGenerator;
import com.serhat.secondhand.aggregator.normalize.DuplicateDetector;
import com.serhat.secondhand.aggregator.normalize.ListingNormalizer;
import com.serhat.secondhand.aggregator.plan.SearchPlanner;
import com.serhat.secondhand.aggregator.rank.RankingService;
import com.serhat.secondhand.aggregator.service.AggregatorService;
import com.serhat.secondhand.aggregator.source.SimulatedSourceAdapter;
import com.serhat.secondhand.aggregator.source.SourceAdapter;
import com.serhat.secondhand.aggregator.trust.TrustScoringService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The planning / clarification / explanation steps that make the pipeline an agent. No network, no database. */
class AgenticBehaviourTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A scripted "LLM" so the LLM code paths can be tested deterministically. */
    private static final class FakeLlm implements LlmTextGenerator {
        private final boolean available;
        private final String reply;
        private final boolean fail;
        int calls = 0;
        String lastPrompt;

        FakeLlm(boolean available, String reply, boolean fail) { this.available = available; this.reply = reply; this.fail = fail; }

        public boolean isAvailable() { return available; }

        public String generate(String prompt) {
            calls++;
            lastPrompt = prompt;
            if (fail) throw new IllegalStateException("quota exceeded");
            return reply;
        }
    }

    private static List<SourceAdapter> sources() {
        List<SourceAdapter> adapters = new ArrayList<>();
        for (String f : List.of("olx", "facebook-marketplace", "whatsapp-groups", "local-niche")) {
            adapters.add(new SimulatedSourceAdapter("/aggregator/" + f + ".json", MAPPER));
        }
        return adapters;
    }

    private static AggregatorService service(SearchPlanner planner, ResultExplainer explainer) {
        IntentService intent = new IntentService(new GeminiIntentParser(null, MAPPER), new RuleBasedIntentParser());
        return new AggregatorService(intent, sources(), new ListingNormalizer(), new DuplicateDetector(),
                new TrustScoringService(), new RankingService(), planner, explainer);
    }

    // ------------------------------------------------------------------ planning

    @Test
    void housingSearchSkipsSourcesThatHaveNoHousingListings() {
        AggregatorResponse r = service(SearchPlanner.rulesOnly(), ResultExplainer.disabled()).search("PG room near SIT Tumkur under 8000", 10);

        assertNotNull(r.plan());
        assertEquals("rules", r.plan().plannedBy());
        assertTrue(r.plan().skippedSourceIds().contains("facebook-marketplace"), "Facebook dataset has no housing");
        assertTrue(r.plan().selectedSourceIds().contains("whatsapp-groups"));
        assertTrue(r.sources().stream().anyMatch(s -> s.id().equals("facebook-marketplace") && s.status().equals("SKIPPED")));
        assertTrue(r.agentTrace().stream().anyMatch(t -> t.startsWith("Plan:")));
        assertFalse(r.results().isEmpty());
    }

    @Test
    void searchWithoutCategoryQueriesEverySource() {
        SearchIntent any = new RuleBasedIntentParser().parse("hero sprint cycle");
        SearchPlan p = SearchPlanner.rulesOnly().plan(new SearchIntent(any.rawQuery(), any.keywords(), null, null, null, null, null,
                SearchIntent.Preference.BALANCED, "test"), sources());
        assertTrue(p.skippedSourceIds().isEmpty());
        assertEquals(4, p.selectedSourceIds().size());
    }

    @Test
    void llmPlannerChoiceIsUsedButNeverDropsSourcesWithUnknownCoverage() {
        SourceAdapter unknownCoverage = new SourceAdapter() {
            public SourceInfo info() { return new SourceInfo("live-db", "Live DB", "INTERNAL_DATABASE", false, 0.9); }
            public List<RawListing> search(SearchIntent i) { return List.of(); }
        };
        List<SourceAdapter> all = new ArrayList<>(sources());
        all.add(unknownCoverage);
        FakeLlm llm = new FakeLlm(true, "```json\n{\"sources\":[\"olx\",\"does-not-exist\"],\"reason\":\"OLX is best for electronics\"}\n```", false);
        SearchIntent intent = new RuleBasedIntentParser().parse("laptop under 30k");

        SearchPlan p = new SearchPlanner(llm, MAPPER).plan(intent, all);

        assertEquals("gemini", p.plannedBy());
        assertTrue(p.selectedSourceIds().contains("olx"));
        assertTrue(p.selectedSourceIds().contains("live-db"), "live DB is never skipped");
        assertFalse(p.selectedSourceIds().contains("does-not-exist"));
        assertTrue(p.skippedSourceIds().contains("whatsapp-groups"));
        assertEquals("OLX is best for electronics", p.rationale());
    }

    @Test
    void llmPlannerGarbageOrFailureFallsBackToRules() {
        SearchIntent intent = new RuleBasedIntentParser().parse("laptop under 30k");
        for (LlmTextGenerator bad : List.of(new FakeLlm(true, "sorry, I cannot help", false), new FakeLlm(true, null, false),
                new FakeLlm(true, "{\"sources\":[]}", false), new FakeLlm(true, "x", true))) {
            SearchPlan p = new SearchPlanner(bad, MAPPER).plan(intent, sources());
            assertEquals("rules", p.plannedBy());
            assertFalse(p.selectedSourceIds().isEmpty());
        }
    }

    @Test
    void unavailableLlmIsNeverCalled() {
        FakeLlm llm = new FakeLlm(false, "{}", false);
        new SearchPlanner(llm, MAPPER).plan(new RuleBasedIntentParser().parse("laptop"), sources());
        assertEquals(0, llm.calls);
    }

    // ------------------------------------------------------------------ clarification

    @Test
    void vagueRequestMakesTheAgentAskInsteadOfGuessing() {
        AggregatorResponse r = service(SearchPlanner.rulesOnly(), ResultExplainer.disabled()).search("under 5000 in tumkur", 10);

        assertNotNull(r.clarification());
        assertTrue(r.results().isEmpty());
        assertTrue(r.sources().isEmpty());
        assertTrue(r.agentTrace().stream().anyMatch(t -> t.startsWith("Clarify:")));
    }

    @Test
    void categoryOnlyRequestIsSearchableAndNotClarified() {
        AggregatorResponse r = service(SearchPlanner.rulesOnly(), ResultExplainer.disabled()).search("electronics under 20k", 10);
        assertEquals(null, r.clarification());
        assertFalse(r.results().isEmpty());
    }

    // ------------------------------------------------------------------ explanation

    @Test
    void explanationIsGroundedInComputedFactsAndDoesNotChangeTheRanking() {
        FakeLlm llm = new FakeLlm(true, "**Best pick** is the first one: it is trusted.", false);
        AggregatorService withLlm = service(SearchPlanner.rulesOnly(), new ResultExplainer(llm));
        AggregatorService without = service(SearchPlanner.rulesOnly(), ResultExplainer.disabled());

        AggregatorResponse a = withLlm.search("laptop under 40k in Tumkur", 10);
        AggregatorResponse b = without.search("laptop under 40k in Tumkur", 10);

        assertEquals("Best pick is the first one: it is trusted.", a.aiExplanation(), "markdown is stripped");
        assertEquals("gemini", a.explainedBy());
        assertEquals(null, b.aiExplanation());
        assertEquals(b.results().size(), a.results().size());
        for (int i = 0; i < a.results().size(); i++) {
            assertEquals(b.results().get(i).listing().id(), a.results().get(i).listing().id());
            assertEquals(b.results().get(i).trust().score(), a.results().get(i).trust().score());
        }
        // the prompt carries the computed trust data and forbids invention
        assertTrue(llm.lastPrompt.contains("trust: "));
        assertTrue(llm.lastPrompt.contains("do not invent"));
    }

    @Test
    void explainerFailureOrBlankReplyFallsBackToTheBuiltInSummary() {
        for (LlmTextGenerator bad : List.of(new FakeLlm(true, "   ", false), new FakeLlm(true, null, false), new FakeLlm(true, "x", true))) {
            AggregatorResponse r = service(SearchPlanner.rulesOnly(), new ResultExplainer(bad)).search("laptop under 40k", 10);
            assertEquals(null, r.aiExplanation());
            assertFalse(r.results().isEmpty());
            assertTrue(r.summary().startsWith("Searched "));
        }
    }

    @Test
    void coverageOfSimulatedSourcesComesFromTheirDatasets() {
        SourceAdapter fb = sources().get(1);
        assertTrue(fb.coverage().contains(Category.ELECTRONICS));
        assertFalse(fb.coverage().contains(Category.HOUSING));
        assertEquals(Set.of(), new SourceAdapter() {
            public SourceInfo info() { return null; }
            public List<RawListing> search(SearchIntent i) { return List.of(); }
        }.coverage());
    }
}
