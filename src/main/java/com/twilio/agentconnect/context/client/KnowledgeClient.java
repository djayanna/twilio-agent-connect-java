package com.twilio.agentconnect.context.client;


import com.twilio.agentconnect.core.TacConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Client for Twilio Knowledge Store API.
 */
@Service
public class KnowledgeClient extends AbstractContextClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeClient.class);
    private static final String BASE_URL = "https://knowledge.twilio.com/v1";

    public KnowledgeClient(WebClient.Builder webClientBuilder,
                          TacConfiguration config,
                          CircuitBreaker circuitBreaker) {
        super(
            webClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", buildAuth(config))
                .defaultHeader("User-Agent", buildUserAgent(config))
                .build(),
            config,
            circuitBreaker
        );
    }

    /**
     * Search knowledge store.
     *
     * @param query The search query
     * @param limit Maximum number of results
     * @return Mono containing search results
     */
    public Mono<List<Map<String, Object>>> search(String query, int limit) {
        log.debug("Searching knowledge store: {}", query);

        return executeRequest(
            client -> client.post()
                .uri("/KnowledgeStores/search")
                .bodyValue(Map.of(
                    "query", query,
                    "limit", limit
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (List<Map<String, Object>>) response.get("results")),
            List::of
        );
    }

    private static String buildAuth(TacConfiguration config) {
        String credentials = config.getApiKey() + ":" + config.getApiSecret();
        return "Basic " + java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes());
    }

    private static String buildUserAgent(TacConfiguration config) {
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        return String.format("twilio-agent-connect-java/0.1.0 java/%s os/%s",
                           javaVersion, osName);
    }
}
