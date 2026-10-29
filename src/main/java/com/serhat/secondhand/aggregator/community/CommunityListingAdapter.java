package com.serhat.secondhand.aggregator.community;

import com.serhat.secondhand.aggregator.domain.RawListing;
import com.serhat.secondhand.aggregator.domain.SearchIntent;
import com.serhat.secondhand.aggregator.domain.SourceInfo;
import com.serhat.secondhand.aggregator.normalize.Vocabulary;
import com.serhat.secondhand.aggregator.source.SourceAdapter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Source of REAL third-party listings that people shared themselves (see {@link CommunityListing}).
 * Sellers here are unverified by definition, so the trust score reflects that unless other signals corroborate.
 */
@Component
public class CommunityListingAdapter implements SourceAdapter {

    public static final String ID = "community-shared";

    private final CommunityListingRepository repository;

    public CommunityListingAdapter(CommunityListingRepository repository) {
        this.repository = repository;
    }

    @Override
    public SourceInfo info() {
        return new SourceInfo(ID, "Community Shared (user submitted)", "COMMUNITY_SUBMITTED", false, 0.45);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RawListing> search(SearchIntent intent) {
        return repository.findTop200ByActiveTrueOrderByCreatedAtDesc().stream()
                .filter(c -> matches(c, intent))
                .map(CommunityListingAdapter::toRaw)
                .toList();
    }

    static boolean matches(CommunityListing c, SearchIntent intent) {
        if (intent.keywords().isEmpty()) {
            return intent.category() == null || (c.getCategory() != null && c.getCategory().equalsIgnoreCase(intent.category().name()))
                    || c.getCategory() == null; // unknown category: let the normaliser infer and the pipeline filter
        }
        String hay = ((c.getTitle() == null ? "" : c.getTitle()) + " " + (c.getDescription() == null ? "" : c.getDescription()))
                .toLowerCase(Locale.ROOT);
        for (String kw : intent.keywords()) {
            for (String variant : Vocabulary.expand(kw)) {
                if (Vocabulary.containsWord(hay, variant)) return true;
            }
        }
        return false;
    }

    static RawListing toRaw(CommunityListing c) {
        int days = c.getCreatedAt() == null ? 0 : (int) Math.max(0, Duration.between(c.getCreatedAt(), LocalDateTime.now()).toDays());
        String contact = c.getContactName();
        RawListing.Seller seller = new RawListing.Seller(
                contact == null ? "Community member" : contact,
                false, null, 0, null,
                c.isContactProvided(),
                contact == null ? null : Integer.toHexString(contact.toLowerCase(Locale.ROOT).hashCode()));
        return new RawListing(
                ID,
                String.valueOf(c.getId()),
                c.getTitle(),
                c.getDescription() == null ? "" : c.getDescription(),
                c.getPrice(),
                "INR",
                c.getCity(),
                c.getCategory(),
                null,
                List.of(),
                c.getOriginUrl(),
                days,
                seller);
    }
}
