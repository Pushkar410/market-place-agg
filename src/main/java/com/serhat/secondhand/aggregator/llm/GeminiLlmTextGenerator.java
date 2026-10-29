package com.serhat.secondhand.aggregator.llm;

import com.serhat.secondhand.aggregator.intent.GeminiIntentParser;
import com.serhat.secondhand.ai.application.GeminiClient;
import org.springframework.stereotype.Component;

/** Production implementation: the platform's existing Gemini client, active only when a real key is configured. */
@Component
public class GeminiLlmTextGenerator implements LlmTextGenerator {

    private final GeminiIntentParser availability;
    private final GeminiClient client;

    public GeminiLlmTextGenerator(GeminiIntentParser availability, GeminiClient client) {
        this.availability = availability;
        this.client = client;
    }

    @Override
    public boolean isAvailable() {
        return availability.isAvailable();
    }

    @Override
    public String generate(String prompt) {
        return client.generateText(prompt);
    }
}
