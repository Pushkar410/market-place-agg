package com.serhat.secondhand.aggregator.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serhat.secondhand.aggregator.domain.Category;
import com.serhat.secondhand.aggregator.domain.SearchIntent;
import com.serhat.secondhand.aggregator.domain.SearchPlan;
import com.serhat.secondhand.aggregator.domain.SourceInfo;
import com.serhat.secondhand.aggregator.llm.LlmTextGenerator;
import com.serhat.secondhand.aggregator.source.SourceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent step "PLAN": decide which sources are worth querying for this request.
 * <ul>
 *   <li>With a Gemini key the LLM chooses, given the intent and what each source can serve.</li>
 *   <li>Otherwise (or if the call fails / returns nonsense) a deterministic rule skips sources whose
 *       declared category coverage cannot satisfy the requested category.</li>
 * </ul>
 * Sources with unknown coverage (the live database, community submissions) are never skipped.
 * Also decides when a request is too vague and the agent should ask a question instead of guessing.
 */
@Component
public class SearchPlanner {

    private static final Logger log = LoggerFactory.getLogger(SearchPlanner.class);

    private final LlmTextGenerator llm;
    private final ObjectMapper mapper;

    @Autowired
    public SearchPlanner(LlmTextGenerator llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    /** Deterministic planner without any LLM (tests and offline use). */
    public static SearchPlanner rulesOnly() {
        return new SearchPlanner(null, null);
    }

    public SearchPlan plan(SearchIntent intent, List<SourceAdapter> adapters) {
        SearchPlan rules = planByRules(intent, adapters);
        if (llm == null || mapper == null || !llm.isAvailable() || adapters.size() < 2) {
            return rules;
        }
        try {
            return planByLlm(intent, adapters, rules);
        } catch (Exception e) {
            log.warn("LLM source planning failed, using rules: {}", e.getMessage());
            return rules;
        }
    }

    /** A vague request makes the agent ask one question instead of searching blindly. */
    public static Optional<String> clarificationFor(SearchIntent intent) {
        if (intent.keywords().isEmpty() && intent.category() == null) {
            return Optional.of("I need a bit more detail: what are you looking for (for example a laptop, a PG room, a bicycle or a textbook)? "
                    + "Adding a budget and a city helps me rank better.");
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ rules

    SearchPlan planByRules(SearchIntent intent, List<SourceAdapter> adapters) {
        Category wanted = intent.category();
        List<String> selected = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (SourceAdapter a : adapters) {
            Set<Category> cov = a.coverage();
            boolean serves = wanted == null || cov.isEmpty() || cov.contains(wanted);
            (serves ? selected : skipped).add(a.info().id());
        }
        if (selected.isEmpty()) { // never plan an empty search
            selected.addAll(skipped);
            skipped.clear();
        }
        String rationale = skipped.isEmpty()
                ? "All " + selected.size() + " sources can serve this request"
                : "Querying " + selected.size() + " of " + adapters.size() + " sources; skipped "
                + names(adapters, skipped) + " because they have no " + wanted + " listings";
        return new SearchPlan(selected, skipped, rationale, "rules");
    }

    // ------------------------------------------------------------------ LLM

    private SearchPlan planByLlm(SearchIntent intent, List<SourceAdapter> adapters, SearchPlan fallback) throws Exception {
        String raw = llm.generate(prompt(intent, adapters));
        JsonNode n = mapper.readTree(stripFences(raw));
        Set<String> known = adapters.stream().map(a -> a.info().id()).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> chosen = new LinkedHashSet<>();
        if (n.hasNonNull("sources")) {
            n.get("sources").forEach(x -> { if (known.contains(x.asText())) chosen.add(x.asText()); });
        }
        if (chosen.isEmpty()) return fallback;
        // sources with unknown coverage (live DB, community) are never dropped by the model
        for (SourceAdapter a : adapters) if (a.coverage().isEmpty()) chosen.add(a.info().id());
        List<String> selected = adapters.stream().map(a -> a.info().id()).filter(chosen::contains).toList();
        List<String> skipped = adapters.stream().map(a -> a.info().id()).filter(id -> !chosen.contains(id)).toList();
        String reason = n.hasNonNull("reason") ? n.get("reason").asText().trim() : "";
        if (reason.isBlank()) reason = "Selected " + selected.size() + " of " + adapters.size() + " sources";
        if (reason.length() > 300) reason = reason.substring(0, 300);
        return new SearchPlan(selected, skipped, reason, "gemini");
    }

    private static String prompt(SearchIntent intent, List<SourceAdapter> adapters) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the planning step of a marketplace aggregator. Choose which sources are worth querying.\n");
        sb.append("Request: keywords=").append(intent.keywords()).append(", category=").append(intent.category())
                .append(", city=").append(intent.city()).append(", maxPrice=").append(intent.maxPrice()).append("\n");
        sb.append("Sources (id | name | type | categories served, empty = unknown):\n");
        for (SourceAdapter a : adapters) {
            SourceInfo i = a.info();
            sb.append("- ").append(i.id()).append(" | ").append(i.name()).append(" | ").append(i.type()).append(" | ").append(a.coverage()).append("\n");
        }
        sb.append("Reply with ONE JSON object only, no markdown: {\"sources\": [ids to query], \"reason\": \"one short sentence\"}. ");
        sb.append("Prefer recall: skip a source only when it clearly cannot help.");
        return sb.toString();
    }

    private static String stripFences(String s) {
        String t = s == null ? "" : s.trim();
        int a = t.indexOf('{');
        int b = t.lastIndexOf('}');
        return (a >= 0 && b > a) ? t.substring(a, b + 1) : t;
    }

    private static String names(List<SourceAdapter> adapters, List<String> ids) {
        return adapters.stream().filter(a -> ids.contains(a.info().id())).map(a -> a.info().name()).collect(Collectors.joining(", "));
    }
}
