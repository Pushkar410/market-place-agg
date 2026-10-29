package com.serhat.secondhand.aggregator.history;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One aggregator search: what was asked, how the agent planned it, and a few headline numbers. */
@Entity
@Table(name = "aggregator_search_history", indexes = {
        @Index(name = "idx_agg_history_user", columnList = "user_email"),
        @Index(name = "idx_agg_history_created", columnList = "created_at")
})
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_text", nullable = false, length = 300)
    private String queryText;

    /** Null for anonymous searches. */
    @Column(name = "user_email", length = 254)
    private String userEmail;

    @Column(name = "interpreted_by", length = 40)
    private String interpretedBy;

    @Column(name = "planned_by", length = 40)
    private String plannedBy;

    @Column(name = "clarification_asked", nullable = false)
    private boolean clarificationAsked;

    @Column(name = "total_fetched", nullable = false)
    private int totalFetched;

    @Column(name = "duplicates_removed", nullable = false)
    private int duplicatesRemoved;

    @Column(name = "total_ranked", nullable = false)
    private int totalRanked;

    @Column(name = "top_trust_score")
    private Integer topTrustScore;

    @Column(name = "took_ms", nullable = false)
    private long tookMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getInterpretedBy() { return interpretedBy; }
    public void setInterpretedBy(String interpretedBy) { this.interpretedBy = interpretedBy; }
    public String getPlannedBy() { return plannedBy; }
    public void setPlannedBy(String plannedBy) { this.plannedBy = plannedBy; }
    public boolean isClarificationAsked() { return clarificationAsked; }
    public void setClarificationAsked(boolean clarificationAsked) { this.clarificationAsked = clarificationAsked; }
    public int getTotalFetched() { return totalFetched; }
    public void setTotalFetched(int totalFetched) { this.totalFetched = totalFetched; }
    public int getDuplicatesRemoved() { return duplicatesRemoved; }
    public void setDuplicatesRemoved(int duplicatesRemoved) { this.duplicatesRemoved = duplicatesRemoved; }
    public int getTotalRanked() { return totalRanked; }
    public void setTotalRanked(int totalRanked) { this.totalRanked = totalRanked; }
    public Integer getTopTrustScore() { return topTrustScore; }
    public void setTopTrustScore(Integer topTrustScore) { this.topTrustScore = topTrustScore; }
    public long getTookMs() { return tookMs; }
    public void setTookMs(long tookMs) { this.tookMs = tookMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
