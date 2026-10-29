package com.serhat.secondhand.aggregator.explain;

import com.serhat.secondhand.aggregator.domain.NormalizedListing;
import com.serhat.secondhand.aggregator.domain.RankedListing;
import com.serhat.secondhand.aggregator.domain.SearchIntent;
import com.serhat.secondhand.aggregator.llm.LlmTextGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Agent step "EXPLAIN": an LLM writes a short comparison of the top results.
 * The model only sees values the pipeline already computed (price, trust score, flags, reasons) and is told
 * not to add facts, so the trust numbers stay rule-based and auditable while the wording is natural.
 * Without a key, or on any failure, nothing is returned and the deterministic summary is used.
 */
@Component
public class ResultExplainer {

    private static final Logger log = LoggerFactory.getLogger(ResultExplainer.class);
    private static final int MAX_CHARS = 800;

    private final LlmTextGenerator llm;

    @Autowired
    public ResultExplainer(LlmTextGenerator llm) {
        this.llm = llm;
    }

    /** No LLM (tests and offline use). */
    public static ResultExplainer disabled() {
        return new ResultExplainer(null);
    }

    public Optional<String> explain(String query, SearchIntent intent, List<RankedListing> top) {
        if (llm == null || !llm.isAvailable() || top == null || top.isEmpty()) {
            return Optional.empty();
        }
        try {
            String text = llm.generate(prompt(query, top.subList(0, Math.min(3, top.size()))));
            if (text == null) return Optional.empty();
            String clean = text.replace("**", "").replace("`", "").trim();
            if (clean.isBlank()) return Optional.empty();
            return Optional.of(clean.length() > MAX_CHARS ? clean.substring(0, MAX_CHARS).trim() + "…" : clean);
        } catch (Exception e) {
            log.warn("LLM explanation failed, using the built-in summary: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static String prompt(String query, List<RankedListing> top) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a careful shopping assistant. The user asked: \"").append(query.replace("\"", "'")).append("\"\n");
        sb.append("Below are the top results ALREADY ranked and trust-scored by the system. ");
        sb.append("Write 3 to 4 plain sentences (no markdown, no bullet points): recommend the best one and why, ");
        sb.append("compare it with the others on price and trust, and warn about any listed risk flags. ");
        sb.append("Use ONLY the facts below; do not invent specs, prices, sellers or guarantees.\n\n");
        for (RankedListing r : top) {
            NormalizedListing l = r.listing();
            sb.append("#").append(r.rank()).append(" ").append(l.title()).append("\n");
            sb.append("  price: ").append(l.price() == null ? "not stated" : l.currency() + " " + Math.round(l.price()));
            sb.append(" | city: ").append(l.city() == null ? "unknown" : l.city());
            sb.append(" | source: ").append(l.sourceName()).append("\n");
            sb.append(String.format(Locale.ROOT, "  trust: %d/100 (%s)%n", r.trust().score(), r.trust().level()));
            if (!r.trust().riskFlags().isEmpty()) sb.append("  risk flags: ").append(String.join("; ", r.trust().riskFlags())).append("\n");
            if (!r.reasons().isEmpty()) sb.append("  positives: ").append(String.join("; ", r.reasons())).append("\n");
        }
        return sb.toString();
    }
}
