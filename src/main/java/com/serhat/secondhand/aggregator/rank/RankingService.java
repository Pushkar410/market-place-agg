package com.serhat.secondhand.aggregator.rank;

import com.serhat.secondhand.aggregator.domain.NormalizedListing;
import com.serhat.secondhand.aggregator.domain.SearchIntent;
import com.serhat.secondhand.aggregator.normalize.Vocabulary;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Step 5: RANK. final = wR*relevance + wT*trust + wP*price + wL*location + wU*preference.
 * Default weights favour relevance and trust; they shift with the user's stated preference
 * ("trusted", "cheapest", "nearby").
 */
@Service
public class RankingService {

    public record Weights(double relevance, double trust, double price, double location, double preference) {
        Weights normalised() {
            double s = relevance + trust + price + location + preference;
            return new Weights(relevance / s, trust / s, price / s, location / s, preference / s);
        }
    }

    public Weights weightsFor(SearchIntent intent) {
        Weights w = switch (intent.preference()) {
            case TRUSTED -> new Weights(0.30, 0.45, 0.10, 0.08, 0.07);
            case CHEAPEST -> new Weights(0.30, 0.22, 0.30, 0.10, 0.08);
            case NEARBY -> new Weights(0.30, 0.25, 0.10, 0.27, 0.08);
            default -> new Weights(0.35, 0.30, 0.15, 0.10, 0.10);
        };
        return w.normalised();
    }

    /** 0..1: how well the listing text matches the requested keywords and category. */
    public double relevance(NormalizedListing l, SearchIntent intent) {
        List<String> kws = intent.keywords();
        double textScore = 0.5;
        if (!kws.isEmpty()) {
            String titleLower = l.title().toLowerCase(Locale.ROOT);
            String descLower = l.description().toLowerCase(Locale.ROOT);
            double sum = 0;
            for (String kw : kws) {
                double best = 0;
                for (String variant : Vocabulary.expand(kw)) {
                    if (Vocabulary.containsWord(titleLower, variant)) {
                        best = Math.max(best, variant.equals(kw) ? 1.0 : 0.85);
                    } else if (Vocabulary.containsWord(descLower, variant)) {
                        best = Math.max(best, variant.equals(kw) ? 0.5 : 0.4);
                    }
                }
                sum += best;
            }
            textScore = sum / kws.size();
        }
        double categoryBonus = intent.category() == null ? 0.0 : (intent.category() == l.category() ? 0.15 : -0.10);
        return clamp(textScore + categoryBonus);
    }

    /** 0..1: cheaper (relative to the candidate range / budget) is better; unknown price is neutral-low. */
    public double priceScore(NormalizedListing l, SearchIntent intent, double minPrice, double maxPrice) {
        if (l.price() == null) return 0.3;
        if (intent.maxPrice() != null && intent.maxPrice() > 0) {
            // Inside the budget: reward being comfortably under, but not absurdly cheap
            double ratio = l.price() / intent.maxPrice();
            return clamp(1.0 - 0.6 * ratio);
        }
        if (maxPrice <= minPrice) return 0.7;
        return clamp(1.0 - (l.price() - minPrice) / (maxPrice - minPrice) * 0.8);
    }

    public double locationScore(NormalizedListing l, SearchIntent intent) {
        if (intent.city() == null) return 0.6;
        if (l.city() == null) return 0.3;
        return l.city().equalsIgnoreCase(intent.city()) ? 1.0 : 0.15;
    }

    public double preferenceScore(NormalizedListing l, SearchIntent intent) {
        double s = 0.5;
        if (intent.condition() != null) {
            if (intent.condition().equalsIgnoreCase(l.condition())) s = 1.0;
            else if (l.condition() == null) s = 0.4;
            else s = 0.2;
        } else if (l.condition() != null) {
            s = switch (l.condition()) { case "NEW" -> 0.8; case "LIKE_NEW" -> 0.85; case "GOOD" -> 0.7; default -> 0.5; };
        }
        if (intent.preference() == SearchIntent.Preference.TRUSTED && l.seller() != null && l.seller().verified()) s = Math.min(1.0, s + 0.25);
        return s;
    }

    public double combine(Weights w, double relevance, double trust01, double price, double location, double pref) {
        return w.relevance() * relevance + w.trust() * trust01 + w.price() * price + w.location() * location + w.preference() * pref;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
