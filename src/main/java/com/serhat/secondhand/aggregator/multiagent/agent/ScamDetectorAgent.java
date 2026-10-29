package com.serhat.secondhand.aggregator.multiagent.agent;

import com.serhat.secondhand.aggregator.multiagent.domain.MultiAgentResult;
import com.serhat.secondhand.aggregator.multiagent.domain.ProductSearchRequest;
import com.serhat.secondhand.ai.application.GeminiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Agent 3 – ScamDetectorAgent
 *
 * Performs deep scam analysis using:
 *  1. Rule-based pattern matching (always active, offline)
 *  2. Gemini LLM analysis (active when API key is configured)
 *
 * Checks for:
 *  - Advance payment traps
 *  - Too-good-to-be-true pricing
 *  - Fake specifications (e.g., claiming 12GB RAM for ₹5000)
 *  - Suspicious seller behaviour
 *  - Platform-level fraud indicators
 */
@Component
public class ScamDetectorAgent implements SpecialistAgent {

    private static final Logger log = LoggerFactory.getLogger(ScamDetectorAgent.class);

    private final GeminiClient geminiClient;

    @Value("${gemini.api.key:}")
    private String apiKey;

    // ---- Pattern banks ----
    private static final List<Pattern> ADVANCE_PAYMENT = List.of(
            Pattern.compile("advance (payment|amount|money)|token (amount|money)|booking (fee|amount)|pay.{0,20}first", Pattern.CASE_INSENSITIVE),
            Pattern.compile("paytm me (pehle|first)|upi transfer (karo|kar)|send \\d+ first", Pattern.CASE_INSENSITIVE)
    );
    private static final List<Pattern> UNTRACEABLE_PAYMENT = List.of(
            Pattern.compile("bitcoin|ethereum|crypto|gift card|google play card|amazon card|western union|wire transfer", Pattern.CASE_INSENSITIVE)
    );
    private static final List<Pattern> MEETUP_REFUSAL = List.of(
            Pattern.compile("no (meetup|meet up|inspection|visit|calls?)|only (whatsapp|telegram|message)|courier only|delivery only|no (video|phone) call", Pattern.CASE_INSENSITIVE)
    );
    private static final List<Pattern> SCAM_STORY = List.of(
            Pattern.compile("\\b(army|navy|air force|soldier|deployed)\\b.{0,50}(sell|give|transfer)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(leaving|moving) (country|abroad|usa|uk|canada).{0,50}sell", Pattern.CASE_INSENSITIVE),
            Pattern.compile("send (me |your )?(otp|pin|password|cvv)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(won|winning|lottery|prize).{0,30}(claim|get|receive)", Pattern.CASE_INSENSITIVE)
    );

    // Spec implausibility: unrealistically cheap for high specs
    private static final List<SpecCheck> SPEC_CHECKS = List.of(
            new SpecCheck("iphone 15|iphone 14 pro|iphone 13 pro", 25000, "iPhone flagships below ₹25,000 are almost always fake/refurbished scams"),
            new SpecCheck("macbook pro|macbook air m[23]", 30000, "MacBook Pro/Air M-series below ₹30,000 - likely stolen/fake"),
            new SpecCheck("samsung galaxy s2[34]|samsung fold|samsung flip", 20000, "Samsung Galaxy S-series/Fold/Flip below ₹20,000 - extremely suspicious"),
            new SpecCheck("12gb.{0,10}ram|16gb.{0,10}ram", 4000, "High-RAM phone below ₹4,000 is technically impossible - fake specs"),
            new SpecCheck("snapdragon 8 gen [23]|dimensity 9[0-9]{3}", 8000, "Flagship chip below ₹8,000 - impossible, fake listing")
    );

