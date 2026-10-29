package com.serhat.secondhand.aggregator.service;

import com.serhat.secondhand.aggregator.domain.*;
import com.serhat.secondhand.aggregator.intent.IntentService;
import com.serhat.secondhand.aggregator.explain.ResultExplainer;
import com.serhat.secondhand.aggregator.normalize.DuplicateDetector;
import com.serhat.secondhand.aggregator.normalize.ListingNormalizer;
import com.serhat.secondhand.aggregator.plan.SearchPlanner;
import com.serhat.secondhand.aggregator.rank.RankingService;
import com.serhat.secondhand.aggregator.source.SourceAdapter;
import com.serhat.secondhand.aggregator.trust.TrustScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * The agent: UNDERSTAND -> (CLARIFY | PLAN) -> SEARCH -> AGGREGATE -> ANALYZE -> SCORE -> RANK -> RECOMMEND (+ EXPLAIN).
 * If too few results survive, the agent REFLECTS and relaxes the least important constraints
 * (condition, city, category, budget +25%) and searches again - recorded in the agent trace.
 */
@Service
public class AggregatorService {

    private static final Logger log = LoggerFactory.getLogger(AggregatorService.class);

    private static final int MIN_RESULTS_BEFORE_RELAX = 3;
    /** Listings below this relevance are not shown (they matched one stray word). */
    static final double MIN_RELEVANCE = 0.45;
    /** Multiplier applied to the final score of LOW-trust listings so scams never lead the list. */
    static final double LOW_TRUST_FACTOR = 0.55;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;

    private final IntentService intentService;
    private final List<SourceAdapter> adapters;
    private final ListingNormalizer normalizer;
    private final DuplicateDetector duplicateDetector;
    private final TrustScoringService trustScoring;
    private final RankingService ranking;
    private final SearchPlanner planner;
    private final ResultExplainer explainer;
    private final ExecutorService executor;

    @Value("${aggregator.source-timeout-ms:4000}")
    private long sourceTimeoutMs = 4000;

    /** Offline wiring (no LLM planner / explainer); used by unit tests. */
    public AggregatorService(IntentService intentService, List<SourceAdapter> adapters, ListingNormalizer normalizer,
                             DuplicateDetector duplicateDetector, TrustScoringService trustScoring, RankingService ranking) {
        this(intentService, adapters, normalizer, duplicateDetector, trustScoring, ranking, SearchPlanner.rulesOnly(), ResultExplainer.disabled());
    }

    @Autowired
    public AggregatorService(IntentService intentService, List<SourceAdapter> adapters, ListingNormalizer normalizer,
                             DuplicateDetector duplicateDetector, TrustScoringService trustScoring, RankingService ranking,
                             SearchPlanner planner, ResultExplainer explainer) {
        this.planner = planner;
        this.explainer = explainer;
        this.intentService = intentService;
        this.adapters = adapters;
        this.normalizer = normalizer;
        this.duplicateDetector = duplicateDetector;
        this.trustScoring = trustScoring;
        this.ranking = ranking;
        this.executor = Executors.newFixedThreadPool(Math.max(2, adapters.size()), r -> {
            Thread t = new Thread(r, "aggregator-source");
            t.setDaemon(true);
            return t;
        });
    }

    @jakarta.annotation.PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public List<SourceInfo> sources() {
        return adapters.stream().map(SourceAdapter::info).toList();
    }

