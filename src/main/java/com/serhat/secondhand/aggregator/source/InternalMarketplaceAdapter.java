package com.serhat.secondhand.aggregator.source;

import com.serhat.secondhand.aggregator.domain.RawListing;
import com.serhat.secondhand.aggregator.domain.SearchIntent;
import com.serhat.secondhand.aggregator.domain.SourceInfo;
import com.serhat.secondhand.aggregator.normalize.Vocabulary;
import com.serhat.secondhand.listing.domain.entity.Listing;
import com.serhat.secondhand.listing.domain.entity.enums.base.ListingStatus;
import com.serhat.secondhand.review.repository.ReviewRepository;
import com.serhat.secondhand.user.domain.entity.User;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The platform's own PostgreSQL marketplace (real data, escrow protected). Seller
 * signals come straight from the user + review tables.
 */
@Component
@RequiredArgsConstructor
public class InternalMarketplaceAdapter implements SourceAdapter {

    public static final String ID = "secondhand-internal";
    private static final int MAX_CANDIDATES = 200;

    private final AggregatorListingRepository listingRepository;
    private final ReviewRepository reviewRepository;

    @Override
    public SourceInfo info() {
        return new SourceInfo(ID, "SecondHand (Internal DB)", "INTERNAL_DATABASE", false, 0.95);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RawListing> search(SearchIntent intent) {
        Specification<Listing> spec = (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            and.add(cb.equal(root.get("status"), ListingStatus.ACTIVE));

            if (!intent.keywords().isEmpty()) {
                List<Predicate> any = new ArrayList<>();
                for (String kw : intent.keywords()) {
                    for (String variant : Vocabulary.expand(kw)) {
                        String like = "%" + variant.toLowerCase(Locale.ROOT) + "%";
                        any.add(cb.like(cb.lower(root.get("title")), like));
                        any.add(cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), like));
                    }
                }
                and.add(cb.or(any.toArray(new Predicate[0])));
            }
            return cb.and(and.toArray(new Predicate[0]));
        };

        List<Listing> listings = listingRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (listings.size() > MAX_CANDIDATES) listings = listings.subList(0, MAX_CANDIDATES);

        Set<Long> sellerIds = new HashSet<>();
        listings.forEach(l -> sellerIds.add(l.getSeller().getId()));
        Map<Long, double[]> reviewMetrics = new HashMap<>();
        if (!sellerIds.isEmpty()) {
            for (Object[] row : reviewRepository.aggregateGreatSellerReviewMetricsBySellerIds(sellerIds)) {
                reviewMetrics.put(((Number) row[0]).longValue(),
                        new double[]{((Number) row[1]).doubleValue(), ((Number) row[2]).doubleValue()});
            }
        }

        List<RawListing> out = new ArrayList<>();
        for (Listing l : listings) {
            User seller = l.getSeller();
            double[] rm = reviewMetrics.get(seller.getId());
            Integer reviewCount = rm != null ? (int) rm[0] : 0;
            Double rating = rm != null && rm[0] > 0 ? rm[1] : null;

            Integer accountAge = seller.getAccountCreationDate() != null
                    ? (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(seller.getAccountCreationDate(), LocalDate.now()))
                    : null;
            int postedDaysAgo = l.getCreatedAt() != null
                    ? (int) Math.max(0, Duration.between(l.getCreatedAt(), LocalDateTime.now()).toDays())
                    : 0;

            String displayName = seller.getName() + (seller.getSurname() != null && !seller.getSurname().isBlank()
                    ? " " + seller.getSurname().charAt(0) + "." : "");

            out.add(new RawListing(
                    ID,
                    l.getId().toString(),
                    l.getTitle(),
                    l.getDescription(),
                    l.getPrice() != null ? l.getPrice().doubleValue() : null,
                    l.getCurrency() != null ? l.getCurrency().name() : null,
                    l.getCity(),
                    l.getListingType() != null ? l.getListingType().name() : null,
                    null,
                    l.getImageUrl() != null && !l.getImageUrl().isBlank() ? List.of(l.getImageUrl()) : List.of(),
                    "/listings/" + l.getId(),
                    postedDaysAgo,
                    new RawListing.Seller(
                            displayName,
                            seller.isAccountVerified(),
                            rating,
                            reviewCount,
                            accountAge,
                            seller.getPhoneNumber() != null && !seller.getPhoneNumber().isBlank(),
                            seller.getPhoneNumber() != null ? Integer.toHexString(seller.getPhoneNumber().hashCode()) : null)
            ));
        }
        return out;
    }
}
