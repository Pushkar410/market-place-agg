package com.serhat.secondhand.aggregator.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    List<SearchHistory> findTop20ByUserEmailOrderByCreatedAtDesc(String userEmail);
}
