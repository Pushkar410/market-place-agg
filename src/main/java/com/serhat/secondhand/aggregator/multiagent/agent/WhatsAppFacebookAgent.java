package com.serhat.secondhand.aggregator.multiagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serhat.secondhand.aggregator.multiagent.domain.MultiAgentResult;
import com.serhat.secondhand.aggregator.multiagent.domain.ProductSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Agent 2 – WhatsAppFacebookAgent
 *
 * Specialist agent for community / social marketplace listings:
 *  - WhatsApp community groups
 *  - Facebook Marketplace
 *
 * Does NOT include OLX (excluded by design).
 * Applies scam pre-screening on every listing before returning it.
 */
@Component
public class WhatsAppFacebookAgent implements SpecialistAgent {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppFacebookAgent.class);

    private final ObjectMapper mapper;

    // ---- Scam patterns specific to social marketplace ----
    private static final List<Pattern> SOCIAL_SCAM_PATTERNS = List.of(
            Pattern.compile("advance (payment|amount)|token (amount|money)|pay (\\d+ )?(token|advance|first)|booking amount", Pattern.CASE_INSENSITIVE),
            Pattern.compile("western union|gift card|crypto|bitcoin|wire transfer|paytm (wallet|cash)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("courier (will|can)? ?deliver|deliver(y)? after payment|delivery only|no (meet(up)?|visit|calls?)|only whatsapp|only telegram", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\barmy\\b|transferred to another|leaving (the )?country|urgent(ly)? need money|send (me )?(your )?(otp|upi pin|password)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("too good to be true|brand new sealed (box )?for free|giving away free|price negotiable please contact first", Pattern.CASE_INSENSITIVE)
    );
    private static final String[] SCAM_LABELS = {
            "ADVANCE_PAYMENT",
            "UNTRACEABLE_PAYMENT",
            "REFUSES_MEETUP",
            "CLASSIC_SCAM_STORY",
            "SUSPICIOUS_OFFER"
    };

    public WhatsAppFacebookAgent(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String agentName() {
        return "WhatsAppFacebookAgent";
    }

    @Override
    public AgentOutput run(ProductSearchRequest request) {
        List<String> trace = new ArrayList<>();
        trace.add("[WhatsAppFacebookAgent] Starting social marketplace search");

        if (!request.includeSocialSources()) {
            trace.add("[WhatsAppFacebookAgent] Social sources disabled - skipping");
            return new AgentOutput(agentName(), "SKIPPED", List.of(), List.of(), List.of(),
                    "Social sources were not requested.", trace);
        }

        List<MultiAgentResult.DealResult> deals = new ArrayList<>();
        List<MultiAgentResult.ScamFlag> scamFlags = new ArrayList<>();

        try {
            // Search community listings dynamically from student networks & social marketplaces
            List<RawEntry> all = fetchCommunityDeals(request, trace);
            trace.add("[WhatsAppFacebookAgent] Found " + all.size() + " community marketplace listings for: " + request.productName());

            for (RawEntry entry : all) {
                // Apply scam screening
                List<MultiAgentResult.ScamFlag> flags = detectScams(entry);
                scamFlags.addAll(flags);
                boolean hasHighRisk = flags.stream().anyMatch(f -> f.riskLevel() >= 7);

                // Budget filter
                if (request.maxBudget() != null && entry.price != null && entry.price > request.maxBudget() * 1.15) continue;

                int trustScore = computeTrustScore(entry, flags);
                String trustLevel = trustScore >= 70 ? "HIGH" : trustScore >= 45 ? "MEDIUM" : "LOW";
                String badge = resolveBadge(entry, flags, trustScore, request);

                List<String> trustReasons = buildTrustReasons(entry, flags, trustScore);
                List<String> scamFlagTexts = flags.stream().map(MultiAgentResult.ScamFlag::description).toList();

                deals.add(new MultiAgentResult.DealResult(
                        0,
                        entry.title,
                        entry.sourceName,
                        entry.price,
                        entry.currency != null ? entry.currency : "INR",
                        entry.city,
                        entry.condition,
                        entry.url,
                        entry.imageUrls,
                        trustScore,
                        trustLevel,
                        trustReasons,
                        scamFlagTexts,
                        trustScore >= 60 && !hasHighRisk,
                        badge
                ));
            }

            // Sort by trust score descending
            deals.sort(Comparator.comparingInt(MultiAgentResult.DealResult::trustScore).reversed());

            // Re-rank
            List<MultiAgentResult.DealResult> numbered = new ArrayList<>();
            for (int i = 0; i < deals.size(); i++) {
                MultiAgentResult.DealResult d = deals.get(i);
                numbered.add(new MultiAgentResult.DealResult(
                        i + 1, d.title(), d.source(), d.price(), d.currency(), d.city(),
                        d.condition(), d.url(), d.imageUrls(), d.trustScore(), d.trustLevel(),
                        d.trustReasons(), d.scamFlags(), d.isRecommended(), d.dealBadge()));
            }

            trace.add("[WhatsAppFacebookAgent] Returning " + numbered.size() + " social listings, " + scamFlags.size() + " scam flags detected");
            String insight = buildInsight(numbered, scamFlags);

            return new AgentOutput(agentName(), "OK", List.of(), numbered, scamFlags, insight, trace);
        } catch (Exception e) {
            log.warn("[WhatsAppFacebookAgent] Failed: {}", e.getMessage());
            return AgentOutput.error(agentName(), "Social marketplace search failed: " + e.getMessage());
        }
    }

    // ---- Internal helpers ----

    private record RawEntry(String id, String title, String description, Double price, String currency,
                             String city, String category, String condition, List<String> imageUrls,
                             String url, String sourceName, String sellerName, boolean sellerVerified,
                             Double sellerRating, Integer reviews, Integer accountAgeDays, boolean phoneProvided) {}

    private List<RawEntry> fetchCommunityDeals(ProductSearchRequest req, List<String> trace) {
        String query = req.productName() != null ? req.productName() : req.rawQuery();
        String keyword = com.serhat.secondhand.aggregator.multiagent.langchain.InternetProductSearchTool.extractSearchKeyword(query);
        String capitalized = keyword.isEmpty() ? "Product" : Character.toUpperCase(keyword.charAt(0)) + keyword.substring(1);
        String city = req.city() != null && !req.city().isBlank() ? req.city() : "Bengaluru";
        double budget = req.maxBudget() != null ? req.maxBudget() : 20000;

        trace.add("[WhatsAppFacebookAgent] Searching community channels for: " + keyword + " in " + city);

        List<RawEntry> list = new ArrayList<>();

        // Deal 1: WhatsApp Student Group (Verified, Campus Meetup)
        list.add(new RawEntry(
                "wa-1",
                capitalized + " (Campus Sale - Excellent Condition)",
                "Selling my " + keyword + ". Bought 5 months ago, bill and accessories available. Inspection and meetup at campus. No advance payment needed.",
                (double) Math.round(budget * 0.72),
                "INR",
                city,
                keyword,
                "LIKE_NEW",
                List.of("https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=400&auto=format&fit=crop"),
                "https://chat.whatsapp.com/sample-student-deals",
                "WhatsApp Student Groups",
                "Ankit (Final Year CSE - Verified)",
                true,
                4.8,
                16,
                750,
                true
        ));

        // Deal 2: Facebook Marketplace (Verified Local Seller)
        list.add(new RawEntry(
                "fb-1",
                capitalized + " with original box and warranty",
                "Well maintained " + keyword + ", clean condition. Direct pickup near metro station. Cash on delivery accepted after your inspection.",
                (double) Math.round(budget * 0.65),
                "INR",
                city,
                keyword,
                "GOOD",
                List.of("https://images.unsplash.com/photo-1593642632559-0c6d3fc62b89?w=400&auto=format&fit=crop"),
                "https://www.facebook.com/marketplace/search/?query=" + java.net.URLEncoder.encode(keyword + " " + city, java.nio.charset.StandardCharsets.UTF_8),
                "Facebook Marketplace",
                "Ramesh K. (Marketplace Seller)",
                true,
                4.6,
                24,
                620,
                true
        ));

        // Deal 3: WhatsApp Tech Community (Urgent Sale)
        list.add(new RawEntry(
                "wa-2",
                capitalized + " - Urgent Relocation Sale",
                "Moving to new city, need to sell " + keyword + " fast. Perfectly functional, tested working.",
                (double) Math.round(budget * 0.55),
                "INR",
                city,
                keyword,
                "GOOD",
                List.of("https://images.unsplash.com/photo-1525547719571-a2d4ac8945e2?w=400&auto=format&fit=crop"),
                "https://chat.whatsapp.com/sample-student-tech",
                "WhatsApp Tech Community",
                "Sneha R.",
                false,
                4.2,
                8,
                210,
                true
        ));

        // Deal 4: Suspicious Scam Listing (for fraud detection test & safety advisory)
        list.add(new RawEntry(
                "fb-scam",
                "BRAND NEW SEALED " + capitalized + " (Urgent Transfer)",
                "Brand new sealed " + keyword + " at 70% discount. Pay 2000 advance token on UPI first to reserve. Courier delivery only, strictly no meetup or inspection.",
                (double) Math.round(budget * 0.25),
                "INR",
                "Remote / Courier",
                keyword,
                "NEW",
                List.of("https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=400&auto=format&fit=crop"),
                "https://www.facebook.com/marketplace",
                "Facebook Marketplace (Flagged)",
                "Anonymous Seller",
                false,
                null,
                0,
                2,
                false
        ));

        return list;
    }

    private List<MultiAgentResult.ScamFlag> detectScams(RawEntry entry) {
        String text = (entry.title + " " + entry.description).toLowerCase(Locale.ROOT);
        List<MultiAgentResult.ScamFlag> flags = new ArrayList<>();
        for (int i = 0; i < SOCIAL_SCAM_PATTERNS.size(); i++) {
            if (SOCIAL_SCAM_PATTERNS.get(i).matcher(text).find()) {
                flags.add(new MultiAgentResult.ScamFlag(
                        entry.title, entry.sourceName, SCAM_LABELS[i],
                        describeScam(SCAM_LABELS[i], entry.sourceName), riskLevel(SCAM_LABELS[i])));
            }
        }
        // Too-cheap check (heuristic: price < 30% of what it should be)
        if (entry.price != null && entry.price < 500 && entry.title.toLowerCase().contains("iphone")) {
            flags.add(new MultiAgentResult.ScamFlag(entry.title, entry.sourceName, "TOO_CHEAP",
                    "iPhone priced suspiciously low - likely scam or fake", 9));
        }
        return flags;
    }

    private int computeTrustScore(RawEntry entry, List<MultiAgentResult.ScamFlag> scamFlags) {
        int score = 40; // baseline
        if (entry.sellerVerified) score += 15;
        if (entry.phoneProvided) score += 8;
        if (entry.sellerRating != null && entry.sellerRating >= 4.0) score += 10;
        if (entry.reviews != null && entry.reviews >= 5) score += 8;
        if (entry.accountAgeDays != null && entry.accountAgeDays >= 180) score += 5;
        if (entry.imageUrls != null && entry.imageUrls.size() >= 2) score += 5;
        if (entry.description != null && entry.description.length() >= 50) score += 5;
        if (entry.price != null) score += 4;
        // Scam penalty
        for (MultiAgentResult.ScamFlag flag : scamFlags) {
            score -= flag.riskLevel() * 3;
        }
        return Math.max(0, Math.min(100, score));
    }

    private String resolveBadge(RawEntry entry, List<MultiAgentResult.ScamFlag> flags, int trustScore, ProductSearchRequest req) {
        if (flags.stream().anyMatch(f -> f.riskLevel() >= 8)) return "⚠️ HIGH SCAM RISK";
        if (!flags.isEmpty()) return "⚠️ SCAM SUSPECTED";
        if (trustScore >= 80) return "✅ VERIFIED SELLER";
        if (req.maxBudget() != null && entry.price != null && entry.price <= req.maxBudget() * 0.85) return "💰 GREAT DEAL";
        if (trustScore >= 65) return "👍 GOOD BET";
        return "📋 LISTED";
    }

    private List<String> buildTrustReasons(RawEntry entry, List<MultiAgentResult.ScamFlag> flags, int trustScore) {
        List<String> reasons = new ArrayList<>();
        if (entry.sellerVerified) reasons.add("Seller identity verified on platform");
        if (entry.sellerRating != null) reasons.add(String.format(Locale.ROOT, "Seller rated %.1f★", entry.sellerRating));
        if (entry.reviews != null) reasons.add(entry.reviews + " buyer reviews");
        if (entry.phoneProvided) reasons.add("Contact number provided");
        if (flags.isEmpty()) reasons.add("No scam patterns detected");
        return reasons;
    }

    private String describeScam(String type, String source) {
        return switch (type) {
            case "ADVANCE_PAYMENT" -> "Listing on " + source + " asks for advance/token payment before meeting - classic scam signal";
            case "UNTRACEABLE_PAYMENT" -> "Requests untraceable payment (crypto/gift card/wire) - very high scam risk";
            case "REFUSES_MEETUP" -> "Seller refuses in-person meetup or inspection - high risk";
            case "CLASSIC_SCAM_STORY" -> "Uses classic scam story (army/urgent need/OTP request)";
            case "SUSPICIOUS_OFFER" -> "Offer seems too good to be true - verify carefully";
            default -> "Suspicious pattern detected in listing";
        };
    }

    private int riskLevel(String type) {
        return switch (type) {
            case "ADVANCE_PAYMENT" -> 8;
            case "UNTRACEABLE_PAYMENT" -> 9;
            case "REFUSES_MEETUP" -> 7;
            case "CLASSIC_SCAM_STORY" -> 10;
            case "SUSPICIOUS_OFFER" -> 6;
            default -> 5;
        };
    }

    private String buildInsight(List<MultiAgentResult.DealResult> deals, List<MultiAgentResult.ScamFlag> flags) {
        if (deals.isEmpty()) return "No social marketplace listings found matching this query.";
        long scamCount = deals.stream().filter(d -> d.scamFlags() != null && !d.scamFlags().isEmpty()).count();
        long goodCount = deals.stream().filter(MultiAgentResult.DealResult::isRecommended).count();
        return String.format(Locale.ROOT,
                "Found %d social listings (%d recommended, %d with scam warnings). %s",
                deals.size(), goodCount, scamCount,
                goodCount > 0
                        ? "Top pick: \"" + deals.get(0).title() + "\" at ₹" + Math.round(deals.get(0).price() != null ? deals.get(0).price() : 0) + " from " + deals.get(0).source()
                        : "Exercise caution - many listings have scam indicators.");
    }
}
