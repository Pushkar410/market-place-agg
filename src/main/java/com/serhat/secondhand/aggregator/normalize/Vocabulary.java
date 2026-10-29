package com.serhat.secondhand.aggregator.normalize;

import com.serhat.secondhand.aggregator.domain.Category;

import java.util.*;

/** Small shared vocabulary: synonyms, category keywords, city aliases, stop words. */
public final class Vocabulary {

    private Vocabulary() {}

    /** Duplicate-tolerant set literal (Set.of throws on duplicates). */
    private static Set<String> words(String... w) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(w)));
    }

    public static final Set<String> STOP_WORDS = words(
            "a", "an", "the", "i", "me", "my", "we", "need", "want", "looking", "look", "for", "find", "show",
            "get", "buy", "in", "on", "at", "near", "nearby", "around", "under", "below", "within", "above",
            "over", "less", "more", "than", "upto", "up", "to", "max", "maximum", "min", "minimum", "budget",
            "price", "cost", "rs", "inr", "rupees", "and", "or", "with", "of", "is", "are", "good", "best",
            "cheap", "cheapest", "affordable", "trusted", "trustworthy", "safe", "verified", "reliable",
            "used", "second", "hand", "secondhand", "new", "like", "please", "any", "some", "that", "this",
            "from", "by", "be", "can", "could", "should", "would", "which", "who", "has", "have", "must",
            "between", "around", "about", "only", "also", "sale", "sell", "selling", "available", "city", "local",
            "seller", "sellers", "buyer", "person", "people", "ones", "one", "items", "item", "stuff", "things");

    /** Groups of words that mean the same thing for matching purposes. */
    private static final List<Set<String>> SYNONYM_GROUPS = List.of(
            words("laptop", "notebook"),
            words("phone", "mobile", "smartphone", "cellphone"),
            words("cycle", "bicycle", "bike", "mtb"),
            words("pg", "hostel", "dormitory", "dorm", "paying", "guest"),
            words("flat", "apartment", "room", "house", "rent", "rental"),
            words("book", "books", "textbook", "textbooks", "novel", "notes"),
            words("headphone", "headphones", "earphones", "earbuds", "headset"),
            words("tv", "television", "monitor", "display"),
            words("table", "desk"),
            words("sofa", "couch"),
            words("tutor", "tutoring", "tuition", "teacher", "coaching"),
            words("scooter", "scooty", "moped"),
            words("fridge", "refrigerator"),
            words("ac", "aircooler", "cooler")
    );

    private static final Map<String, Set<String>> SYNONYM_INDEX = new HashMap<>();
    static {
        for (Set<String> group : SYNONYM_GROUPS) {
            for (String w : group) SYNONYM_INDEX.put(w, group);
        }
    }

    /** Whole-word match (tolerates simple plurals) so "phone" does not match "headphones" or "sit" "situation". */
    public static boolean containsWord(String haystackLower, String word) {
        if (haystackLower == null || haystackLower.isEmpty() || word == null || word.isEmpty()) return false;
        return java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(word) + "(?:s|es)?(?![\\p{L}\\p{N}])")
                .matcher(haystackLower).find();
    }

    public static Set<String> expand(String token) {
        Set<String> group = SYNONYM_INDEX.get(token);
        return group != null ? group : Set.of(token);
    }

    private static final Map<Category, Set<String>> CATEGORY_WORDS = new EnumMap<>(Category.class);
    static {
        CATEGORY_WORDS.put(Category.ELECTRONICS, words("laptop", "notebook", "macbook", "phone", "mobile", "smartphone",
                "iphone", "android", "headset", "tablet", "ipad", "charger", "headphone", "headphones", "earphones", "earbuds",
                "monitor", "keyboard", "mouse", "camera", "tv", "television", "speaker", "router", "printer",
                "calculator", "powerbank", "smartwatch", "console", "ps4", "ps5", "processor", "ram", "ssd", "gpu"));
        CATEGORY_WORDS.put(Category.BOOKS, words("book", "books", "textbook", "textbooks", "novel", "notes", "guide",
                "gate", "cat", "upsc", "syllabus", "reference", "edition", "manual"));
        CATEGORY_WORDS.put(Category.FURNITURE, words("chair", "table", "desk", "bed", "cot", "sofa", "couch", "mattress",
                "wardrobe", "almirah", "shelf", "bookshelf", "cupboard", "fridge", "refrigerator", "cooler", "fan",
                "microwave", "kettle", "stove", "furniture"));
        CATEGORY_WORDS.put(Category.VEHICLES, words("bike", "cycle", "bicycle", "scooter", "scooty", "motorcycle", "moped",
                "car", "activa", "splendor", "pulsar", "helmet", "mtb", "gear"));
        CATEGORY_WORDS.put(Category.HOUSING, words("pg", "hostel", "dormitory", "flat", "apartment", "room", "rent",
                "rental", "bhk", "lease", "roommate", "accommodation", "house", "dorm"));
        CATEGORY_WORDS.put(Category.SPORTS, words("cricket", "bat", "football", "badminton", "racket", "racquet", "gym",
                "dumbbell", "dumbbells", "treadmill", "skates", "tennis", "carrom", "yoga", "shuttle"));
        CATEGORY_WORDS.put(Category.CLOTHING, words("jacket", "shirt", "jeans", "shoes", "sneakers", "hoodie", "saree",
                "kurta", "dress", "tshirt", "watch", "bag", "backpack"));
        CATEGORY_WORDS.put(Category.SERVICES, words("restaurant", "restaurants", "cafe", "cafes", "food", "dining",
                "eatery", "canteen", "bakery", "dhaba", "mess", "hotel", "snack", "snacks", "tea", "coffee",
                "tutor", "tutoring", "tuition", "coaching", "repair", "plumber",
                "electrician", "laundry", "tiffin", "movers", "packers", "cleaning", "service", "classes"));
    }

    /**
     * Generic category labels ("electronics under 20k"). They select a category but are NOT keywords: no listing
     * text contains the word "electronics", so requiring it would wrongly return nothing.
     */
    private static final Map<String, Category> GENERIC_CATEGORY_WORDS = Map.ofEntries(
            Map.entry("electronics", Category.ELECTRONICS), Map.entry("electronic", Category.ELECTRONICS), Map.entry("gadgets", Category.ELECTRONICS),
            Map.entry("gadget", Category.ELECTRONICS), Map.entry("books", Category.BOOKS), Map.entry("furniture", Category.FURNITURE),
            Map.entry("vehicles", Category.VEHICLES), Map.entry("vehicle", Category.VEHICLES), Map.entry("housing", Category.HOUSING),
            Map.entry("sports", Category.SPORTS), Map.entry("sport", Category.SPORTS), Map.entry("clothing", Category.CLOTHING),
            Map.entry("clothes", Category.CLOTHING), Map.entry("services", Category.SERVICES),
            Map.entry("restaurant", Category.SERVICES), Map.entry("restaurants", Category.SERVICES),
            Map.entry("cafe", Category.SERVICES), Map.entry("cafes", Category.SERVICES),
            Map.entry("food", Category.SERVICES), Map.entry("dining", Category.SERVICES));

    /** The category a generic label stands for, or null when the token is an ordinary keyword. */
    public static Category genericCategory(String token) {
        return GENERIC_CATEGORY_WORDS.get(token);
    }

    public static Category inferCategory(Collection<String> tokens) {
        Category best = null;
        int bestHits = 0;
        for (Map.Entry<Category, Set<String>> e : CATEGORY_WORDS.entrySet()) {
            int hits = 0;
            for (String t : tokens) if (e.getValue().contains(t)) hits++;
            if (hits > bestHits) {
                bestHits = hits;
                best = e.getKey();
            }
        }
        return best;
    }

    /** Canonical city names (aliases map onto them). */
    private static final Map<String, String> CITY_ALIASES = new LinkedHashMap<>();
    static {
        CITY_ALIASES.put("sit tumkur", "Tumakuru");
        CITY_ALIASES.put("sit tumakuru", "Tumakuru");
        CITY_ALIASES.put("sit campus", "Tumakuru");
        CITY_ALIASES.put("sit college", "Tumakuru");
        CITY_ALIASES.put("sit", "Tumakuru");
        CITY_ALIASES.put("tumakuru", "Tumakuru");
        CITY_ALIASES.put("tumkur", "Tumakuru");
        CITY_ALIASES.put("bengaluru", "Bengaluru");
        CITY_ALIASES.put("bangalore", "Bengaluru");
        CITY_ALIASES.put("blr", "Bengaluru");
        CITY_ALIASES.put("mysuru", "Mysuru");
        CITY_ALIASES.put("mysore", "Mysuru");
        CITY_ALIASES.put("hubballi", "Hubballi");
        CITY_ALIASES.put("hubli", "Hubballi");
        CITY_ALIASES.put("mangaluru", "Mangaluru");
        CITY_ALIASES.put("mangalore", "Mangaluru");
        CITY_ALIASES.put("shivamogga", "Shivamogga");
        CITY_ALIASES.put("shimoga", "Shivamogga");
        CITY_ALIASES.put("chitradurga", "Chitradurga");
        CITY_ALIASES.put("davanagere", "Davanagere");
        CITY_ALIASES.put("hassan", "Hassan");
        CITY_ALIASES.put("mumbai", "Mumbai");
        CITY_ALIASES.put("delhi", "Delhi");
        CITY_ALIASES.put("chennai", "Chennai");
        CITY_ALIASES.put("hyderabad", "Hyderabad");
        CITY_ALIASES.put("pune", "Pune");
        CITY_ALIASES.put("istanbul", "Istanbul");
        CITY_ALIASES.put("ankara", "Ankara");
        CITY_ALIASES.put("izmir", "Izmir");
    }

    public static String canonicalCity(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String lower = raw.toLowerCase(Locale.ROOT).trim();
        for (Map.Entry<String, String> e : CITY_ALIASES.entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        String cleaned = raw.trim();
        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    public static Optional<String> findCityIn(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : CITY_ALIASES.entrySet()) {
            if (lower.matches(".*\\b" + e.getKey() + "\\b.*")) return Optional.of(e.getValue());
        }
        return Optional.empty();
    }
}
