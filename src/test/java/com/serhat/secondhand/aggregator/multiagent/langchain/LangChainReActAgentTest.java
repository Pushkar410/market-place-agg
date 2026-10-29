package com.serhat.secondhand.aggregator.multiagent.langchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serhat.secondhand.ai.application.GeminiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class LangChainReActAgentTest {

    private LangChainReActAgent agent;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        InternetProductSearchTool internetTool = new InternetProductSearchTool(mapper);
        CommunityDealsSearchTool communityTool = new CommunityDealsSearchTool(mapper);
        ScamDetectorTool scamTool = new ScamDetectorTool(mapper);
        SpecEvaluatorTool specTool = new SpecEvaluatorTool(mapper);

        GeminiClient geminiMock = Mockito.mock(GeminiClient.class);
        when(geminiMock.generateText(anyString())).thenReturn("Recommendation: BUY. Solid choices found across live internet and community markets with verified seller feedback.");

        agent = new LangChainReActAgent(internetTool, communityTool, scamTool, specTool, geminiMock, mapper);
    }

    @Test
    @DisplayName("LangChain agent should search live internet and community for laptops (any product input)")
    void testLaptopSearch() {
        LangChainAgentResult result = agent.runAgent("I want a laptop with 16GB RAM under 50000", 50000.0, null, "Bengaluru");

        assertNotNull(result);
        assertEquals("laptop", result.productSearched());
        assertNotNull(result.verdict());
        assertFalse(result.reActSteps().isEmpty());
        assertEquals(4, result.reActSteps().size(), "Must execute all 4 ReAct tool steps");
        assertFalse(result.topInternetProducts().isEmpty(), "Must find live internet products");
        assertFalse(result.communityDeals().isEmpty(), "Must find community deals");
        assertTrue(result.finalAnswer().length() > 20);
    }

    @Test
    @DisplayName("LangChain agent should evaluate battery requirement for mobile devices")
    void testMobileBatterySearch() {
        LangChainAgentResult result = agent.runAgent("mobile with 6000mAh battery under 25000", 25000.0, 6000, "Tumakuru");

        assertNotNull(result);
        assertNotNull(result.batteryAnalysis());
        assertTrue(result.batteryAnalysis().contains("6000mAh"));
        assertNotNull(result.verdict());
        assertFalse(result.detectedScams().isEmpty(), "Must include scam detection scan");
    }

    @Test
    @DisplayName("LangChain agent should work for non-electronic items like bicycles")
    void testBicycleSearch() {
        LangChainAgentResult result = agent.runAgent("Hero gear bicycle for college commute under 5000", 5000.0, null, "Mysuru");

        assertNotNull(result);
        assertTrue(result.productSearched().contains("cycle") || result.productSearched().contains("hero"));
        assertFalse(result.topInternetProducts().isEmpty());
        assertFalse(result.communityDeals().isEmpty());
    }
}
