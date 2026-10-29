package com.serhat.secondhand.aggregator.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serhat.secondhand.aggregator.domain.Category;
import com.serhat.secondhand.aggregator.domain.SearchIntent;
import com.serhat.secondhand.aggregator.normalize.Vocabulary;
import com.serhat.secondhand.ai.application.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * LLM-backed "Understand Query" step (Agentic AI). Uses the existing Gemini client.
 * Only active when a real GEMINI_API_KEY is configured; any failure falls back to the
 * rule-based parser, so the pipeline never depends on the LLM being reachable.
 */
@Component
@Slf4j
public class GeminiIntentParser {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${aggregator.llm.enabled:true}")
    private boolean enabled;

    public GeminiIntentParser(GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("your_") && !apiKey.startsWith("dummy");
    }

    public Optional<SearchIntent> parse(String query) {
        if (!isAvailable()) return Optional.empty();
        try {
            String raw = geminiClient.generateText(prompt(query));
            JsonNode n = objectMapper.readTree(stripFences(raw));

            List<String> keywords = new ArrayList<>();
            if (n.hasNonNull("keywords")) n.get("keywords").forEach(k -> keywords.add(k.asText().toLowerCase(Locale.ROOT)));
            if (keywords.isEmpty()) return Optional.empty();

            Category category = n.hasNonNull("category") ? Category.parse(n.get("category").asText()) : null;
            Double min = n.hasNonNull("minPrice") ? n.get("minPrice").asDouble() : null;
            Double max = n.hasNonNull("maxPrice") ? n.get("maxPrice").asDouble() : null;
            String city = n.hasNonNull("city") ? Vocabulary.canonicalCity(n.get("city").asText()) : null;
            String condition = n.hasNonNull("condition") ? n.get("condition").asText().toUpperCase(Locale.ROOT) : null;
            SearchIntent.Preference pref = SearchIntent.Preference.BALANCED;
            if (n.hasNonNull("preference")) {
                try {
                    pref = SearchIntent.Preference.valueOf(n.get("preference").asText().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) { }
            }
            return Optional.of(new SearchIntent(query, keywords, category, min, max, city, condition, pref, "gemini"));
        } catch (Exception e) {
            log.warn("Gemini intent parsing failed, falling back to rules: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String stripFences(String s) {
        String t = s == null ? "" : s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?", "");
            int end = t.lastIndexOf("```");
            if (end >= 0) t = t.substring(0, end);
        }
        int a = t.indexOf('{');
        int b = t.lastIndexOf('}');
        return (a >= 0 && b > a) ? t.substring(a, b + 1) : t;
    }

    private static String prompt(String query) {
        return """
                You are the query-understanding step of a marketplace aggregator for students in India.
                Convert the user's request into ONE JSON object (no markdown, no commentary) with exactly these keys:
                keywords: array of 1-6 lowercase product/service words (no budget words, no city names)
                category: one of ELECTRONICS, BOOKS, FURNITURE, VEHICLES, HOUSING, SPORTS, CLOTHING, SERVICES, OTHER, or null
                minPrice: number in INR or null
                maxPrice: number in INR or null ("20k" = 20000, "1 lakh" = 100000)
                city: city name or null
                condition: one of NEW, LIKE_NEW, GOOD, FAIR or null
                preference: one of BALANCED, TRUSTED, CHEAPEST, NEARBY
                User request: %s
                """.formatted(query.replace("\"", "'"));
    }
}