    public AggregatorResponse search(String query, Integer limitParam) {
        long t0 = System.currentTimeMillis();
        int limit = limitParam == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limitParam));
        List<String> trace = new ArrayList<>();

        // 1. UNDERSTAND
        SearchIntent intent = intentService.understand(query);
        trace.add("Understand: " + describe(intent) + " (" + intent.interpretedBy() + ")");

        // 1b. CLARIFY: a request with neither a keyword nor a category cannot be searched meaningfully
        Optional<String> clarification = SearchPlanner.clarificationFor(intent);
        if (clarification.isPresent()) {
            trace.add("Clarify: request is too vague, asking the user instead of guessing");
            return new AggregatorResponse(query, intent, List.of(), 0, 0, 0, List.of(), clarification.get(), trace,
                    System.currentTimeMillis() - t0, null, clarification.get(), null, null);
        }

        // 1c. PLAN: which sources are worth querying for this request
        SearchPlan plan = planner.plan(intent, adapters);
        trace.add("Plan: " + plan.rationale() + " (" + plan.plannedBy() + ")");
        Set<String> planned = new HashSet<>(plan.selectedSourceIds());

        // 2-6. run pipeline, relax if needed
        final SearchIntent original = intent;
        Pipeline result = runPipeline(intent, original, trace, adaptersFor(intent, original, planned));
        List<SearchIntent> relaxations = relaxations(intent);
        for (SearchIntent relaxed : relaxations) {
            if (result.ranked.size() >= MIN_RESULTS_BEFORE_RELAX) break;
            trace.add("Reflect: only " + result.ranked.size() + " result(s) - relaxing constraints -> " + describe(relaxed));
            Pipeline next = runPipeline(relaxed, original, trace, adaptersFor(relaxed, original, planned));
            if (next.ranked.size() > result.ranked.size()) {
                result = next;
                intent = relaxed;
            }
        }

        List<RankedListing> top = result.ranked.size() > limit ? result.ranked.subList(0, limit) : result.ranked;
        String summary = summarize(intent, result, top);
        trace.add("Recommend: returning " + top.size() + " of " + result.ranked.size() + " ranked listings");

        // 7. EXPLAIN (optional LLM wording over already-computed facts; never changes scores or order)
        Optional<String> explanation = explainer.explain(query, intent, top);
        explanation.ifPresent(e -> trace.add("Explain: wrote a grounded comparison of the top results (gemini)"));

        return new AggregatorResponse(query, intent, result.reports, result.fetched, result.duplicatesRemoved,
                result.ranked.size(), top, summary, trace, System.currentTimeMillis() - t0,
                plan, null, explanation.orElse(null), explanation.isPresent() ? "gemini" : null);
    }

    // ------------------------------------------------------------------ pipeline

    private record Pipeline(List<SourceReport> reports, int fetched, int duplicatesRemoved, List<RankedListing> ranked) {}

    /** Planned sources only - except when a relaxation dropped the category, then every source gets a chance. */
    private List<SourceAdapter> adaptersFor(SearchIntent current, SearchIntent original, Set<String> planned) {
        if (original.category() != null && current.category() == null) return adapters;
        return adapters.stream().filter(a -> planned.contains(a.info().id())).toList();
    }

    private Pipeline runPipeline(SearchIntent intent, SearchIntent original, List<String> trace, List<SourceAdapter> active) {
        // 2. SEARCH (parallel, fault tolerant)
        Map<SourceAdapter, Future<List<RawListing>>> futures = new LinkedHashMap<>();
        Map<SourceAdapter, Long> started = new HashMap<>();
        for (SourceAdapter a : active) {
            started.put(a, System.currentTimeMillis());
            futures.put(a, executor.submit(() -> a.search(intent)));
        }
        List<SourceReport> reports = new ArrayList<>();
        List<NormalizedListing> normalized = new ArrayList<>();
        int fetched = 0;
        for (Map.Entry<SourceAdapter, Future<List<RawListing>>> e : futures.entrySet()) {
            SourceInfo info = e.getKey().info();
            try {
                List<RawListing> raws = e.getValue().get(sourceTimeoutMs, TimeUnit.MILLISECONDS);
                long ms = System.currentTimeMillis() - started.get(e.getKey());
                fetched += raws.size();
                reports.add(new SourceReport(info.id(), info.name(), info.type(), info.simulated(), "OK", raws.size(), ms));
                for (RawListing r : raws) normalizer.normalize(r, info.name()).ifPresent(normalized::add);
            } catch (Exception ex) {
                e.getValue().cancel(true);
                log.warn("Source {} failed: {}", info.id(), ex.toString());
                reports.add(new SourceReport(info.id(), info.name(), info.type(), info.simulated(),
                        ex instanceof TimeoutException ? "TIMEOUT" : "ERROR", 0, System.currentTimeMillis() - started.get(e.getKey())));
            }
        }
        for (SourceAdapter a : adapters) {
            if (!active.contains(a)) {
                SourceInfo info = a.info();
                reports.add(new SourceReport(info.id(), info.name(), info.type(), info.simulated(), "SKIPPED", 0, 0));
            }
        }
        trace.add("Search: queried " + active.size() + " source" + (active.size() == 1 ? "" : "s") + " in parallel, retrieved " + fetched + " raw listings");

        // hard filters from the intent (budget / category)
        List<NormalizedListing> filtered = normalized.stream().filter(l -> passesFilters(l, intent)).toList();
        trace.add("Normalize: " + normalized.size() + " listings in a common schema, " + filtered.size() + " match budget/category");

        // 3. AGGREGATE: de-duplicate
        List<DuplicateDetector.Cluster> clusters = duplicateDetector.cluster(filtered);
        int removed = filtered.size() - clusters.size();
        trace.add("Aggregate: removed " + removed + " duplicate listing(s) -> " + clusters.size() + " unique items");

        // price statistics per (category, currency) over the unique items
        Map<String, List<Double>> pricesByGroup = new HashMap<>();
        for (DuplicateDetector.Cluster c : clusters) {
            NormalizedListing p = c.primary();
            if (p.price() != null) pricesByGroup.computeIfAbsent(groupKey(p), k -> new ArrayList<>()).add(p.price());
        }

        double minP = clusters.stream().map(c -> c.primary().price()).filter(Objects::nonNull).mapToDouble(Double::doubleValue).min().orElse(0);
        double maxP = clusters.stream().map(c -> c.primary().price()).filter(Objects::nonNull).mapToDouble(Double::doubleValue).max().orElse(0);
        Map<String, Double> priorBySource = adapters.stream().collect(Collectors.toMap(a -> a.info().id(), a -> a.info().reliabilityPrior(), (a, b) -> a));
        RankingService.Weights w = ranking.weightsFor(intent);

        // 4-5. ANALYZE + SCORE, then RANK
        List<RankedListing> ranked = new ArrayList<>();
        for (DuplicateDetector.Cluster c : clusters) {
            NormalizedListing p = c.primary();
            List<Double> group = pricesByGroup.getOrDefault(groupKey(p), List.of());
            Double median = group.size() >= 3 ? median(group) : null;

            Set<String> sourceNames = new LinkedHashSet<>();
            sourceNames.add(p.sourceName());
            c.duplicates().forEach(d -> sourceNames.add(d.sourceName()));
            Set<String> sourceIds = new HashSet<>();
            sourceIds.add(p.sourceId());
            c.duplicates().forEach(d -> sourceIds.add(d.sourceId()));

            TrustScore trust = trustScoring.score(p, median, group.size(), priorBySource.getOrDefault(p.sourceId(), 0.5), sourceIds.size());

            double rel = ranking.relevance(p, intent);
            double price = ranking.priceScore(p, intent, minP, maxP);
            if (median != null && p.price() != null && p.price() < 0.4 * median) price = Math.min(price, 0.2); // too-good-to-be-true is not a bargain
            double loc = ranking.locationScore(p, intent);
            double pref = ranking.preferenceScore(p, intent);
            if (!intent.keywords().isEmpty() && rel < MIN_RELEVANCE) continue;
            double finalScore = ranking.combine(w, rel, trust.score() / 100.0, price, loc, pref);
            if (trust.level() == TrustScore.Level.LOW) finalScore *= LOW_TRUST_FACTOR;

            List<String> alsoOn = sourceNames.stream().filter(n -> !n.equals(p.sourceName())).toList();
            ranked.add(new RankedListing(0, p, trust, round3(rel), round3(price), round3(loc), round3(pref),
                    round3(finalScore), alsoOn, reasons(p, trust, original, median, alsoOn, loc)));
        }
        ranked.sort(Comparator.comparingDouble(RankedListing::finalScore).reversed());
        List<RankedListing> numbered = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            RankedListing r = ranked.get(i);
            numbered.add(new RankedListing(i + 1, r.listing(), r.trust(), r.relevance(), r.priceScore(), r.locationScore(),
                    r.preferenceScore(), r.finalScore(), r.alsoListedOn(), r.reasons()));
        }
        trace.add("Analyze/Score/Rank: computed trust scores and ranked " + numbered.size() + " listings (weights R/T/P/L/U = "
                + String.format(Locale.ROOT, "%.2f/%.2f/%.2f/%.2f/%.2f", w.relevance(), w.trust(), w.price(), w.location(), w.preference()) + ")");
        return new Pipeline(reports, fetched, removed, numbered);
    }

    private static boolean passesFilters(NormalizedListing l, SearchIntent intent) {
        if (intent.maxPrice() != null && l.price() != null && l.price() > intent.maxPrice()) return false;
        if (intent.minPrice() != null && l.price() != null && l.price() < intent.minPrice()) return false;
        if (intent.category() != null && l.category() != intent.category()
                && l.category() != Category.OTHER) return false;
        return true;
    }

    private static List<SearchIntent> relaxations(SearchIntent i) {
        List<SearchIntent> out = new ArrayList<>();
        SearchIntent cur = i;
        if (cur.condition() != null) { cur = cur.withoutCondition(); out.add(cur); }
        if (cur.city() != null) { cur = cur.withoutCity(); out.add(cur); }
        if (cur.maxPrice() != null) { cur = cur.withMaxPrice(cur.maxPrice() * 1.25); out.add(cur); }
        if (cur.category() != null) { cur = cur.withoutCategory(); out.add(cur); }
        return out;
    }

    private static String groupKey(NormalizedListing l) {
        return l.category() + "|" + l.currency();
    }

    private static double median(List<Double> values) {
        List<Double> s = new ArrayList<>(values);
        Collections.sort(s);
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }

    private static List<String> reasons(NormalizedListing p, TrustScore trust, SearchIntent intent, Double median,
                                        List<String> alsoOn, double loc) {
        List<String> r = new ArrayList<>();
        RawListing.Seller s = p.seller();
        if (s != null && s.verified() && s.rating() != null && s.reviewCount() != null && s.reviewCount() > 0)
            r.add(String.format(Locale.ROOT, "Verified seller, %.1f★ across %d reviews", s.rating(), s.reviewCount()));
        else if (s != null && s.verified()) r.add("Verified seller");
        if (!alsoOn.isEmpty()) r.add("Cross-listed on " + alsoOn.size() + " other source" + (alsoOn.size() == 1 ? "" : "s") + " (duplicates merged)");
        if (median != null && p.price() != null) {
            double ratio = p.price() / median;
            if (ratio < 0.95 && ratio >= 0.6) r.add(String.format(Locale.ROOT, "%d%% below the typical price", Math.round((1 - ratio) * 100)));
            else if (ratio > 1.15) r.add(String.format(Locale.ROOT, "%d%% above the typical price", Math.round((ratio - 1) * 100)));
        }
        if (intent.maxPrice() != null && p.price() != null) {
            if (p.price() <= intent.maxPrice()) r.add("Within your budget");
            else r.add("Slightly above your budget (shown because few exact matches exist)");
        }
        if (intent.city() != null && loc >= 1.0) r.add("Located in " + p.city());
        if (p.postedDaysAgo() <= 7) r.add("Posted recently");
        if (trust.level() == TrustScore.Level.LOW) r.add("Ranked lower because of risk flags");
        return r;
    }

    private static String describe(SearchIntent i) {
        StringBuilder sb = new StringBuilder("looking for ");
        sb.append(i.keywords().isEmpty() ? "anything" : String.join(" ", i.keywords()));
        if (i.category() != null) sb.append(" [").append(i.category()).append("]");
        if (i.minPrice() != null) sb.append(", min ").append(Math.round(i.minPrice()));
        if (i.maxPrice() != null) sb.append(", max ").append(Math.round(i.maxPrice()));
        if (i.city() != null) sb.append(", in ").append(i.city());
        if (i.condition() != null) sb.append(", condition ").append(i.condition());
        if (i.preference() != SearchIntent.Preference.BALANCED) sb.append(", prefers ").append(i.preference());
        return sb.toString();
    }

    private static String summarize(SearchIntent intent, Pipeline p, List<RankedListing> top) {
        if (top.isEmpty()) {
            return "No listings matched. Try fewer words, a higher budget, or a different city.";
        }
        RankedListing best = top.get(0);
        long trusted = top.stream().filter(r -> r.trust().level() == TrustScore.Level.HIGH).count();
        long risky = top.stream().filter(r -> r.trust().level() == TrustScore.Level.LOW).count();
        StringBuilder sb = new StringBuilder();
        long queried = p.reports.stream().filter(r -> !"SKIPPED".equals(r.status())).count();
        sb.append(String.format(Locale.ROOT, "Searched %d sources, found %d listings and merged %d duplicate%s. ",
                queried, p.fetched, p.duplicatesRemoved, p.duplicatesRemoved == 1 ? "" : "s"));
        sb.append(String.format(Locale.ROOT, "Top pick: \"%s\"%s, trust %d/100", best.listing().title(),
                best.listing().price() != null ? " at " + best.listing().currency() + " " + Math.round(best.listing().price()) : "",
                best.trust().score()));
        if (!best.reasons().isEmpty()) {
            String why = best.reasons().get(0);
            sb.append(" - ").append(Character.toLowerCase(why.charAt(0))).append(why.substring(1));
        }
        sb.append(". ");
        sb.append("High-trust results: ").append(trusted).append(" of ").append(top.size());
        if (risky > 0) sb.append("; ").append(risky).append(" flagged high-risk - never pay in advance for those");
        sb.append(".");
        return sb.toString();
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
