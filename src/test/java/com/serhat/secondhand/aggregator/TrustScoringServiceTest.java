package com.serhat.secondhand.aggregator;

import com.serhat.secondhand.aggregator.domain.NormalizedListing;
import com.serhat.secondhand.aggregator.domain.TrustScore;
import com.serhat.secondhand.aggregator.trust.TrustScoringService;
import org.junit.jupiter.api.Test;

import static com.serhat.secondhand.aggregator.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

class TrustScoringServiceTest {

    private final TrustScoringService service = new TrustScoringService();

    @Test
    void verifiedWellReviewedSellerWithGoodListingScoresHigh() {
        NormalizedListing l = norm("olx", "1", "HP Pavilion 15 laptop",
                "Detailed description that is clearly longer than eighty characters so that the quality signal is fully awarded.",
                32000.0, "Tumakuru", 4, 3, seller(true, 4.8, 23, 900, "a"));
        TrustScore t = service.score(l, 31000.0, 5, 0.6, 2);
        assertEquals(TrustScore.Level.HIGH, t.level());
        assertTrue(t.score() >= 85, "score was " + t.score());
        assertTrue(t.riskFlags().isEmpty(), t.riskFlags().toString());
    }

    @Test
    void scamLanguageAndBaitPriceScoreLowAndAreFlagged() {
        NormalizedListing l = norm("fb", "2", "iPhone 13 only 8000",
                "Pay advance token amount. Courier will deliver after payment. No meetup.",
                8000.0, "Bengaluru", 1, 0, seller(false, null, 0, 2, "z"));
        TrustScore t = service.score(l, 30000.0, 4, 0.5, 1);
        assertEquals(TrustScore.Level.LOW, t.level());
        assertTrue(t.score() < 30, "score was " + t.score());
        assertTrue(t.riskFlags().stream().anyMatch(f -> f.contains("advance")));
        assertTrue(t.riskFlags().stream().anyMatch(f -> f.toLowerCase().contains("below market")));
    }

    @Test
    void fewReviewsAreSmoothedTowardsNeutral() {
        NormalizedListing oneReview = norm("olx", "3", "Table", "A study table in good condition for hostel use, sturdy wood.", 2000.0, "Tumakuru", 2, 3, seller(true, 5.0, 1, 400, "a"));
        NormalizedListing manyReviews = norm("olx", "4", "Table", "A study table in good condition for hostel use, sturdy wood.", 2000.0, "Tumakuru", 2, 3, seller(true, 4.8, 40, 400, "b"));
        double one = service.score(oneReview, null, 0, 0.6, 1).signals().get(0).points();
        double many = service.score(manyReviews, null, 0, 0.6, 1).signals().get(0).points();
        assertTrue(many > one, "40 reviews at 4.8 should outweigh 1 review at 5.0");
    }

    @Test
    void staleListingsLoseFreshnessPointsAndAreFlagged() {
        NormalizedListing old = norm("olx", "5", "Monitor", "Samsung monitor in working condition without dead pixels at all.", 3000.0, "Tumakuru", 1, 200, seller(false, 4.0, 3, 200, "c"));
        TrustScore t = service.score(old, null, 0, 0.6, 1);
        assertTrue(t.riskFlags().stream().anyMatch(f -> f.contains("200 days old")));
        assertEquals(0.0, t.signals().stream().filter(s -> s.name().equals("Freshness")).findFirst().orElseThrow().points());
    }

    @Test
    void scoreIsAlwaysWithinZeroAndHundred() {
        NormalizedListing l = norm("x", "6", "Gift card western union crypto", "advance payment token amount army officer only whatsapp no visit", 10.0, null, 0, 400, null);
        TrustScore t = service.score(l, 1000.0, 5, 0.1, 1);
        assertTrue(t.score() >= 0 && t.score() <= 100);
    }
}
