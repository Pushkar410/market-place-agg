package com.serhat.secondhand.aggregator.community;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Accepts shared listings, with a global hourly cap as a basic spam guard (there is no moderation queue yet). */
@Service
public class CommunityListingService {

    /** Thrown when too many listings were shared recently; mapped to HTTP 429 by the controller. */
    public static class TooManySubmissionsException extends RuntimeException {
        public TooManySubmissionsException() {
            super("Too many listings were shared in the last hour. Please try again later.");
        }
    }

    public record Submission(String title, String description, Double price, String city, String category,
                             String originName, String originUrl, String contactName, boolean contactProvided) {}

    private final CommunityListingRepository repository;

    @Value("${aggregator.community.max-per-hour:60}")
    private int maxPerHour = 60;

    public CommunityListingService(CommunityListingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CommunityListing submit(Submission s) {
        if (repository.countByCreatedAtAfter(LocalDateTime.now().minusHours(1)) >= maxPerHour) {
            throw new TooManySubmissionsException();
        }
        CommunityListing c = new CommunityListing();
        c.setTitle(clean(s.title(), 120));
        c.setDescription(clean(s.description(), 1000));
        c.setPrice(s.price() != null && s.price() > 0 ? s.price() : null);
        c.setCity(clean(s.city(), 60));
        c.setCategory(clean(s.category() == null ? null : s.category().toUpperCase(java.util.Locale.ROOT), 30));
        c.setOriginName(clean(s.originName(), 60));
        c.setOriginUrl(isHttpUrl(s.originUrl()) ? clean(s.originUrl(), 300) : null);
        c.setContactName(clean(s.contactName(), 60));
        c.setContactProvided(s.contactProvided());
        c.setActive(true);
        return repository.save(c);
    }

    static boolean isHttpUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase(java.util.Locale.ROOT);
        return (u.startsWith("http://") || u.startsWith("https://")) && !u.contains(" ");
    }

    /** Trim, collapse whitespace and cut to length; blank becomes null. Output is rendered escaped by the UI. */
    static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").replaceAll("[ \\t]+", " ").trim();
        if (t.isEmpty()) return null;
        return t.length() <= max ? t : t.substring(0, max);
    }
}
