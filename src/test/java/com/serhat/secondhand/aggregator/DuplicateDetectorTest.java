package com.serhat.secondhand.aggregator;

import com.serhat.secondhand.aggregator.domain.NormalizedListing;
import com.serhat.secondhand.aggregator.normalize.DuplicateDetector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.serhat.secondhand.aggregator.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

class DuplicateDetectorTest {

    private final DuplicateDetector detector = new DuplicateDetector();

    @Test
    void sameItemOnTwoPlatformsIsMergedAndKeepsMostCompleteListing() {
        NormalizedListing olx = norm("olx", "1", "HP Pavilion 15 laptop i5 8GB 512GB SSD",
                "Detailed description of the laptop that is definitely longer than sixty characters in total.", 32000.0, "Tumkur", 4, 6, seller(true, 4.8, 23, 900, "a"));
        NormalizedListing fb = norm("fb", "9", "HP Pavilion 15 i5 10th gen laptop 8GB/512GB",
                "Short", 31500.0, "Tumakuru", 1, 4, seller(true, 4.7, 9, 600, "b"));

        List<DuplicateDetector.Cluster> clusters = detector.cluster(List.of(olx, fb));

        assertEquals(1, clusters.size());
        assertEquals("olx:1", clusters.get(0).primary().id());
        assertEquals(1, clusters.get(0).duplicates().size());
    }

    @Test
    void differentProductsAreNotMerged() {
        NormalizedListing a = norm("olx", "1", "Dell Inspiron 14 laptop i3", "desc", 17500.0, "Tumakuru", 1, 5, null);
        NormalizedListing b = norm("olx", "2", "HP Pavilion 15 laptop i5", "desc", 32000.0, "Tumakuru", 1, 5, null);
        assertEquals(2, detector.cluster(List.of(a, b)).size());
    }

    @Test
    void sameTitleButContradictingPriceIsNotMerged() {
        NormalizedListing a = norm("olx", "1", "Hero Sprint gear cycle", "desc", 4000.0, "Tumakuru", 1, 5, null);
        NormalizedListing b = norm("fb", "2", "Hero Sprint gear cycle", "desc", 9000.0, "Tumakuru", 1, 5, null);
        assertEquals(2, detector.cluster(List.of(a, b)).size());
    }

    @Test
    void differentCitiesAreNotMerged() {
        NormalizedListing a = norm("olx", "1", "Hero Sprint gear cycle", "desc", 4000.0, "Tumakuru", 1, 5, null);
        NormalizedListing b = norm("fb", "2", "Hero Sprint gear cycle", "desc", 4000.0, "Mysuru", 1, 5, null);
        assertEquals(2, detector.cluster(List.of(a, b)).size());
    }

    @Test
    void identicalSellerContactLowersTheTitleThreshold() {
        NormalizedListing a = norm("olx", "1", "Boat Rockerz 450 headphones", "desc", 900.0, "Tumakuru", 1, 5, seller(true, 4.5, 5, 300, "same"));
        NormalizedListing b = norm("wa", "2", "Boat Rockerz 450 wireless headset", "desc", 950.0, "Tumakuru", 0, 7, seller(true, 4.5, 5, 300, "same"));
        assertEquals(1, detector.cluster(List.of(a, b)).size());
    }
}