    public ScamDetectorAgent(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    @Override
    public String agentName() {
        return "ScamDetectorAgent";
    }

    @Override
    public AgentOutput run(ProductSearchRequest request) {
        List<String> trace = new ArrayList<>();
        trace.add("[ScamDetectorAgent] Starting scam analysis for: " + request.productName());

        List<MultiAgentResult.ScamFlag> allFlags = new ArrayList<>();

        try {
            // Rule-based general scan on the query itself
            String query = (request.productName() != null ? request.productName() : "") + " " + (request.rawQuery() != null ? request.rawQuery() : "");
            allFlags.addAll(runRulesOnText(query, "Search Query", "User Query"));

            // Spec implausibility check
            if (request.maxBudget() != null) {
                for (SpecCheck check : SPEC_CHECKS) {
                    if (Pattern.compile(check.pattern(), Pattern.CASE_INSENSITIVE).matcher(query).find()
                            && request.maxBudget() < check.suspiciousBelowPrice()) {
                        allFlags.add(new MultiAgentResult.ScamFlag(
                                "Search: " + request.productName(),
                                "Budget Analysis",
                                "IMPLAUSIBLE_SPECS_FOR_BUDGET",
                                check.warning() + " (your budget: ₹" + Math.round(request.maxBudget()) + ")",
                                8
                        ));
                        trace.add("[ScamDetectorAgent] ⚠️ Spec implausibility detected: " + check.warning());
                    }
                }
            }

            // Gemini deep analysis (if key available)
            if (isGeminiAvailable()) {
                trace.add("[ScamDetectorAgent] Gemini available - running LLM scam analysis");
                try {
                    String geminiInsight = runGeminiScamAnalysis(request);
                    if (geminiInsight != null && !geminiInsight.isBlank()) {
                        allFlags.add(new MultiAgentResult.ScamFlag(
                                request.productName(), "Gemini AI Analysis", "AI_SCAM_ANALYSIS",
                                geminiInsight, 0 // informational, 0 = not a direct risk flag
                        ));
                        trace.add("[ScamDetectorAgent] Gemini analysis completed");
                    }
                } catch (Exception e) {
                    trace.add("[ScamDetectorAgent] Gemini analysis failed: " + e.getMessage() + " - rule-based only");
                }
            } else {
                trace.add("[ScamDetectorAgent] Gemini not configured - using rule-based analysis only");
            }

            trace.add("[ScamDetectorAgent] Total scam signals: " + allFlags.size());
            String insight = buildInsight(allFlags, request);

            return new AgentOutput(agentName(), "OK", List.of(), List.of(), allFlags, insight, trace);

        } catch (Exception e) {
            log.warn("[ScamDetectorAgent] Error: {}", e.getMessage());
            return AgentOutput.error(agentName(), "Scam detection error: " + e.getMessage());
        }
    }

    // ---- Helpers ----

    private List<MultiAgentResult.ScamFlag> runRulesOnText(String text, String listingTitle, String source) {
        List<MultiAgentResult.ScamFlag> flags = new ArrayList<>();
        for (Pattern p : ADVANCE_PAYMENT) {
            if (p.matcher(text).find()) {
                flags.add(new MultiAgentResult.ScamFlag(listingTitle, source, "ADVANCE_PAYMENT",
                        "Advance/token payment requested before meeting seller - strong scam indicator", 8));
                break;
            }
        }
        for (Pattern p : UNTRACEABLE_PAYMENT) {
            if (p.matcher(text).find()) {
                flags.add(new MultiAgentResult.ScamFlag(listingTitle, source, "UNTRACEABLE_PAYMENT",
                        "Untraceable payment method mentioned - very high fraud risk", 9));
                break;
            }
        }
        for (Pattern p : MEETUP_REFUSAL) {
            if (p.matcher(text).find()) {
                flags.add(new MultiAgentResult.ScamFlag(listingTitle, source, "REFUSES_MEETUP",
                        "Seller refuses in-person meetup/inspection - cannot verify product", 7));
                break;
            }
        }
        for (Pattern p : SCAM_STORY) {
            if (p.matcher(text).find()) {
                flags.add(new MultiAgentResult.ScamFlag(listingTitle, source, "CLASSIC_SCAM_STORY",
                        "Classic social engineering story detected (military/lottery/otp) - do not proceed", 10));
                break;
            }
        }
        return flags;
    }

    private String runGeminiScamAnalysis(ProductSearchRequest request) {
        String prompt = String.format("""
                You are a scam detection expert for Indian online marketplaces (WhatsApp groups, Facebook Marketplace).
                
                A user wants to buy: %s
                Budget: ₹%s to ₹%s
                
                Provide a BRIEF, actionable scam-safety advisory (2-3 sentences max) covering:
                1. Common scams for this product category in Indian secondhand markets
                2. Specific red flags to watch for when buying this type of product
                3. One key safety tip
                
                Keep it concise, practical and specific to this product. Do NOT make up specific prices.
                """,
                request.productName() != null ? request.productName() : request.rawQuery(),
                request.minBudget() != null ? Math.round(request.minBudget()) : "any",
                request.maxBudget() != null ? Math.round(request.maxBudget()) : "any"
        );

        return geminiClient.generateText(prompt);
    }

    private boolean isGeminiAvailable() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("your_") && !apiKey.startsWith("dummy");
    }

    private String buildInsight(List<MultiAgentResult.ScamFlag> flags, ProductSearchRequest req) {
        if (flags.isEmpty()) {
            return "✅ No immediate scam patterns detected for " + req.productName() + ". Always meet sellers in person and inspect before paying.";
        }
        long highRisk = flags.stream().filter(f -> f.riskLevel() >= 8).count();
        if (highRisk > 0) {
            return "🚨 HIGH RISK: " + highRisk + " critical scam indicator(s) detected! Do not make any advance payments. "
                    + "Common fraud type: " + flags.get(0).flagType() + ". " + flags.get(0).description();
        }
        return "⚠️ " + flags.size() + " scam warning(s) found. Proceed cautiously, always inspect before paying, "
                + "and prefer cash-on-meetup only. Primary concern: " + flags.get(0).description();
    }

    private record SpecCheck(String pattern, double suspiciousBelowPrice, String warning) {}
}
