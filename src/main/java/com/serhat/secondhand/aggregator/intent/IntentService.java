package com.serhat.secondhand.aggregator.intent;

import com.serhat.secondhand.aggregator.domain.SearchIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Step 1 of the pipeline: UNDERSTAND. LLM first (if configured), rules as fallback. */
@Service
public class IntentService {

    private final GeminiIntentParser llmParser;
    private final RuleBasedIntentParser ruleParser;

    public IntentService(GeminiIntentParser llmParser, RuleBasedIntentParser ruleParser) {
        this.llmParser = llmParser;
        this.ruleParser = ruleParser;
    }

    public SearchIntent understand(String query) {
        return llmParser.parse(query).orElseGet(() -> ruleParser.parse(query));
    }
}
