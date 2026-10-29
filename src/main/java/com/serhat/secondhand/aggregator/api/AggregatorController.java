package com.serhat.secondhand.aggregator.api;

import com.serhat.secondhand.aggregator.community.CommunityListing;
import com.serhat.secondhand.aggregator.community.CommunityListingService;
import com.serhat.secondhand.aggregator.domain.AggregatorResponse;
import com.serhat.secondhand.aggregator.domain.SourceInfo;
import com.serhat.secondhand.aggregator.history.SearchHistory;
import com.serhat.secondhand.aggregator.history.SearchHistoryService;
import com.serhat.secondhand.aggregator.service.AggregatorService;
import com.serhat.secondhand.core.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/aggregator")
@Tag(name = "Aggregator", description = "Agentic AI trust-aware marketplace aggregator")
public class AggregatorController {

    private final AggregatorService aggregatorService;
    private final SearchHistoryService historyService;
    private final CommunityListingService communityService;

    public AggregatorController(AggregatorService aggregatorService, SearchHistoryService historyService,
                                CommunityListingService communityService) {
        this.aggregatorService = aggregatorService;
        this.historyService = historyService;
        this.communityService = communityService;
    }

    public record SearchRequest(@NotBlank @Size(max = 300) String query, Integer limit) {}

    public record SubmitRequest(
            @NotBlank @Size(min = 5, max = 120) String title,
            @Size(max = 1000) String description,
            @PositiveOrZero Double price,
            @Size(max = 60) String city,
            @Size(max = 30) String category,
            @Size(max = 60) String originName,
            @Pattern(regexp = "^$|^https?://\\S{3,290}$", message = "must be a http(s) link") String originUrl,
            @Size(max = 60) String contactName,
            Boolean contactProvided) {}

    public record HistoryItem(Long id, String query, int totalRanked, Integer topTrustScore, boolean clarificationAsked, LocalDateTime createdAt) {}

    @PublicEndpoint
    @PostMapping("/search")
    public ResponseEntity<AggregatorResponse> search(@Valid @RequestBody SearchRequest request) {
        AggregatorResponse response = aggregatorService.search(request.query(), request.limit());
        historyService.record(response, currentUserEmail()); // best-effort, never fails the search
        return ResponseEntity.ok(response);
    }

    /** Share a listing you found elsewhere so the agent can score and rank it. Public; capped per hour. */
    @PublicEndpoint
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody SubmitRequest r) {
        try {
            CommunityListing saved = communityService.submit(new CommunityListingService.Submission(
                    r.title(), r.description(), r.price(), r.city(), r.category(), r.originName(), r.originUrl(),
                    r.contactName(), Boolean.TRUE.equals(r.contactProvided())));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", saved.getId(),
                    "message", "Thanks! Your listing is now searchable. It is unverified, so its trust score will say so."));
        } catch (CommunityListingService.TooManySubmissionsException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", e.getMessage()));
        }
    }

    /** The signed-in user's own recent searches. Requires authentication (not a public endpoint). */
    @GetMapping("/history")
    public ResponseEntity<List<HistoryItem>> history() {
        String email = currentUserEmail();
        if (email == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<HistoryItem> items = historyService.recentFor(email).stream()
                .map((SearchHistory h) -> new HistoryItem(h.getId(), h.getQueryText(), h.getTotalRanked(), h.getTopTrustScore(),
                        h.isClarificationAsked(), h.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(items);
    }

    @PublicEndpoint
    @GetMapping("/sources")
    public ResponseEntity<List<SourceInfo>> sources() {
        return ResponseEntity.ok(aggregatorService.sources());
    }

    @PublicEndpoint
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "sources", aggregatorService.sources().size()));
    }

    private static String currentUserEmail() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) return null;
        return a.getName();
    }
}
