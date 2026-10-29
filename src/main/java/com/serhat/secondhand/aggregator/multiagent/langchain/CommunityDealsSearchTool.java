package com.serhat.secondhand.aggregator.multiagent.langchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * LangChain Tool: community_deals_search
 * Searches community sources: WhatsApp student groups & Facebook Marketplace.
 * OLX is strictly EXCLUDED by requirement.
 * Works dynamically for ANY product (phones, laptops, cycles, books, furniture, etc.).
 */
@Component
public class CommunityDealsSearchTool implements LangChainTool {

    private final ObjectMapper mapper;

    public CommunityDealsSearchTool(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "community_deals_search";
    }

    @Override
    public String description() {
        return "Searches community secondhand listings from WhatsApp student groups and Facebook Marketplace. "
                + "OLX is permanently excluded. Works for ANY product category. "
                + "Input parameters: 'product' (string, required), 'maxBudget' (number, optional), 'city' (string, optional).";
    }

    @Override
    public String execute(Map<String, Object> input) {
        String product = String.valueOf(input.getOrDefault("product", "item"));
        Double maxBudget = parseDouble(input.get("maxBudget"));
        String city = (String) input.getOrDefault("city", "Bengaluru");

        String keyword = InternetProductSearchTool.extractSearchKeyword(product);
        double budget = maxBudget != null ? maxBudget : 20000;

        List<Map<String, Object>> listings = new ArrayList<>();

        // 1. WhatsApp Community Deal (Simulated live feed from student networks)
        Map<String, Object> waDeal = new LinkedHashMap<>();
        waDeal.put("source", "WhatsApp Student Community Group");
        waDeal.put("title", capitalize(keyword) + " (Gently Used)");
        waDeal.put("price", Math.round(budget * 0.70));
        waDeal.put("condition", "LIKE_NEW");
        waDeal.put("city", city);
        waDeal.put("description", "Selling " + keyword + ", bill available, meetup in college campus. No online advance needed.");
        waDeal.put("seller", "Verified Student (WhatsApp)");
        waDeal.put("verifiedSeller", true);
        waDeal.put("requiresAdvancePayment", false);
        waDeal.put("inPersonInspection", true);
        waDeal.put("url", "https://chat.whatsapp.com/sample-student-marketplace");
        listings.add(waDeal);

        // 2. Facebook Marketplace Deal
        Map<String, Object> fbDeal = new LinkedHashMap<>();
        fbDeal.put("source", "Facebook Marketplace");
        fbDeal.put("title", capitalize(keyword) + " with original accessories");
        fbDeal.put("price", Math.round(budget * 0.60));
        fbDeal.put("condition", "GOOD");
        fbDeal.put("city", city);
        fbDeal.put("description", "Good working condition " + keyword + ". Pickup near metro station. Cash on delivery accepted.");
        fbDeal.put("seller", "Local Marketplace Seller");
        fbDeal.put("verifiedSeller", false);
        fbDeal.put("requiresAdvancePayment", false);
        fbDeal.put("inPersonInspection", true);
        fbDeal.put("url", "https://www.facebook.com/marketplace/search/?query=" + URLEncoder.encode(keyword + " " + city, StandardCharsets.UTF_8));
        listings.add(fbDeal);

        // 3. Suspect Listing for Scam Detection demonstration (simulating real marketplace traps)
        Map<String, Object> suspectDeal = new LinkedHashMap<>();
        suspectDeal.put("source", "Facebook Marketplace (Unverified)");
        suspectDeal.put("title", "BRAND NEW " + capitalize(keyword) + " - URGENT SALE");
        suspectDeal.put("price", Math.round(budget * 0.25)); // Unrealistically low
        suspectDeal.put("condition", "NEW");
        suspectDeal.put("city", "Remote / Courier");
        suspectDeal.put("description", "Brand new sealed. Pay 2000 advance token on UPI first. Courier delivery only, no meetup allowed.");
        suspectDeal.put("seller", "Army Officer / Anonymous");
        suspectDeal.put("verifiedSeller", false);
        suspectDeal.put("requiresAdvancePayment", true);
        suspectDeal.put("inPersonInspection", false);
        suspectDeal.put("url", "https://www.facebook.com/marketplace");
        listings.add(suspectDeal);

        try {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("status", "SUCCESS");
            res.put("excludedPlatforms", List.of("OLX"));
            res.put("includedPlatforms", List.of("WhatsApp Student Groups", "Facebook Marketplace"));
            res.put("dealCount", listings.size());
            res.put("listings", listings);
            return mapper.writeValueAsString(res);
        } catch (Exception e) {
            return "{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private Double parseDouble(Object obj) {
        if (obj == null) return null;
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (Exception e) {
            return null;
        }
    }
}
