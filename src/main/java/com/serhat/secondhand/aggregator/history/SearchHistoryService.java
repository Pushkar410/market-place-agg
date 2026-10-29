package com.serhat.secondhand.aggregator.history;

import com.serhat.secondhand.aggregator.domain.AggregatorResponse;
import com.serhat.secondhand.aggregator.domain.RankedListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists every search and the trust score of each returned listing to PostgreSQL.
 * Recording is best-effort: a database problem must never break a user's search.
 */
@Service
public class SearchHistoryService {

    private static final Logger log = LoggerFactory.getLogger(SearchHistoryService.class);

    private final SearchHistoryRepository histories;
    private final ListingTrustRecordRepository trustRecords;

    public SearchHistoryService(SearchHistoryRepository histories, ListingTrustRecordRepository trustRecords) {
        this.histories = histories;
        this.trustRecords = trustRecords;
    }

    /** @param userEmail the signed-in user, or null for an anonymous search */
    @Transactional
    public void record(AggregatorResponse response, String userEmail) {
        try {
            SearchHistory h = toHistory(response, userEmail);
            histories.save(h);
            List<ListingTrustRecord> rows = toTrustRecords(response, h.getId());
            if (!rows.isEmpty()) trustRecords.saveAll(rows);
        } catch (RuntimeException e) {
            log.warn("Could not record search history (search result is unaffected): {}", e.toString());
        }
    }

    @Transactional(readOnly = true)
    public List<SearchHistory> recentFor(String userEmail) {
        return userEmail == null ? List.of() : histories.findTop20ByUserEmailOrderByCreatedAtDesc(userEmail);
    }

    // ---- pure mapping (unit-tested without a database)

    static SearchHistory toHistory(AggregatorResponse r, String userEmail) {
        SearchHistory h = new SearchHistory();
        h.setQueryText(cut(r.query(), 300));
        h.setUserEmail(userEmail == null ? null : cut(userEmail, 254));
        h.setInterpretedBy(r.intent() == null ? null : cut(r.intent().interpretedBy(), 40));
        h.setPlannedBy(r.plan() == null ? null : cut(r.plan().plannedBy(), 40));
        h.setClarificationAsked(r.clarification() != null);
        h.setTotalFetched(r.totalFetched());
        h.setDuplicatesRemoved(r.duplicatesRemoved());
        h.setTotalRanked(r.totalRanked());
        h.setTopTrustScore(r.results().isEmpty() ? null : r.results().get(0).trust().score());
        h.setTookMs(r.tookMs());
        return h;
    }

    static List<ListingTrustRecord> toTrustRecords(AggregatorResponse r, Long searchId) {
        List<ListingTrustRecord> out = new ArrayList<>();
        for (RankedListing x : r.results()) {
            ListingTrustRecord t = new ListingTrustRecord();
            t.setSearchId(searchId);
            t.setListingKey(cut(x.listing().id(), 200));
            t.setSourceId(cut(x.listing().sourceId(), 100));
            t.setTitle(cut(x.listing().title(), 300));
            t.setPrice(x.listing().price());
            t.setResultRank(x.rank());
            t.setTrustScore(x.trust().score());
            t.setTrustLevel(x.trust().level().name());
            t.setFinalScore(x.finalScore());
            t.setRiskFlags(x.trust().riskFlags().isEmpty() ? null : cut(String.join(" | ", x.trust().riskFlags()), 2000));
            out.add(t);
        }
        return out;
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
