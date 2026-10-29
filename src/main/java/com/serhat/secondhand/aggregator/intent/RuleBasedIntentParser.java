package com.serhat.secondhand.aggregator.intent;

import com.serhat.secondhand.aggregator.domain.Category;
import com.serhat.secondhand.aggregator.domain.SearchIntent;
import com.serhat.secondhand.aggregator.normalize.Vocabulary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic natural-language understanding used when no LLM key is configured
 * (and as a safety net when the LLM fails). Handles budgets ("under 20k", "between 5000 and
 * 10,000"), city, condition, category and preference cues ("trusted", "cheapest", "nearby").
 */
@Component
public class RuleBasedIntentParser {

    private static final String NUM = "(?:rs\\.?|inr|₹)?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k|thousand|lakh|lac|l)?";
    private static final Pattern BETWEEN = Pattern.compile("between\\s+" + NUM + "\\s*(?:and|to|-)\\s*" + NUM);
    private static final Pattern RANGE = Pattern.compile("\\b" + NUM + "\\s*(?:-|to)\\s*" + NUM + "\\b");
    private static final Pattern MAX = Pattern.compile("(?:under|below|within|upto|up to|max(?:imum)?|less than|<=?|budget(?: of)?|at most|not more than)\\s*" + NUM);
    private static final Pattern MIN = Pattern.compile("(?:above|over|min(?:imum)?|more than|at least|>=?)\\s*" + NUM);

    public SearchIntent parse(String query) {
        String q = query == null ? "" : query.trim();
        String lower = q.toLowerCase(Locale.ROOT);

        Double min = null;
        Double max = null;
        String working = lower;

        Matcher m = BETWEEN.matcher(working);
        if (m.find()) {
            min = amount(m.group(1), m.group(2));
            max = amount(m.group(3), m.group(4));
            working = working.replace(m.group(), " ");
        } else {
            m = MAX.matcher(working);
            if (m.find()) {
                max = amount(m.group(1), m.group(2));
                working = working.replace(m.group(), " ");
            }
            m = MIN.matcher(working);
            if (m.find()) {
                min = amount(m.group(1), m.group(2));
                working = working.replace(m.group(), " ");
            }
            if (max == null && min == null) {
                m = RANGE.matcher(working);
                if (m.find()) {
                    min = amount(m.group(1), m.group(2));
                    max = amount(m.group(3), m.group(4));
                    working = working.replace(m.group(), " ");
                }
            }
        }
        if (min != null && max != null && min > max) {
            double t = min; min = max; max = t;
        }

        String city = Vocabulary.findCityIn(lower).orElse(null);

        String condition = null;
        if (lower.matches(".*\\b(like new|as good as new|barely used|mint)\\b.*")) condition = "LIKE_NEW";
        else if (lower.matches(".*\\b(brand new|sealed|unused)\\b.*")) condition = "NEW";
        else if (lower.matches(".*\\b(good condition|well maintained)\\b.*")) condition = "GOOD";

        SearchIntent.Preference pref = SearchIntent.Preference.BALANCED;
        if (lower.matches(".*\\b(trusted|trustworthy|safe|safest|verified|reliable|genuine|legit)\\b.*")) pref = SearchIntent.Preference.TRUSTED;
        else if (lower.matches(".*\\b(cheap|cheapest|lowest price|budget|affordable|bargain)\\b.*")) pref = SearchIntent.Preference.CHEAPEST;
        else if (lower.matches(".*\\b(nearby|near me|close by|walking distance|near)\\b.*")) pref = SearchIntent.Preference.NEARBY;

        List<String> keywords = new ArrayList<>();
        Category generic = null;
        for (String k : tokenize(working, city)) {
            Category g = Vocabulary.genericCategory(k);
            if (g != null) generic = generic == null ? g : generic;
            else keywords.add(k);
        }
        Category category = Vocabulary.inferCategory(keywords);
        if (category == null) category = generic;

        return new SearchIntent(q, keywords, category, min, max, city, condition, pref, "rule-based");
    }

    private static List<String> tokenize(String text, String city) {
        List<String> out = new ArrayList<>();
        Set<String> cityWords = new HashSet<>();
        if (city != null) {
            cityWords.add(city.toLowerCase(Locale.ROOT));
            cityWords.addAll(Arrays.asList("tumkur", "bangalore", "mysore", "hubli", "mangalore", "shimoga"));
        }
        for (String raw : text.split("[^\\p{L}\\p{N}+]+")) {
            String t = raw.trim();
            if (t.length() < 2) continue;
            if (Vocabulary.STOP_WORDS.contains(t)) continue;
            if (cityWords.contains(t)) continue;
            if (t.matches("\\d{4,}(k|l)?|\\d+(k|l)")) continue; // budget-like numbers; short numbers (iphone 13) are kept
            if (t.matches("like|new|brand|sealed|unused|good|condition|mint|barely|maintained|genuine|legit|safest|lowest|bargain|walking|distance|close|rs|inr")) continue;
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    private static Double amount(String number, String suffix) {
        double v = Double.parseDouble(number.replace(",", ""));
        if (suffix != null) {
            switch (suffix) {
                case "k", "thousand" -> v *= 1_000;
                case "lakh", "lac", "l" -> v *= 100_000;
                default -> { }
            }
        }
        return v;
    }
}
