package com.serhat.secondhand.aggregator.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CommunityListingRepository extends JpaRepository<CommunityListing, Long> {

    List<CommunityListing> findTop200ByActiveTrueOrderByCreatedAtDesc();

    long countByCreatedAtAfter(LocalDateTime since);
}
