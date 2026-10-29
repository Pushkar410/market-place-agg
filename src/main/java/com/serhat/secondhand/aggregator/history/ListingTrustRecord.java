package com.serhat.secondhand.aggregator.history;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** The Trust Score computed for one returned listing in one search (the "Trust Scores" store of the architecture). */
@Entity
@Table(name = "aggregator_trust_scores", indexes = {
        @Index(name = "idx_agg_trust_search", columnList = "search_id"),
        @Index(name = "idx_agg_trust_listing", columnList = "listing_key")
})
public class ListingTrustRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "search_id", nullable = false)
    private Long searchId;

    /** Stable id of the normalised listing, e.g. "olx:o7" or "secondhand-internal:<uuid>". */
    @Column(name = "listing_key", nullable = false, length = 200)
    private String listingKey;

    @Column(name = "source_id", length = 100)
    private String sourceId;

    @Column(name = "title", length = 300)
    private String title;

    @Column(name = "price")
    private Double price;

    @Column(name = "result_rank", nullable = false)
    private int resultRank;

    @Column(name = "trust_score", nullable = false)
    private int trustScore;

    @Column(name = "trust_level", nullable = false, length = 10)
    private String trustLevel;

    @Column(name = "final_score", nullable = false)
    private double finalScore;

    /** Risk flags joined with " | ". */
    @Column(name = "risk_flags", length = 2000)
    private String riskFlags;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getSearchId() { return searchId; }
    public void setSearchId(Long searchId) { this.searchId = searchId; }
    public String getListingKey() { return listingKey; }
    public void setListingKey(String listingKey) { this.listingKey = listingKey; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public int getResultRank() { return resultRank; }
    public void setResultRank(int resultRank) { this.resultRank = resultRank; }
    public int getTrustScore() { return trustScore; }
    public void setTrustScore(int trustScore) { this.trustScore = trustScore; }
    public String getTrustLevel() { return trustLevel; }
    public void setTrustLevel(String trustLevel) { this.trustLevel = trustLevel; }
    public double getFinalScore() { return finalScore; }
    public void setFinalScore(double finalScore) { this.finalScore = finalScore; }
    public String getRiskFlags() { return riskFlags; }
    public void setRiskFlags(String riskFlags) { this.riskFlags = riskFlags; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
