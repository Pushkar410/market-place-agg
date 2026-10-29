package com.serhat.secondhand.aggregator.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListingTrustRecordRepository extends JpaRepository<ListingTrustRecord, Long> {

    List<ListingTrustRecord> findBySearchIdOrderByResultRankAsc(Long searchId);
}
