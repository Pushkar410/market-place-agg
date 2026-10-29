package com.serhat.secondhand.aggregator.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the bundled demo datasets as sources.
 *
 * NOTE: OLX is intentionally EXCLUDED from this registry by design.
 * Only trusted community sources are included (Facebook Marketplace,
 * WhatsApp Groups, Local Niche boards).
 *
 * Toggle all simulated sources with: aggregator.simulated-sources.enabled
 */
@Configuration
public class SourceRegistry {

    // OLX is EXCLUDED — not registered as a source bean.

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "aggregator.simulated-sources.enabled", havingValue = "true", matchIfMissing = true)
    public SourceAdapter facebookSource(ObjectMapper mapper) {
        return new SimulatedSourceAdapter("/aggregator/facebook-marketplace.json", mapper);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "aggregator.simulated-sources.enabled", havingValue = "true", matchIfMissing = true)
    public SourceAdapter whatsappSource(ObjectMapper mapper) {
        return new SimulatedSourceAdapter("/aggregator/whatsapp-groups.json", mapper);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "aggregator.simulated-sources.enabled", havingValue = "true", matchIfMissing = true)
    public SourceAdapter localNicheSource(ObjectMapper mapper) {
        return new SimulatedSourceAdapter("/aggregator/local-niche.json", mapper);
    }
}
