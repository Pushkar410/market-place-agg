package com.serhat.secondhand.aggregator.multiagent;

import com.serhat.secondhand.aggregator.multiagent.agent.*;
import com.serhat.secondhand.aggregator.multiagent.domain.MultiAgentResult;
import com.serhat.secondhand.aggregator.multiagent.domain.ProductSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Multi-Agent Orchestrator
 *
 * Coordinates all specialist agents in parallel:
 *  1. PopularProductsAgent  – best products from internet
 *  2. WhatsAppFacebookAgent – WhatsApp groups + Facebook Marketplace deals
 *  3. ScamDetectorAgent     – scam analysis
 *  4. BuyAdvisorAgent       – final buy/consider/avoid verdict (runs after all others)
 *
 * OLX is always excluded (no OLX source bean included).
 * Supports both ONLINE and OFFLINE modes.
 */
@Service
public class MultiAgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentOrchestrator.class);

    private final PopularProductsAgent popularProductsAgent;
    private final WhatsAppFacebookAgent whatsAppFacebookAgent;
    private final ScamDetectorAgent scamDetectorAgent;
    private final BuyAdvisorAgent buyAdvisorAgent;
    private final ExecutorService executor;

    public MultiAgentOrchestrator(PopularProductsAgent popularProductsAgent,
                                  WhatsAppFacebookAgent whatsAppFacebookAgent,
                                  ScamDetectorAgent scamDetectorAgent,
                                  BuyAdvisorAgent buyAdvisorAgent) {
        this.popularProductsAgent = popularProductsAgent;
        this.whatsAppFacebookAgent = whatsAppFacebookAgent;
        this.scamDetectorAgent = scamDetectorAgent;
        this.buyAdvisorAgent = buyAdvisorAgent;
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "multi-agent-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @jakarta.annotation.PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public MultiAgentResult orchestrate(ProductSearchRequest rawRequest) {
        long t0 = System.currentTimeMillis();
        ProductSearchRequest request = rawRequest.withDefaults();

        List<String> trace = new ArrayList<>();
        trace.add("=== Multi-Agent System Started ===");
        trace.add("Query: " + request.rawQuery() + " | Product: " + request.productName());
        trace.add("Mode: " + (request.onlineSearch() ? "ONLINE" : "OFFLINE")
                + " | Social: " + request.includeSocialSources()
                + " | OLX: EXCLUDED (by design)");
        if (request.maxBudget() != null)
            trace.add("Budget: ₹" + request.minBudget() + " - ₹" + request.maxBudget());
        if (request.minBatteryMah() != null)
            trace.add("Battery requirement: " + request.minBatteryMah() + "mAh minimum");

        // ---- Phase 1: Run all agents in parallel ----
        Map<String, Future<SpecialistAgent.AgentOutput>> futures = new LinkedHashMap<>();
        futures.put("popular",    executor.submit(() -> popularProductsAgent.run(request)));
        futures.put("social",     executor.submit(() -> whatsAppFacebookAgent.run(request)));
        futures.put("scam",       executor.submit(() -> scamDetectorAgent.run(request)));

        // Collect results
        List<MultiAgentResult.ProductRanking> popularPicks = new ArrayList<>();
        List<MultiAgentResult.DealResult> socialListings = new ArrayList<>();
        List<MultiAgentResult.ScamFlag> scamFlags = new ArrayList<>();
        Map<String, String> agentStatuses = new LinkedHashMap<>();

        for (Map.Entry<String, Future<SpecialistAgent.AgentOutput>> entry : futures.entrySet()) {
            try {
                SpecialistAgent.AgentOutput output = entry.getValue().get(8, TimeUnit.SECONDS);
                agentStatuses.put(output.agentName(), output.status());
                trace.addAll(output.trace());

                popularPicks.addAll(output.popularPicks());
                socialListings.addAll(output.deals());
                scamFlags.addAll(output.scamFlags());

            } catch (TimeoutException te) {
                String name = entry.getKey();
                agentStatuses.put(name, "TIMEOUT");
                trace.add("[Orchestrator] Agent '" + name + "' timed out after 8s");
            } catch (Exception e) {
                String name = entry.getKey();
                agentStatuses.put(name, "ERROR");
                trace.add("[Orchestrator] Agent '" + name + "' failed: " + e.getMessage());
            }
        }

        trace.add("=== Phase 1 Complete: " + popularPicks.size() + " popular picks, "
                + socialListings.size() + " social deals, " + scamFlags.size() + " scam flags ===");

        // ---- Phase 2: BuyAdvisorAgent synthesizes everything ----
        trace.add("=== Phase 2: BuyAdvisorAgent synthesizing ===");
        BuyAdvisorAgent.Advisory advisory = buyAdvisorAgent.advise(
                request, popularPicks, socialListings, scamFlags);
        agentStatuses.put("BuyAdvisorAgent", "OK");
        trace.addAll(advisory.trace());

        // ---- Merge social + scam-flagged deals for final presentation ----
        // Apply scam labels to social listings
        List<MultiAgentResult.DealResult> finalDeals = mergeDealScamFlags(socialListings, scamFlags);

        // ---- Top deals: best from popular picks converted to DealResult ----
        List<MultiAgentResult.DealResult> topDeals = convertPopularToDeals(popularPicks, scamFlags, request);

        trace.add("=== Multi-Agent System Complete (" + (System.currentTimeMillis() - t0) + "ms) ===");

        return new MultiAgentResult(
                request.rawQuery(),
                request.productName(),
                advisory.verdict(),
                advisory.narrative(),
                advisory.confidence(),
                topDeals,
                popularPicks,
                finalDeals,
                scamFlags,
                scamFlags.stream().anyMatch(f -> f.riskLevel() >= 7),
                advisory.battery(),
                advisory.budget(),
                trace,
                agentStatuses,
                System.currentTimeMillis() - t0
        );
    }

    private List<MultiAgentResult.DealResult> mergeDealScamFlags(
            List<MultiAgentResult.DealResult> deals, List<MultiAgentResult.ScamFlag> allScamFlags) {

        return deals.stream().map(deal -> {
            List<String> flagTexts = allScamFlags.stream()
                    .filter(f -> deal.title() != null && f.listingTitle() != null &&
                            deal.title().equalsIgnoreCase(f.listingTitle()))
                    .map(MultiAgentResult.ScamFlag::description)
                    .toList();
            if (!flagTexts.isEmpty() && (deal.scamFlags() == null || deal.scamFlags().isEmpty())) {
                return new MultiAgentResult.DealResult(
                        deal.rank(), deal.title(), deal.source(), deal.price(), deal.currency(),
                        deal.city(), deal.condition(), deal.url(), deal.imageUrls(),
                        deal.trustScore(), deal.trustLevel(), deal.trustReasons(),
                        flagTexts, deal.isRecommended(), "⚠️ VERIFY CAREFULLY");
            }
            return deal;
        }).toList();
    }

    private List<MultiAgentResult.DealResult> convertPopularToDeals(
            List<MultiAgentResult.ProductRanking> popular,
            List<MultiAgentResult.ScamFlag> scamFlags, ProductSearchRequest req) {

        long highRiskCount = scamFlags.stream().filter(f -> f.riskLevel() >= 8).count();

        List<MultiAgentResult.DealResult> result = new ArrayList<>();
        int rank = 1;
        for (MultiAgentResult.ProductRanking pick : popular) {
            if (rank > req.maxResults()) break;
            int trustScore = (int) Math.round(Math.min(95, 50 + (pick.rating() != null ? (pick.rating() - 3.0) * 20 : 0)
                    + Math.min(25, Math.log1p(pick.reviewCount()) * 3)));
            String badge = resolveProductBadge(pick, req, highRiskCount);

            result.add(new MultiAgentResult.DealResult(
                    rank++,
                    pick.productName() + " (" + pick.brand() + ")",
                    pick.source(),
                    pick.price(),
                    pick.currency() != null ? pick.currency() : "INR",
                    req.city(),
                    "NEW",
                    pick.url(),
                    pick.imageUrls(),
                    trustScore,
                    trustScore >= 70 ? "HIGH" : "MEDIUM",
                    buildProductReasons(pick),
                    List.of(),
                    trustScore >= 65 && highRiskCount == 0,
                    badge
            ));
        }
        return result;
    }

    private String resolveProductBadge(MultiAgentResult.ProductRanking pick, ProductSearchRequest req, long highRiskCount) {
        if (pick.isPopularChoice()) return "🔥 POPULAR CHOICE";
        if (req.maxBudget() != null && pick.price() != null && pick.price() <= req.maxBudget() * 0.9) return "💰 IN BUDGET";
        if (pick.rating() != null && pick.rating() >= 4.5) return "⭐ TOP RATED";
        return "📦 GOOD OPTION";
    }

    private List<String> buildProductReasons(MultiAgentResult.ProductRanking pick) {
        List<String> r = new ArrayList<>();
        if (pick.rating() != null) r.add(String.format(Locale.ROOT, "Rated %.1f★ from %d reviews", pick.rating(), pick.reviewCount()));
        if (pick.batteryInfo() != null && !pick.batteryInfo().equals("N/A")) r.add("Battery: " + pick.batteryInfo());
        if (pick.highlights() != null && !pick.highlights().isBlank()) {
            String h = pick.highlights();
            r.add(h.length() > 100 ? h.substring(0, 100) + "..." : h);
        }
        return r;
    }
}
