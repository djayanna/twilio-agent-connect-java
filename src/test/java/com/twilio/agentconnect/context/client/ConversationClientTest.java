package com.twilio.agentconnect.context.client;

import com.twilio.agentconnect.context.model.OutboundMessage;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConversationClient}.
 */
class ConversationClientTest {

    private MockWebServer server;
    private TacConfiguration config;
    private ConversationClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        config = new TacConfiguration();
        config.setAccountSid("ACtest");
        config.setAuthToken("token");
        config.setApiKey("SKtest");
        config.setApiSecret("secret");
        // No retries so a single error response triggers the fallback immediately.
        config.getResilience().getRetry().setMaxAttempts(0);

        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test");
        client = new ConversationClient(
            MemoryClientTest.redirectingBuilder(server), config, circuitBreaker);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void sendMessage_postsAuthorAndBodyAndReturnsOutboundMessage() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(201)
            .setHeader("Content-Type", "application/json")
            .setBody("{ \"sid\": \"IMxxxx\", \"body\": \"Hi there\" }"));

        StepVerifier.create(client.sendMessage("CHxxxx", "Hi there"))
            .assertNext((OutboundMessage msg) -> {
                assertThat(msg.getContent()).isEqualTo("Hi there");
                assertThat(msg.getConversationId()).isEqualTo("CHxxxx");
            })
            .verifyComplete();

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/v1/Conversations/CHxxxx/Messages");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"Author\":\"AI_AGENT\"", "\"Body\":\"Hi there\"");
    }

    @Test
    void sendMessage_errorFallsBackToEmptyMono() {
        // Server error -> executeRequest fallback supplier returns null -> empty Mono.
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(client.sendMessage("CHxxxx", "Hi there"))
            .verifyComplete();
    }

    @Test
    void getConversation_returnsParsedMap() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{ \"sid\": \"CHxxxx\", \"state\": \"active\", \"friendly_name\": \"Support\" }"));

        StepVerifier.create(client.getConversation("CHxxxx"))
            .assertNext((Map<String, Object> conv) -> {
                assertThat(conv).containsEntry("sid", "CHxxxx");
                assertThat(conv).containsEntry("state", "active");
                assertThat(conv).containsEntry("friendly_name", "Support");
            })
            .verifyComplete();

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/v1/Conversations/CHxxxx");
    }

    @Test
    void getConversation_errorFallsBackToEmptyMap() {
        server.enqueue(new MockResponse().setResponseCode(503));

        StepVerifier.create(client.getConversation("CHxxxx"))
            .assertNext((Map<String, Object> conv) -> assertThat(conv).isEmpty())
            .verifyComplete();
    }

    @Test
    void getConfig_returnsConfiguration() {
        assertThat(client.getConfig()).isSameAs(config);
    }
}
