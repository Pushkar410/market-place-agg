package com.serhat.secondhand.aggregator;

import com.serhat.secondhand.aggregator.domain.Category;
import com.serhat.secondhand.aggregator.domain.SearchIntent;
import com.serhat.secondhand.aggregator.intent.RuleBasedIntentParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedIntentParserTest {

    private final RuleBasedIntentParser parser = new RuleBasedIntentParser();

    @Test
    void parsesBudgetCityCategoryAndPreference() {
        SearchIntent i = parser.parse("I need a laptop under 35k in Tumkur, trusted sellers only");
        assertEquals(35_000.0, i.maxPrice());
        assertEquals("Tumakuru", i.city());
        assertEquals(Category.ELECTRONICS, i.category());
        assertEquals(SearchIntent.Preference.TRUSTED, i.preference());
        assertTrue(i.keywords().contains("laptop"));
        assertFalse(i.keywords().contains("sellers"));
    }

    @Test
    void parsesBetweenRangeAndCommas() {
        SearchIntent i = parser.parse("study table between 2,000 and 4k");
        assertEquals(2_000.0, i.minPrice());
        assertEquals(4_000.0, i.maxPrice());
        assertEquals(Category.FURNITURE, i.category());
    }

    @Test
    void parsesLakhAndCondition() {
        SearchIntent i = parser.parse("bike like new below 1 lakh");
        assertEquals(100_000.0, i.maxPrice());
        assertEquals("LIKE_NEW", i.condition());
        assertEquals(Category.VEHICLES, i.category());
    }

    @Test
    void keepsShortModelNumbersButDropsBudgetNumbers() {
        SearchIntent i = parser.parse("iphone 13 under 40000");
        assertTrue(i.keywords().contains("iphone"));
        assertTrue(i.keywords().contains("13"));
        assertFalse(i.keywords().contains("40000"));
    }

    @Test
    void cheapestPreferenceDetected() {
        assertEquals(SearchIntent.Preference.CHEAPEST, parser.parse("cheapest cycle").preference());
    }

    @Test
    void genericCategoryLabelSetsCategoryButIsNotAKeyword() {
        SearchIntent i = new RuleBasedIntentParser().parse("electronics under 20k");
        assertEquals(Category.ELECTRONICS, i.category());
        assertTrue(i.keywords().isEmpty());
        assertEquals(20000.0, i.maxPrice());

        SearchIntent b = new RuleBasedIntentParser().parse("physics books in Tumkur");
        assertEquals(Category.BOOKS, b.category());
        assertEquals(List.of("physics"), b.keywords());
    }
}
