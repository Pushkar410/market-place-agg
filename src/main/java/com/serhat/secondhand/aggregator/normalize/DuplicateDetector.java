package com.serhat.secondhand.aggregator.normalize;

import com.serhat.secondhand.aggregator.domain.NormalizedListing;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Step 3b: remove duplicates. The same item is often posted on several platforms (or
 * re-posted). Two listings are duplicates when their titles are similar AND their price and
 * location do not contradict - or when title similarity is moderate and the seller's contact
 * fingerprint is identical. Duplicates are clustered (union-find); the most complete listing
 * is kept and the others are recorded as "also listed on".
 */
@Component
public class DuplicateDetector {

    public static final double TITLE_STRONG = 0.60;
    public static final double TITLE_WITH_SAME_CONTACT = 0.35;
    public static final double PRICE_TOLERANCE = 0.12;

    public record Cluster(NormalizedListing primary, List<NormalizedListing> duplicates) {}

    public List<Cluster> cluster(List<NormalizedListing> items) {
        int n = items.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (areDuplicates(items.get(i), items.get(j))) union(parent, i, j);
            }
        }

        Map<Integer, List<NormalizedListing>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(items.get(i));

        List<Cluster> out = new ArrayList<>();
        for (List<NormalizedListing> g : groups.values()) {
            NormalizedListing primary = g.stream()
                    .max(Comparator.comparingDouble(DuplicateDetector::completeness))
                    .orElseThrow();
            List<NormalizedListing> dups = g.stream().filter(x -> x != primary).toList();
            out.add(new Cluster(primary, dups));
        }
        return out;
    }

    public boolean areDuplicates(NormalizedListing a, NormalizedListing b) {
        if (a.sourceId().equals(b.sourceId()) && a.id().equals(b.id())) return true;
        if (a.category() != b.category()
                && a.category() != com.serhat.secondhand.aggregator.domain.Category.OTHER
                && b.category() != com.serhat.secondhand.aggregator.domain.Category.OTHER) return false;

        double sim = jaccard(a.titleTokens(), b.titleTokens());
        if (sim < TITLE_WITH_SAME_CONTACT) return false;

        if (!priceCompatible(a.price(), b.price())) return false;
        if (!cityCompatible(a.city(), b.city())) return false;

        if (sim >= TITLE_STRONG) return true;
        return sameContact(a, b);
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int inter = 0;
        for (String t : a) if (b.contains(t)) inter++;
        int union = a.size() + b.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
    }

    private static boolean priceCompatible(Double p1, Double p2) {
        if (p1 == null || p2 == null) return true; // missing price cannot contradict
        double hi = Math.max(p1, p2);
        return (hi - Math.min(p1, p2)) / hi <= PRICE_TOLERANCE;
    }

    private static boolean cityCompatible(String c1, String c2) {
        return c1 == null || c2 == null || c1.equalsIgnoreCase(c2);
    }

    private static boolean sameContact(NormalizedListing a, NormalizedListing b) {
        return a.seller() != null && b.seller() != null
                && a.seller().contactHash() != null
                && a.seller().contactHash().equals(b.seller().contactHash());
    }

    /** Heuristic data-completeness used to choose which duplicate to keep. */
    static double completeness(NormalizedListing l) {
        double s = 0;
        if (l.price() != null) s += 2;
        s += Math.min(3, l.imageUrls().size());
        s += Math.min(3, l.description().length() / 60.0);
        if (l.city() != null) s += 1;
        if (l.condition() != null) s += 1;
        if (l.seller() != null && l.seller().verified()) s += 1;
        return s;
    }

    private static int find(int[] p, int x) {
        while (p[x] != x) {
            p[x] = p[p[x]];
            x = p[x];
        }
        return x;
    }

    private static void union(int[] p, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra != rb) p[rb] = ra;
    }
}
