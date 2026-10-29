package com.serhat.secondhand.aggregator.source;

import com.serhat.secondhand.listing.domain.entity.Listing;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Read-only access path used by the aggregator's internal source adapter. */
@Repository
public interface AggregatorListingRepository extends JpaRepository<Listing, UUID>, JpaSpecificationExecutor<Listing> {

    @Override
    @EntityGraph(attributePaths = {"seller"})
    java.util.List<Listing> findAll(org.springframework.data.jpa.domain.Specification<Listing> spec,
                                    org.springframework.data.domain.Sort sort);
}
