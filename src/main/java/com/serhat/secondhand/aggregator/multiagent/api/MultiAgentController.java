package com.serhat.secondhand.aggregator.multiagent.api;

import com.serhat.secondhand.aggregator.multiagent.MultiAgentOrchestrator;
import com.serhat.secondhand.aggregator.multiagent.domain.MultiAgentResult;
import com.serhat.secondhand.aggregator.multiagent.domain.ProductSearchRequest;
import com.serhat.secondhand.core.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Multi-Agent Search REST Controller
 *
 * POST /api/v1/aggregator/smart-search
 *
 * Accepts a rich request with: product name, budget, battery requirement,
 * online/offline mode, and social marketplace toggle.
 * Returns a comprehensive verdict: BUY / CONSIDER / AVOID + full analysis.
 */
@RestController
@RequestMapping("/api/v1/aggregator")
@Tag(name = "Multi-Agent Search", description = "AI multi-agent product advisor: LangChain agent, best deals, scam detection, buy/avoid verdict")
public class MultiAgentController {

    private final MultiAgentOrchestrator orchestrator;
    private final com.serhat.secondhand.aggregator.multiagent.langchain.LangChainReActAgent langChainAgent;

    public MultiAgentController(MultiAgentOrchestrator orchestrator,
                                com.serhat.secondhand.aggregator.multiagent.langchain.LangChainReActAgent langChainAgent) {
        this.orchestrator = orchestrator;
        this.langChainAgent = langChainAgent;
    }

    // ---- Request DTO ----
    public record SmartSearchRequest(
            @NotBlank @Size(max = 300) String query,

            /** Optional explicit product name; if null, derived from query */
            @Size(max = 200) String productName,

            /** Maximum budget in INR */
            Double maxBudget,

            /** Minimum budget in INR */
            Double minBudget,

            /**
             * Minimum battery capacity in mAh (e.g. 5000).
             * Critical for mobile/tablet searches.
             */
            Integer minBatteryMah,

            /** MOBILE, LAPTOP, TABLET, ELECTRONICS, etc. */
            @Size(max = 50) String category,

            @Size(max = 100) String preferredBrand,

            @Size(max = 100) String city,

            /** NEW, LIKE_NEW, GOOD, FAIR */
            @Size(max = 20) String condition,

            /**
             * true = search internet live (DummyJSON, Gemini)
             * false = offline only (bundled datasets)
             */
            Boolean onlineSearch,

            /**
             * true = include WhatsApp groups and Facebook Marketplace listings
             * (OLX is always excluded)
             */
            Boolean includeSocialSources,

            /** Max results to return; default 10, max 30 */
            Integer maxResults
    ) {}

    @PublicEndpoint
    @PostMapping("/smart-search")
    @Operation(
            summary = "Multi-agent smart product search",
            description = "Runs 4 specialist AI agents in parallel: PopularProductsAgent (best products online), "
                    + "WhatsAppFacebookAgent (community deals), ScamDetectorAgent (fraud detection), "
                    + "BuyAdvisorAgent (final BUY/CONSIDER/AVOID verdict with Gemini narrative). "
                    + "OLX is permanently excluded. Supports battery filtering and online/offline modes."
    )
    public ResponseEntity<MultiAgentResult> smartSearch(@Valid @RequestBody SmartSearchRequest req) {
        ProductSearchRequest searchRequest = new ProductSearchRequest(
                req.query(),
                req.productName() != null ? req.productName() : req.query(),
                req.maxBudget(),
                req.minBudget(),
                req.minBatteryMah(),
                req.category(),
                req.preferredBrand(),
                req.city(),
                req.condition(),
                Boolean.TRUE.equals(req.onlineSearch()),
                req.includeSocialSources() == null || req.includeSocialSources(), // default true
                true, // always exclude OLX
                req.maxResults() != null ? req.maxResults() : 10
        );

        MultiAgentResult result = orchestrator.orchestrate(searchRequest);
        return ResponseEntity.ok(result);
    }

    /** Quick GET endpoint for simple searches (convenience) */
    @PublicEndpoint
    @GetMapping("/smart-search")
    @Operation(summary = "Quick smart search via GET parameters")
    public ResponseEntity<MultiAgentResult> smartSearchGet(
            @RequestParam @NotBlank @Size(max = 300) String query,
            @RequestParam(required = false) Double maxBudget,
            @RequestParam(required = false) Integer minBatteryMah,
            @RequestParam(defaultValue = "true") boolean online,
            @RequestParam(defaultValue = "true") boolean social,
            @RequestParam(defaultValue = "10") int limit) {

        ProductSearchRequest searchRequest = new ProductSearchRequest(
                query, null, maxBudget, null, minBatteryMah,
                null, null, null, null,
                online, social, true, limit);

        MultiAgentResult result = orchestrator.orchestrate(searchRequest);
        return ResponseEntity.ok(result);
    }

    /**
     * LangChain ReAct Agent endpoint
     * Executes the ReAct reasoning loop (Thought -> Action -> Observation -> Final Answer)
     * using live internet tools, community sources, scam detection, and spec evaluation.
     */
    @PublicEndpoint
    @GetMapping("/langchain/agent")
    @Operation(summary = "LangChain ReAct Agent Search",
               description = "Runs autonomous LangChain ReAct agent with tools: internet search, community deals (no OLX), scam detector, and spec evaluator.")
    public ResponseEntity<com.serhat.secondhand.aggregator.multiagent.langchain.LangChainAgentResult> langChainAgentGet(
            @RequestParam @NotBlank @Size(max = 300) String query,
            @RequestParam(required = false) Double maxBudget,
            @RequestParam(required = false) Integer minBatteryMah,
            @RequestParam(required = false) String city) {

        com.serhat.secondhand.aggregator.multiagent.langchain.LangChainAgentResult result =
                langChainAgent.runAgent(query, maxBudget, minBatteryMah, city);
        return ResponseEntity.ok(result);
    }

    @PublicEndpoint
    @PostMapping("/langchain/agent")
    @Operation(summary = "LangChain ReAct Agent POST endpoint")
    public ResponseEntity<com.serhat.secondhand.aggregator.multiagent.langchain.LangChainAgentResult> langChainAgentPost(
            @Valid @RequestBody SmartSearchRequest req) {

        com.serhat.secondhand.aggregator.multiagent.langchain.LangChainAgentResult result =
                langChainAgent.runAgent(req.query(), req.maxBudget(), req.minBatteryMah(), req.city());
        return ResponseEntity.ok(result);
    }
}
