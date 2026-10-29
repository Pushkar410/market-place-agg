package com.serhat.secondhand.aggregator.multiagent.langchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * LangChain Tool: scam_detector
 * Evaluates listings and descriptions for fraud indicators:
 *  - Advance payment / token demands
 *  - Refusal to meet in person (courier-only)
 *  - Unrealistically cheap prices
 *  - Classic impersonation / OTP scam scripts
 */
@Component
public class ScamDetectorTool implements LangChainTool {

    private final ObjectMapper mapper;

    private static final List<Pattern> ADVANCE_PAYMENT = List.of(
            Pattern.compile("advance|token (amount|money)|booking amount|pay.{0,15}first|send.{0,10}upi", Pattern.CASE_INSENSITIVE)
    );
    private static final List<Pattern> NO_MEETUP = List.of(
            Pattern.compile("no (meetup|meet up|visit|inspection)|courier (only|delivery)|delivery only|only whatsapp", Pattern.CASE_INSENSITIVE)
    );
    private static final List<Pattern> SOCIAL_ENGINEERING = List.of(
            Pattern.compile("\\barmy\\b|air force|soldier|deployed|lottery|send.{0,10}otp", Pattern.CASE_INSENSITIVE)
    );

    public ScamDetectorTool(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "scam_detector";
    }

    @Override
    public String description() {
        return "Analyzes listing title, description, and price for fraud indicators. "
                + "Detects advance payment traps, meetup refusals, unrealistic prices, and social engineering stories. "
                + "Input parameters: 'text' (string, required), 'price' (number, optional), 'expectedPrice' (number, optional).";
    }

    @Override
    public String execute(Map<String, Object> input) {
        String text = String.valueOf(input.getOrDefault("text", ""));
        Double price = parseDouble(input.get("price"));
        Double expectedPrice = parseDouble(input.get("expectedPrice"));

        List<Map<String, Object>> flags = new ArrayList<>();

        for (Pattern p : ADVANCE_PAYMENT) {
            if (p.matcher(text).find()) {
                flags.add(Map.of(
                        "flag", "ADVANCE_PAYMENT_TRAP",
                        "severity", "HIGH",
                        "riskScore", 9,
                        "warning", "Seller demands advance token payment before handover. 95% of online marketplace fraud starts this way."
                ));
                break;
            }
        }

        for (Pattern p : NO_MEETUP) {
            if (p.matcher(text).find()) {
                flags.add(Map.of(
                        "flag", "REFUSES_IN_PERSON_MEETUP",
                        "severity", "HIGH",
                        "riskScore", 8,
                        "warning", "Seller refuses physical inspection or face-to-face transaction. Never buy secondhand without in-person verification."
                ));
                break;
            }
        }

        for (Pattern p : SOCIAL_ENGINEERING) {
            if (p.matcher(text).find()) {
                flags.add(Map.of(
                        "flag", "SUSPICIOUS_IDENTITY_STORY",
                        "severity", "CRITICAL",
                        "riskScore", 10,
                        "warning", "Uses classic military/emergency transfer script often abused by scam networks in India."
                ));
                break;
            }
        }

        if (price != null && expectedPrice != null && expectedPrice > 0 && price < (expectedPrice * 0.35)) {
            flags.add(Map.of(
                    "flag", "TOO_GOOD_TO_BE_TRUE",
                    "severity", "HIGH",
                    "riskScore", 8,
                    "warning", "Price ₹" + Math.round(price) + " is suspiciously low compared to typical budget/market value (₹" + Math.round(expectedPrice) + ")."
            ));
        }

        boolean hasHighRisk = flags.stream().anyMatch(f -> "HIGH".equals(f.get("severity")) || "CRITICAL".equals(f.get("severity")));

        try {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("status", "SUCCESS");
            res.put("scamDetected", !flags.isEmpty());
            res.put("highRiskWarning", hasHighRisk);
            res.put("flagsCount", flags.size());
            res.put("flags", flags);
            res.put("safetyAdvice", hasHighRisk
                    ? "DO NOT transfer money upfront. Always meet in a public location and test the product thoroughly before payment."
                    : "No critical fraud patterns found. Standard precaution: inspect before paying.");
            return mapper.writeValueAsString(res);
        } catch (Exception e) {
            return "{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}";
        }
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
