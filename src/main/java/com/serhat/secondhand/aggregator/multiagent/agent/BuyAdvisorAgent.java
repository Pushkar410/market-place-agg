package com.serhat.secondhand.aggregator.multiagent.agent;

import com.serhat.secondhand.aggregator.multiagent.domain.MultiAgentResult;
import com.serhat.secondhand.aggregator.multiagent.domain.ProductSearchRequest;
import com.serhat.secondhand.ai.application.GeminiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent 4 – BuyAdvisorAgent
 *
 * The "should I buy this?" reasoning agent.
 * Synthesizes findings from all other agents to produce:
 *  - An overall verdict: BUY / CONSIDER / AVOID
 *  - Confidence score (0-100)
 *  - Budget analysis
 *  - Battery analysis (if relevant)
 *  - A plain-English recommendation paragraph
 *
 * Uses Gemini to generate the final advisory when available; falls back to
 * deterministic rule-based reasoning when offline.
 */
@Component
public class BuyAdvisorAgent implements SpecialistAgent {

    private static final Logger log = LoggerFactory.getLogger(BuyAdvisorAgent.class);

    private final GeminiClient geminiClient;

    @Value("${gemini.api.key:}")
    private String apiKey;

    public BuyAdvisorAgent(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    @Override
    public String agentName() {
        return "BuyAdvisorAgent";
    }

    /** This agent doesn't run standalone - it runs at the end of orchestration. */
    @Override
    public AgentOutput run(ProductSearchRequest request) {
        return AgentOutput.ok(agentName(), "BuyAdvisor is invoked by orchestrator, not standalone.", List.of());
    }

    /**
     * Full advisory synthesis - called by the orchestrator after all agents have completed.
     */
    public Advisory advise(ProductSearchRequest request,
                           List<MultiAgentResult.ProductRanking> popularPicks,
                           List<MultiAgentResult.DealResult> allDeals,
                           List<MultiAgentResult.ScamFlag> scamFlags) {

        List<String> trace = new ArrayList<>();
        trace.add("[BuyAdvisorAgent] Synthesizing results from all agents");

        // ---- Budget Analysis ----
        MultiAgentResult.BudgetAnalysis budget = analyzeBudget(request, popularPicks, allDeals, trace);

        // ---- Battery Analysis ----
        MultiAgentResult.BatteryAnalysis battery = analyzeBattery(request, popularPicks, trace);

        // ---- Verdict Scoring ----
        int score = 50; // baseline
        List<String> verdictReasons = new ArrayList<>();

        // Good deals available?
        long goodDeals = allDeals.stream().filter(MultiAgentResult.DealResult::isRecommended).count();
        if (goodDeals > 0) { score += 15; verdictReasons.add(goodDeals + " recommended deal(s) found"); }
        else if (!allDeals.isEmpty()) { score -= 5; verdictReasons.add("No deals meet all trust criteria"); }
        else { score -= 15; verdictReasons.add("No deals found - market may be limited"); }

        // Popular products available in budget?
        if (budget.budgetSufficient()) { score += 10; verdictReasons.add("Budget sufficient for quality products"); }
        else { score -= 10; verdictReasons.add("Budget may be tight for this product category"); }

        // Scam risk?
        long highRiskScams = scamFlags.stream().filter(f -> f.riskLevel() >= 8).count();
        long mediumScams = scamFlags.stream().filter(f -> f.riskLevel() >= 6 && f.riskLevel() < 8).count();
        if (highRiskScams > 0) { score -= 25; verdictReasons.add(highRiskScams + " HIGH RISK scam signal(s) detected - extreme caution"); }
        else if (mediumScams > 0) { score -= 10; verdictReasons.add(mediumScams + " moderate scam warning(s) - verify carefully"); }
        else { score += 10; verdictReasons.add("No critical scam patterns detected"); }

        // Popular product available?
        if (!popularPicks.isEmpty()) { score += 5; verdictReasons.add("Popular products available in this category"); }

        // Battery important and satisfied?
        if (request.minBatteryMah() != null && request.minBatteryMah() > 0) {
            if (battery.productsWithGoodBattery().size() > 0) {
                score += 5;
                verdictReasons.add("Products with " + request.minBatteryMah() + "+ mAh battery found");
            } else {
                score -= 5;
                verdictReasons.add("Few products with your minimum battery requirement found");
            }
        }

        score = Math.max(0, Math.min(100, score));
        String verdict = score >= 70 ? "BUY" : score >= 45 ? "CONSIDER" : "AVOID";

        trace.add("[BuyAdvisorAgent] Verdict: " + verdict + " (confidence: " + score + ")");

        // ---- Final narrative ----
        String narrative;
        if (isGeminiAvailable()) {
            trace.add("[BuyAdvisorAgent] Generating Gemini advisory narrative");
            try {
                narrative = generateGeminiAdvisory(request, verdict, score, verdictReasons,
                        popularPicks, allDeals, scamFlags, budget, battery);
                trace.add("[BuyAdvisorAgent] Gemini advisory generated successfully");
            } catch (Exception e) {
                trace.add("[BuyAdvisorAgent] Gemini failed (" + e.getMessage() + "), using rule-based narrative");
                narrative = buildRuleBasedNarrative(request, verdict, score, verdictReasons, budget, battery, scamFlags, goodDeals);
            }
        } else {
            trace.add("[BuyAdvisorAgent] Gemini not configured - using rule-based narrative");
            narrative = buildRuleBasedNarrative(request, verdict, score, verdictReasons, budget, battery, scamFlags, goodDeals);
        }

        return new Advisory(verdict, score, narrative, budget, battery, verdictReasons, trace);
    }

    // ---- Budget Analysis ----

    private MultiAgentResult.BudgetAnalysis analyzeBudget(ProductSearchRequest req,
            List<MultiAgentResult.ProductRanking> popular, List<MultiAgentResult.DealResult> deals, List<String> trace) {

        // Compute average market price from popular picks
        OptionalDouble avgOpt = popular.stream()
                .filter(r -> r.price() != null)
                .mapToDouble(MultiAgentResult.ProductRanking::price)
                .average();

        Double avgMarket = avgOpt.isPresent() ? Math.round(avgOpt.getAsDouble() * 10) / 10.0 : null;

        // Best deal price
        OptionalDouble bestOpt = deals.stream()
                .filter(MultiAgentResult.DealResult::isRecommended)
                .filter(d -> d.price() != null)
                .mapToDouble(MultiAgentResult.DealResult::price)
                .min();
        Double bestDeal = bestOpt.isPresent() ? bestOpt.getAsDouble() : null;

        boolean sufficient = true;
        String budgetAdvice;
        if (req.maxBudget() != null && avgMarket != null) {
            if (req.maxBudget() >= avgMarket) {
                budgetAdvice = "Your budget of ₹" + Math.round(req.maxBudget()) + " is comfortable for this product category (avg market: ₹" + Math.round(avgMarket) + ").";
            } else if (req.maxBudget() >= avgMarket * 0.75) {
                budgetAdvice = "Budget is slightly below average (₹" + Math.round(avgMarket) + ") but good secondhand deals exist. Stretch by ₹" + Math.round(avgMarket - req.maxBudget()) + " for new.";
                sufficient = false;
            } else {
                budgetAdvice = "Budget of ₹" + Math.round(req.maxBudget()) + " is significantly below market average of ₹" + Math.round(avgMarket) + ". Consider increasing budget or choosing a lower-spec model.";
                sufficient = false;
            }
        } else if (req.maxBudget() != null) {
            budgetAdvice = "Budget: ₹" + Math.round(req.maxBudget()) + ". Market data limited - check listed deals for price reference.";
        } else {
            budgetAdvice = "No budget specified. Products range from ₹" + (avgMarket != null ? Math.round(avgMarket * 0.5) : "N/A") + " to ₹" + (avgMarket != null ? Math.round(avgMarket * 1.5) : "N/A") + " typically.";
        }

        trace.add("[BuyAdvisorAgent] Budget analysis: avg market ₹" + (avgMarket != null ? Math.round(avgMarket) : "unknown") + ", budget sufficient: " + sufficient);

        return new MultiAgentResult.BudgetAnalysis(req.minBudget(), req.maxBudget(), avgMarket, bestDeal, budgetAdvice, sufficient);
    }

    // ---- Battery Analysis ----

    private MultiAgentResult.BatteryAnalysis analyzeBattery(ProductSearchRequest req,
            List<MultiAgentResult.ProductRanking> popular, List<String> trace) {

        boolean batteryImportant = req.minBatteryMah() != null && req.minBatteryMah() > 0;

        if (!batteryImportant) {
            return new MultiAgentResult.BatteryAnalysis(false, null, List.of(),
                    "Battery was not specified as a priority filter.");
        }

        List<String> goodBatteryProducts = popular.stream()
                .filter(r -> r.batteryInfo() != null && !r.batteryInfo().equals("N/A"))
                .filter(r -> parseBatteryMah(r.batteryInfo()) >= req.minBatteryMah())
                .map(r -> r.productName() + " (" + r.batteryInfo() + ")")
                .toList();

        String advice;
        if (goodBatteryProducts.isEmpty()) {
            advice = "No popular products found with " + req.minBatteryMah() + "+ mAh battery in this price range. "
                    + "Consider Samsung Galaxy M-series or Redmi Note series for large batteries.";
        } else {
            advice = goodBatteryProducts.size() + " product(s) meet your " + req.minBatteryMah() + "mAh requirement. "
                    + "Top pick: " + goodBatteryProducts.get(0);
        }

        trace.add("[BuyAdvisorAgent] Battery analysis: " + goodBatteryProducts.size() + " products with " + req.minBatteryMah() + "+ mAh");

        return new MultiAgentResult.BatteryAnalysis(true, req.minBatteryMah(), goodBatteryProducts, advice);
    }

    private int parseBatteryMah(String batteryInfo) {
        if (batteryInfo == null || batteryInfo.isBlank()) return 0;
        try {
            String digits = batteryInfo.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 0;
            return Integer.parseInt(digits.length() > 5 ? digits.substring(0, 5) : digits);
        } catch (Exception e) { return 0; }
    }

    // ---- Narrative generation ----

    private String generateGeminiAdvisory(ProductSearchRequest request, String verdict, int confidence,
            List<String> verdictReasons, List<MultiAgentResult.ProductRanking> popular,
            List<MultiAgentResult.DealResult> deals, List<MultiAgentResult.ScamFlag> scams,
            MultiAgentResult.BudgetAnalysis budget, MultiAgentResult.BatteryAnalysis battery) {

        String topProduct = popular.isEmpty() ? "N/A" : popular.get(0).productName() + " by " + popular.get(0).brand();
        String bestDeal = deals.stream().filter(MultiAgentResult.DealResult::isRecommended).findFirst()
                .map(d -> d.title() + " at ₹" + Math.round(d.price() != null ? d.price() : 0) + " from " + d.source())
                .orElse("no verified deal found");
        long highRiskScams = scams.stream().filter(s -> s.riskLevel() >= 8).count();

        String prompt = String.format("""
                You are a trusted buying advisor for second-hand products in India. Give a practical, friendly, and direct recommendation.
                
                User wants to buy: %s
                Budget: ₹%s
                Battery requirement: %s
                
                Analysis results:
                - Verdict: %s (confidence: %d/100)
                - Top popular product: %s
                - Best community deal: %s
                - High-risk scam signals: %d
                - Budget assessment: %s
                - Battery: %s
                
                Write a 2-3 paragraph buying recommendation:
                1. Direct verdict and why (be specific, not generic)
                2. What to look for / what to avoid (product-specific)
                3. Specific action: where to buy, what to check, safety tips for India
                
                Be direct, friendly, use simple language. Mention specific product names if helpful.
                Do NOT repeat numbers already stated. Add actionable advice.
                """,
                request.productName() != null ? request.productName() : request.rawQuery(),
                request.maxBudget() != null ? Math.round(request.maxBudget()) : "flexible",
                request.minBatteryMah() != null ? request.minBatteryMah() + "mAh minimum" : "not specified",
                verdict, confidence,
                topProduct, bestDeal, highRiskScams,
                budget.budgetAdvice(), battery.batteryAdvice()
        );

        return geminiClient.generateText(prompt);
    }

    private String buildRuleBasedNarrative(ProductSearchRequest req, String verdict, int confidence,
            List<String> verdictReasons, MultiAgentResult.BudgetAnalysis budget,
            MultiAgentResult.BatteryAnalysis battery, List<MultiAgentResult.ScamFlag> scams, long goodDeals) {

        StringBuilder sb = new StringBuilder();

        // Para 1: verdict
        sb.append(switch (verdict) {
            case "BUY" -> "✅ **Go for it!** Our analysis gives a strong green light for buying " +
                    (req.productName() != null ? req.productName() : "this product") + " (confidence: " + confidence + "/100). ";
            case "CONSIDER" -> "🤔 **Proceed with caution.** Buying " +
                    (req.productName() != null ? req.productName() : "this product") + " is viable but needs careful verification (confidence: " + confidence + "/100). ";
            default -> "❌ **We advise against this purchase right now** for " +
                    (req.productName() != null ? req.productName() : "this product") + " (confidence: " + confidence + "/100). ";
        });
        sb.append(String.join(". ", verdictReasons.subList(0, Math.min(3, verdictReasons.size())))).append(".\n\n");

        // Para 2: budget/battery
        sb.append("💰 **Budget:** ").append(budget.budgetAdvice()).append(" ");
        if (battery.batteryImportant()) {
            sb.append("🔋 **Battery:** ").append(battery.batteryAdvice());
        }
        sb.append("\n\n");

        // Para 3: scam/safety tips
        long highRisk = scams.stream().filter(s -> s.riskLevel() >= 8).count();
        if (highRisk > 0) {
            sb.append("🚨 **Scam Alert:** ").append(highRisk).append(" high-risk scam pattern(s) detected. ")
              .append("Never pay advance token money. Always meet in a public place, inspect the product fully before payment. ")
              .append("Prefer cash-on-delivery or Google Pay only AFTER inspection. ");
        } else if (!scams.isEmpty()) {
            sb.append("⚠️ **Safety Tips:** Some caution flags raised. Always inspect before paying. ");
        } else {
            sb.append("✅ **Safety:** No immediate scam patterns detected. ");
        }
        sb.append("For secondhand purchases: check IMEI (for mobiles), insist on original box/bill, ");
        sb.append("and prefer meetup in a safe public place like a café or mall food court.");

        return sb.toString();
    }

    private boolean isGeminiAvailable() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("your_") && !apiKey.startsWith("dummy");
    }

    /** Structured advisory output from this agent */
    public record Advisory(
            String verdict,
            int confidence,
            String narrative,
            MultiAgentResult.BudgetAnalysis budget,
            MultiAgentResult.BatteryAnalysis battery,
            List<String> verdictReasons,
            List<String> trace
    ) {}
}
