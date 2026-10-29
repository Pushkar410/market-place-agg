package com.serhat.secondhand.aggregator.community;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A listing a person found on another platform (a WhatsApp group, a notice board, a marketplace post...) and shared
 * with the aggregator. This is the permitted, non-scraping way real-world listings enter the system: the user supplies
 * the data, we store only what was shared and a link back to the original post.
 */
@Entity
@Table(name = "aggregator_community_listings", indexes = {
        @Index(name = "idx_agg_community_created", columnList = "created_at")
})
public class CommunityListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "price")
    private Double price;

    @Column(name = "city", length = 60)
    private String city;

    /** Optional hint such as ELECTRONICS; the normaliser infers it from the text when absent. */
    @Column(name = "category", length = 30)
    private String category;

    /** Where the poster found it, e.g. "OLX" or "College WhatsApp group". */
    @Column(name = "origin_name", length = 60)
    private String originName;

    @Column(name = "origin_url", length = 300)
    private String originUrl;

    @Column(name = "contact_name", length = 60)
    private String contactName;

    /** Whether a way to contact the seller exists (the number itself is deliberately not stored). */
    @Column(name = "contact_provided", nullable = false)
    private boolean contactProvided;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getOriginName() { return originName; }
    public void setOriginName(String originName) { this.originName = originName; }
    public String getOriginUrl() { return originUrl; }
    public void setOriginUrl(String originUrl) { this.originUrl = originUrl; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public boolean isContactProvided() { return contactProvided; }
    public void setContactProvided(boolean contactProvided) { this.contactProvided = contactProvided; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
