package com.serhat.secondhand.aggregator.community;

import com.serhat.secondhand.aggregator.domain.RawListing;
import com.serhat.secondhand.aggregator.domain.SearchIntent;
import com.serhat.secondhand.aggregator.intent.RuleBasedIntentParser;
import com.serhat.secondhand.aggregator.normalize.ListingNormalizer;
import com.serhat.secondhand.aggregator.trust.TrustScoringService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CommunityListingTest {

    private static CommunityListing listing(String title, String desc, String category, String url, String contact) {
        CommunityListing c = new CommunityListing();
        c.setTitle(title);
        c.setDescription(desc);
        c.setPrice(5500.0);
        c.setCity("Tumkur");
        c.setCategory(category);
        c.setOriginUrl(url);
        c.setContactName(contact);
        c.setContactProvided(true);
        c.setCreatedAt(LocalDateTime.now().minusDays(2));
        return c;
    }

    @Test
    void keywordsMatchTitleOrDescriptionAndCategoryOnlyQueriesWork() {
        CommunityListing monitor = listing("Samsung 24 inch monitor", "Full HD, HDMI cable included", "ELECTRONICS", null, "Karthik");
        RuleBasedIntentParser p = new RuleBasedIntentParser();
        assertTrue(CommunityListingAdapter.matches(monitor, p.parse("monitor under 8000")));
        assertFalse(CommunityListingAdapter.matches(monitor, p.parse("bicycle")));
        assertTrue(CommunityListingAdapter.matches(monitor, p.parse("electronics under 20k")));
        assertFalse(CommunityListingAdapter.matches(monitor, p.parse("housing under 20k")));
    }

    @Test
    void sharedListingBecomesAnUnverifiedRawListingThatLinksBackToTheOriginalPost() {
        RawListing raw = CommunityListingAdapter.toRaw(listing("Samsung monitor", "Full HD monitor in good shape with cable", "ELECTRONICS", "https://olx.in/x", "Karthik"));
        assertEquals("community-shared", raw.sourceId());
        assertEquals("https://olx.in/x", raw.url());
        assertEquals("INR", raw.currency());
        assertFalse(raw.seller().verified());
        assertTrue(raw.seller().phoneProvided());
        assertEquals("Karthik", raw.seller().name());
        assertEquals(2, raw.postedDaysAgo().intValue());
    }

    @Test
    void communityListingsAreScoredAsUnverifiedNotAsHighTrust() {
        RawListing raw = CommunityListingAdapter.toRaw(listing("Samsung monitor", "Full HD monitor in good shape with cable, no dead pixels", "ELECTRONICS", "https://olx.in/x", "Karthik"));
        var n = new ListingNormalizer().normalize(raw, "Community Shared (user submitted)").orElseThrow();
        var score = new TrustScoringService().score(n, null, 0, 0.45, 1);
        assertTrue(score.score() < 75, "unverified, no reviews, no photos can never be HIGH");
        assertTrue(score.riskFlags().contains("Seller identity not verified"));
    }

    @Test
    void scamTextInASharedListingIsStillFlaggedByTheTrustEngine() {
        RawListing raw = CommunityListingAdapter.toRaw(listing("iPhone 14 sealed", "Pay advance token amount first, courier will deliver, no meetup", "ELECTRONICS", null, null));
        var n = new ListingNormalizer().normalize(raw, "Community Shared (user submitted)").orElseThrow();
        var score = new TrustScoringService().score(n, null, 0, 0.45, 1);
        assertTrue(score.riskFlags().contains("Asks for advance / token payment"));
    }

    @Test
    void onlyHttpLinksAreAcceptedAsOriginUrls() {
        assertTrue(CommunityListingService.isHttpUrl("https://olx.in/item/1"));
        assertTrue(CommunityListingService.isHttpUrl("http://example.com/a"));
        assertFalse(CommunityListingService.isHttpUrl("javascript:alert(1)"));
        assertFalse(CommunityListingService.isHttpUrl("data:text/html,x"));
        assertFalse(CommunityListingService.isHttpUrl("https://a b.com"));
        assertFalse(CommunityListingService.isHttpUrl(null));
    }

    @Test
    void textIsCleanedTrimmedAndCut() {
        assertNull(CommunityListingService.clean("   ", 10));
        assertNull(CommunityListingService.clean(null, 10));
        assertEquals("a b", CommunityListingService.clean("  a \t  b\u0007 ", 10));
        assertEquals("abcde", CommunityListingService.clean("abcdefghij", 5));
    }
}
