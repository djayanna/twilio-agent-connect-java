package com.twilio.agentconnect.context.client;

import com.twilio.agentconnect.core.TacConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KnowledgeClient}.
 */
class KnowledgeClientTest {

    private MockWebServer server;
    private TacConfiguration config;
    private KnowledgeClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        config = new TacConfiguration();
        config.setAccountSid("ACtest");
        config.setAuthToken("token");
        config.setApiKey("SKtest");
        config.setApiSecret("secret");
        // No retries so error responses fall back immediately.
        config.getResilience().getRetry().setMaxAttempts(0);

        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test");
        client = new KnowledgeClient(
            MemoryClientTest.redirectingBuilder(server), config, circuitBreaker);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void search_postsQueryAndLimitAndReturnsResults() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "results": [
                    { "id": "doc1", "text": "Return policy is 30 days", "score": 0.9 },
                    { "id": "doc2", "text": "Refunds within 14 days", "score": 0.7 }
                  ]
                }
                """));

        StepVerifier.create(client.search("return policy", 5))
            .assertNext((List<Map<String, Object>> results) -> {
                assertThat(results).hasSize(2);
                assertThat(results.get(0)).containsEntry("id", "doc1");
                assertThat(results.get(0)).containsEntry("text", "Return policy is 30 days");
                assertThat(results.get(1)).containsEntry("id", "doc2");
            })
            .verifyComplete();

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/v1/KnowledgeStores/search");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"query\":\"return policy\"", "\"limit\":5");
    }

    @Test
    void search_errorFallsBackToEmptyList() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(client.search("anything", 3))
            .assertNext((List<Map<String, Object>> results) -> assertThat(results).isEmpty())
            .verifyComplete();
    }

    @Test
    void search_missingResultsKeyFallsBackToEmptyList() {
        // No "results" key -> map(...) yields null -> error -> fallback empty list.
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{ \"other\": [] }"));

        StepVerifier.create(client.search("anything", 3))
            .assertNext((List<Map<String, Object>> results) -> assertThat(results).isEmpty())
            .verifyComplete();
    }

    @Test
    void getConfig_returnsConfiguration() {
        assertThat(client.getConfig()).isSameAs(config);
    }
}
