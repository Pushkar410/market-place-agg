package com.serhat.secondhand.aggregator.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serhat.secondhand.aggregator.domain.Category;
import com.serhat.secondhand.aggregator.domain.RawListing;
import com.serhat.secondhand.aggregator.domain.SearchIntent;
import com.serhat.secondhand.aggregator.domain.SourceInfo;
import com.serhat.secondhand.aggregator.normalize.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

/**
 * Adapter over a bundled JSON dataset that stands in for an external source
 * (OLX, Facebook Marketplace, WhatsApp community groups, local niche boards).
 *
 * Phase 1 uses simulated, clearly-labelled data because scraping those platforms is not
 * permitted. In production each instance is replaced by a real adapter that talks to an
 * official API / partner feed / user-shared export (see README "Adding a real source").
 */
public class SimulatedSourceAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSourceAdapter.class);

    private final SourceInfo info;
    private final List<RawListing> all = new ArrayList<>();

    public SimulatedSourceAdapter(String resourcePath, ObjectMapper mapper) {
        SourceInfo parsed = new SourceInfo("online-source", "Internet Live Source", "WEB_FEED", false, 0.7);
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                JsonNode root = mapper.readTree(in);
                JsonNode s = root.get("source");
                if (s != null) {
                    parsed = new SourceInfo(s.get("id").asText(), s.get("name").asText(), s.get("type").asText(),
                            true, s.get("reliabilityPrior").asDouble());
                }
                if (root.has("listings") && root.get("listings").isArray()) {
                    for (JsonNode n : root.get("listings")) all.add(toRaw(parsed.id(), n));
                }
            } else {
                log.info("No static JSON dataset at {}; operating in live internet mode", resourcePath);
            }
        } catch (Exception e) {
            log.warn("Dataset {} not loaded, live internet search will be used: {}", resourcePath, e.getMessage());
        }
        this.info = parsed;
        log.info("Source '{}' initialized with {} listings", info.name(), all.size());
    }

    @Override
    public SourceInfo info() {
        return info;
    }

    @Override
    public Set<Category> coverage() {
        Set<Category> out = EnumSet.noneOf(Category.class);
        for (RawListing r : all) {
            Category c = Category.parse(r.category());
            if (c != null) out.add(c);
        }
        return out;
    }

    @Override
    public List<RawListing> search(SearchIntent intent) {
        List<RawListing> out = new ArrayList<>();
        for (RawListing r : all) {
            if (matches(r, intent)) out.add(r);
        }
        return out;
    }

    private static boolean matches(RawListing r, SearchIntent intent) {
        if (intent.keywords().isEmpty()) {
            return intent.category() == null || intent.category() == Category.parse(r.category());
        }
        String hay = ((r.title() == null ? "" : r.title()) + " " + (r.description() == null ? "" : r.description())
                + " " + (r.category() == null ? "" : r.category())).toLowerCase(Locale.ROOT);
        for (String kw : intent.keywords()) {
            for (String variant : Vocabulary.expand(kw)) {
                if (Vocabulary.containsWord(hay, variant)) return true;
            }
        }
        return false;
    }

    private static RawListing toRaw(String sourceId, JsonNode n) {
        JsonNode s = n.get("seller");
        RawListing.Seller seller = s == null ? null : new RawListing.Seller(
                text(s, "name"),
                s.path("verified").asBoolean(false),
                s.hasNonNull("rating") ? s.get("rating").asDouble() : null,
                s.hasNonNull("reviews") ? s.get("reviews").asInt() : null,
                s.hasNonNull("accountAgeDays") ? s.get("accountAgeDays").asInt() : null,
                s.path("phone").asBoolean(false),
                text(s, "contactHash"));
        List<String> images = new ArrayList<>();
        if (n.has("images")) n.get("images").forEach(i -> images.add(i.asText()));
        return new RawListing(
                sourceId,
                text(n, "id"),
                text(n, "title"),
                text(n, "description"),
                n.hasNonNull("price") ? n.get("price").asDouble() : null,
                n.hasNonNull("currency") ? n.get("currency").asText() : "INR",
                text(n, "city"),
                text(n, "category"),
                text(n, "condition"),
                images,
                text(n, "url"),
                n.hasNonNull("daysAgo") ? n.get("daysAgo").asInt() : null,
                seller);
    }

    private static String text(JsonNode n, String f) {
        return n.hasNonNull(f) ? n.get(f).asText() : null;
    }
}
