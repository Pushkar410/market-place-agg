package com.serhat.secondhand.aggregator.trust;

import com.serhat.secondhand.aggregator.domain.NormalizedListing;
import com.serhat.secondhand.aggregator.domain.RawListing;
import com.serhat.secondhand.aggregator.domain.TrustScore;
import com.serhat.secondhand.aggregator.domain.TrustScore.Signal;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Step 4: ANALYZE + SCORE. Produces an explainable 0-100 Trust Score from the signals that
 * are actually available for each listing, minus risk penalties.
 *
 * <pre>
 *  Seller reputation   25   (Bayesian-smoothed rating weighted by review volume)
 *  Seller identity     15   (verified, phone provided, account age)
 *  Listing quality     20   (photos, description, price, location, condition)
 *  Price plausibility  15   (vs. median of comparable listings in this search)
 *  Freshness           10
 *  Source reliability   5   (prior for the platform itself)
 *  Cross-source         10  (the same item corroborated on several platforms)
 *  Risk penalties      -    (scam phrases, "too good to be true" price, off-platform payment)
 * </pre>
 */
@Service
public class TrustScoringService {

    public static final double W_REPUTATION = 25;
    public static final double W_IDENTITY = 15;
    public static final double W_QUALITY = 20;
    public static final double W_PRICE = 15;
    public static final double W_FRESH = 10;
    public static final double W_SOURCE = 5;
    public static final double W_CROSS = 10;

    private static final double PRIOR_RATING = 3.5;
    private static final double PRIOR_WEIGHT = 5;

