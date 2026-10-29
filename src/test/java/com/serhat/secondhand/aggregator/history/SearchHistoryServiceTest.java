package com.serhat.secondhand.aggregator.history;

import com.serhat.secondhand.aggregator.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Mapping from an agent response to the rows persisted in PostgreSQL (no database needed). */
class SearchHistoryServiceTest {

    private static RankedListing ranked(int rank, String id, int score, TrustScore.Level level, List<String> flags) {
        NormalizedListing l = new NormalizedListing(id, "olx", "OLX", "Laptop " + rank, "desc", 30000.0 + rank, "INR", null, "Tumkur",
                Category.ELECTRONICS, "GOOD", List.of(), "https://x", 3, "2026-10-01", null, Set.of());
        return new RankedListing(rank, l, new TrustScore(score, level, List.of(), flags), 1, 0.5, 1, 0.5, 0.8, List.of(), List.of());
    }

    private static AggregatorResponse response(List<RankedListing> results, String clarification) {
        SearchIntent intent = new SearchIntent("q", List.of("laptop"), Category.ELECTRONICS, null, null, null, null, SearchIntent.Preference.BALANCED, "rule-based");
        return new AggregatorResponse("laptop under 40k", intent, List.of(), 7, 1, results.size(), results, "s", List.of(), 123,
                new SearchPlan(List.of("olx"), List.of(), "r", "rules"), clarification, null, null);
    }

    @Test
    void searchIsMappedWithHeadlineNumbers() {
        SearchHistory h = SearchHistoryService.toHistory(response(List.of(ranked(1, "olx:1", 88, TrustScore.Level.HIGH, List.of())), null), "a@b.com");
        assertEquals("laptop under 40k", h.getQueryText());
        assertEquals("a@b.com", h.getUserEmail());
        assertEquals("rule-based", h.getInterpretedBy());
        assertEquals("rules", h.getPlannedBy());
        assertEquals(7, h.getTotalFetched());
        assertEquals(88, h.getTopTrustScore().intValue());
        assertFalse(h.isClarificationAsked());
    }

    @Test
    void anonymousSearchHasNoUserAndClarificationIsFlagged() {
        SearchHistory h = SearchHistoryService.toHistory(response(List.of(), "What are you looking for?"), null);
        assertNull(h.getUserEmail());
        assertNull(h.getTopTrustScore());
        assertTrue(h.isClarificationAsked());
    }

    @Test
    void everyReturnedListingGetsATrustRecordWithItsRiskFlags() {
        AggregatorResponse r = response(List.of(
                ranked(1, "olx:1", 88, TrustScore.Level.HIGH, List.of()),
                ranked(2, "olx:2", 20, TrustScore.Level.LOW, List.of("Asks for advance / token payment", "No photos"))), null);

        List<ListingTrustRecord> rows = SearchHistoryService.toTrustRecords(r, 42L);

        assertEquals(2, rows.size());
        assertEquals(42L, rows.get(0).getSearchId().longValue());
        assertEquals("olx:1", rows.get(0).getListingKey());
        assertNull(rows.get(0).getRiskFlags());
        assertEquals("LOW", rows.get(1).getTrustLevel());
        assertEquals(20, rows.get(1).getTrustScore());
        assertEquals("Asks for advance / token payment | No photos", rows.get(1).getRiskFlags());
    }

    @Test
    void overlongValuesAreCutToTheColumnSize() {
        String longQuery = "x".repeat(500);
        AggregatorResponse base = response(List.of(), null);
        AggregatorResponse r = new AggregatorResponse(longQuery, base.intent(), List.of(), 0, 0, 0, List.of(), "s", List.of(), 1, null, null, null, null);
        assertEquals(300, SearchHistoryService.toHistory(r, null).getQueryText().length());
    }
}
