package com.serhat.secondhand.aggregator;

import com.serhat.secondhand.aggregator.domain.NormalizedListing;
import com.serhat.secondhand.aggregator.domain.RawListing;
import com.serhat.secondhand.aggregator.normalize.ListingNormalizer;

import java.util.List;

/** Small builders shared by the aggregator unit tests. */
final class TestData {
    private TestData() {}

    static RawListing.Seller seller(boolean verified, Double rating, int reviews, int ageDays, String hash) {
        return new RawListing.Seller("Seller", verified, rating, reviews, ageDays, true, hash);
    }

    static RawListing raw(String source, String id, String title, String desc, Double price, String city,
                          int images, int daysAgo, RawListing.Seller seller) {
        List<String> imgs = new java.util.ArrayList<>();
        for (int i = 0; i < images; i++) imgs.add("https://img/" + id + "/" + i);
        return new RawListing(source, id, title, desc, price, "INR", city, null, "GOOD", imgs, "https://x/" + id, daysAgo, seller);
    }

    static NormalizedListing norm(String source, String id, String title, String desc, Double price, String city,
                                  int images, int daysAgo, RawListing.Seller seller) {
        return new ListingNormalizer().normalize(raw(source, id, title, desc, price, city, images, daysAgo, seller), source).orElseThrow();
    }
}