    private static final List<Pattern> SCAM_PATTERNS = List.of(
            Pattern.compile("advance (payment|amount)|token (amount|money)|pay (\\d+ )?(token|advance|first)|booking amount", Pattern.CASE_INSENSITIVE),
            Pattern.compile("western union|gift card|crypto|bitcoin|wire transfer", Pattern.CASE_INSENSITIVE),
            Pattern.compile("courier (will|can)? ?deliver|deliver(y)? after payment|delivery only|no (meet(up)?|visit|calls?)|only whatsapp", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\barmy\\b|transferred to another|leaving (the )?country|urgent(ly)? need money|send (me )?(your )?(otp|upi pin)", Pattern.CASE_INSENSITIVE)
    );
    private static final String[] SCAM_LABELS = {
            "Asks for advance / token payment",
            "Mentions untraceable payment method",
            "Refuses meetup or inspection",
            "Classic scam story (urgent transfer / forces)"
    };

    /**
     * @param listing       the listing to score
     * @param medianPrice   median price of comparable listings (same category + currency) in this search, or null
     * @param comparables   how many comparable priced listings produced that median
     * @param sourcePrior   0..1 reliability prior of the source
     * @param sourceCount   number of distinct sources this item appeared on (1 = only here)
     */
    public TrustScore score(NormalizedListing listing, Double medianPrice, int comparables, double sourcePrior, int sourceCount) {
        List<Signal> signals = new ArrayList<>();
        List<String> flags = new ArrayList<>();
        RawListing.Seller seller = listing.seller();

        // 1. Seller reputation
        double rep;
        String repDetail;
        if (seller != null && seller.rating() != null && seller.reviewCount() != null && seller.reviewCount() > 0) {
            double n = seller.reviewCount();
            double smoothed = (seller.rating() * n + PRIOR_RATING * PRIOR_WEIGHT) / (n + PRIOR_WEIGHT);
            rep = clamp((smoothed - 2.0) / 3.0) * W_REPUTATION;
            repDetail = String.format(Locale.ROOT, "%.1f★ from %d review%s", seller.rating(), seller.reviewCount(), n == 1 ? "" : "s");
        } else {
            rep = 0.30 * W_REPUTATION;
            repDetail = "No reviews yet (neutral)";
            flags.add("Seller has no review history");
        }
        signals.add(new Signal("Seller reputation", round(rep), W_REPUTATION, repDetail));

        // 2. Seller identity
        double ident = 0;
        List<String> identParts = new ArrayList<>();
        if (seller != null) {
            if (seller.verified()) { ident += 8; identParts.add("verified"); }
            if (seller.phoneProvided()) { ident += 4; identParts.add("phone on file"); }
            Integer age = seller.accountAgeDays();
            if (age != null) {
                if (age >= 365) { ident += 3; identParts.add("account 1y+"); }
                else if (age >= 90) { ident += 2; identParts.add("account 3m+"); }
                else if (age >= 30) { ident += 1; identParts.add("account 1m+"); }
                else if (age < 14) { flags.add("Very new seller account (" + age + " days)"); }
            }
            if (!seller.verified()) flags.add("Seller identity not verified");
            if (!seller.phoneProvided()) flags.add("No contact number provided");
        } else {
            flags.add("No seller information available");
        }
        signals.add(new Signal("Seller identity", round(ident), W_IDENTITY, identParts.isEmpty() ? "No identity signals" : String.join(", ", identParts)));

        // 3. Listing quality
        double q = 0;
        List<String> qParts = new ArrayList<>();
        int imgs = listing.imageUrls().size();
        q += Math.min(3, imgs) * 2.0;
        qParts.add(imgs + " photo" + (imgs == 1 ? "" : "s"));
        int dl = listing.description().length();
        if (dl >= 80) q += 6; else if (dl >= 30) q += 3;
        qParts.add(dl >= 80 ? "detailed description" : dl >= 30 ? "short description" : "little/no description");
        if (listing.price() != null) q += 3; else flags.add("Price not stated");
        if (listing.city() != null) q += 3;
        if (listing.condition() != null) q += 2;
        if (imgs == 0) flags.add("No photos");
        signals.add(new Signal("Listing quality", round(q), W_QUALITY, String.join(", ", qParts)));

        // 4. Price plausibility
        double pricePts;
        String priceDetail;
        double penalty = 0;
        if (listing.price() != null && medianPrice != null && comparables >= 3 && medianPrice > 0) {
            double ratio = listing.price() / medianPrice;
            if (ratio >= 0.6 && ratio <= 1.5) pricePts = W_PRICE;
            else if (ratio > 1.5) pricePts = W_PRICE * clamp(1 - (ratio - 1.5) / 1.5);
            else if (ratio >= 0.4) pricePts = W_PRICE * 0.4;
            else { pricePts = 0; }
            priceDetail = String.format(Locale.ROOT, "%d%% of median (%d comparables)", Math.round(ratio * 100), comparables);
            if (ratio < 0.4) {
                flags.add("Price is far below market - possible bait or scam");
                penalty += 10;
            } else if (ratio > 1.8) {
                flags.add("Priced well above comparable listings");
            }
        } else {
            pricePts = 0.6 * W_PRICE;
            priceDetail = "Not enough comparables (neutral)";
        }
        signals.add(new Signal("Price plausibility", round(pricePts), W_PRICE, priceDetail));

        // 5. Freshness
        int d = listing.postedDaysAgo();
        double fresh = d <= 7 ? 1.0 : d <= 30 ? 0.8 : d <= 90 ? 0.5 : d <= 180 ? 0.25 : 0.0;
        if (d > 90) flags.add("Listing is " + d + " days old - may be outdated");
        signals.add(new Signal("Freshness", round(fresh * W_FRESH), W_FRESH, "Posted " + (d == 0 ? "today" : d + " day" + (d == 1 ? "" : "s") + " ago")));

        // 6. Source reliability
        signals.add(new Signal("Source reliability", round(clamp(sourcePrior) * W_SOURCE), W_SOURCE,
                String.format(Locale.ROOT, "%s prior %.0f%%", listing.sourceName(), sourcePrior * 100)));

        // 7. Cross-source corroboration
        double cross = sourceCount >= 3 ? 1.0 : sourceCount == 2 ? 0.75 : 0.35;
        signals.add(new Signal("Cross-source match", round(cross * W_CROSS), W_CROSS,
                sourceCount > 1 ? "Found on " + sourceCount + " sources" : "Only on this source"));

        // Risk penalties from text
        String text = listing.title() + " " + listing.description();
        for (int i = 0; i < SCAM_PATTERNS.size(); i++) {
            if (SCAM_PATTERNS.get(i).matcher(text).find()) {
                flags.add(SCAM_LABELS[i]);
                penalty += 15;
            }
        }
        penalty = Math.min(penalty, 45);

        double total = signals.stream().mapToDouble(Signal::points).sum() - penalty;
        int score = (int) Math.round(Math.max(0, Math.min(100, total)));
        TrustScore.Level level = score >= 75 ? TrustScore.Level.HIGH : score >= 50 ? TrustScore.Level.MEDIUM : TrustScore.Level.LOW;

        if (penalty > 0) signals.add(new Signal("Risk penalty", -round(penalty), 0, "Deducted for risk flags"));
        return new TrustScore(score, level, signals, flags);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
