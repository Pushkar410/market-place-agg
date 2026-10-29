package com.serhat.secondhand.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serhat.secondhand.aggregator.domain.*;
import com.serhat.secondhand.aggregator.intent.IntentService;
import com.serhat.secondhand.aggregator.intent.GeminiIntentParser;
import com.serhat.secondhand.aggregator.intent.RuleBasedIntentParser;
import com.serhat.secondhand.aggregator.normalize.DuplicateDetector;
import com.serhat.secondhand.aggregator.normalize.ListingNormalizer;
import com.serhat.secondhand.aggregator.rank.RankingService;
import com.serhat.secondhand.aggregator.service.AggregatorService;
import com.serhat.secondhand.aggregator.source.SimulatedSourceAdapter;
import com.serhat.secondhand.aggregator.source.SourceAdapter;
import com.serhat.secondhand.aggregator.trust.TrustScoringService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end pipeline test over the bundled simulated datasets (no database, no network). */
class AggregatorServiceTest {

    private final AggregatorService service = buildService(false);

    private static AggregatorService buildService(boolean failingSource) {
        ObjectMapper mapper = new ObjectMapper();
        List<SourceAdapter> adapters = new ArrayList<>();
        for (String f : List.of("olx", "facebook-marketplace", "whatsapp-groups", "local-niche")) {
            adapters.add(new SimulatedSourceAdapter("/aggregator/" + f + ".json", mapper));
        }
        if (failingSource) {
            adapters.add(new SourceAdapter() {
                public SourceInfo info() { return new SourceInfo("broken", "Broken source", "TEST", false, 0.5); }
                public List<RawListing> search(SearchIntent intent) { throw new IllegalStateException("boom"); }
            });
        }
        // Rule-based only: pass a null-safe Gemini parser that reports unavailable.
        IntentService intent = new IntentService(new GeminiIntentParser(null, mapper), new RuleBasedIntentParser());
        return new AggregatorService(intent, adapters, new ListingNormalizer(), new DuplicateDetector(), new TrustScoringService(), new RankingService());
    }

    @Test
    void laptopSearchMergesDuplicatesAndPushesScamsToTheBottom() {
        AggregatorResponse r = service.search("laptop under 35k in Tumkur trusted sellers", 10);

        assertTrue(r.duplicatesRemoved() >= 1, "HP Pavilion is listed on two platforms");
        assertFalse(r.results().isEmpty());
        assertEquals(1, r.results().get(0).rank());
        assertEquals(TrustScore.Level.HIGH, r.results().get(0).trust().level());
        assertTrue(r.results().get(0).trust().score() > 80);

        // every LOW trust listing must rank below every HIGH trust listing
        int lastHigh = -1, firstLow = Integer.MAX_VALUE;
        for (RankedListing x : r.results()) {
            if (x.trust().level() == TrustScore.Level.HIGH) lastHigh = Math.max(lastHigh, x.rank());
            if (x.trust().level() == TrustScore.Level.LOW) firstLow = Math.min(firstLow, x.rank());
        }
        assertTrue(lastHigh < firstLow);
        assertTrue(r.results().stream().noneMatch(x -> x.listing().price() != null && x.listing().price() > 35_000));
    }

    @Test
    void scamListingNeverWinsEvenWhenItIsTheCheapestMatch() {
        AggregatorResponse r = service.search("cheapest iphone 13", 10);
        for (RankedListing x : r.results()) {
            if (x.listing().title().contains("only 8000")) {
                assertEquals(TrustScore.Level.LOW, x.trust().level());
                assertTrue(x.rank() > 1 || r.results().size() == 1);
            }
        }
    }

    @Test
    void duplicateOfTheSameItemAppearsOnlyOnce() {
        AggregatorResponse r = service.search("hero sprint cycle", 10);
        long count = r.results().stream().filter(x -> x.listing().title().toLowerCase().contains("hero sprint")).count();
        assertEquals(2, count, "OLX/WhatsApp copy merged, Facebook copy has a different price and seller");
    }

    @Test
    void aFailingSourceDoesNotBreakTheSearch() {
        AggregatorResponse r = buildService(true).search("laptop", 10);
        assertFalse(r.results().isEmpty());
        assertTrue(r.sources().stream().anyMatch(s -> s.id().equals("broken") && s.status().equals("ERROR")));
    }

    @Test
    void agentTraceDescribesEveryPipelineStep() {
        AggregatorResponse r = service.search("textbooks engineering maths", 5);
        String trace = String.join("\n", r.agentTrace());
        for (String step : List.of("Understand", "Search", "Normalize", "Aggregate", "Analyze/Score/Rank", "Recommend")) {
            assertTrue(trace.contains(step), "missing step " + step);
        }
    }
}
