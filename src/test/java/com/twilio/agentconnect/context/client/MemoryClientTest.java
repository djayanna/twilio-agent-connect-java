package com.twilio.agentconnect.context.client;

import com.twilio.agentconnect.core.MemoryMode;
import com.twilio.agentconnect.core.TacConfiguration;
import com.twilio.agentconnect.session.Session;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MemoryClient}.
 *
 * <p>The client hardcodes a Twilio base URL in its constructor. To exercise the
 * real request/response wiring against {@link MockWebServer} we supply a
 * {@link WebClient.Builder} carrying an {@link ExchangeFilterFunction} that
 * rewrites the scheme/host/port of every outgoing request to the mock server
 * while preserving the path and query string the client built (note the real
 * {@code /v1} base path is therefore retained in the recorded request paths).
 *
 * <p>{@code retrieveMemory} issues the traits ({@code GET}) and recall
 * ({@code POST}) calls concurrently via {@code Mono.zip}, so their arrival order
 * is non-deterministic. Tests that exercise that flow use a path-routing
 * {@link Dispatcher} rather than FIFO-enqueued responses.
 */
class MemoryClientTest {

    private static final String STORE = "US_store_123";

    private MockWebServer server;
    private TacConfiguration config;
    private CircuitBreaker circuitBreaker;
    private MemoryClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        config = new TacConfiguration();
        config.setAccountSid("ACtest");
        config.setAuthToken("token");
        config.setApiKey("SKtest");
        config.setApiSecret("secret");
        // Keep deterministic; retrieveMemory does not retry, but createObservation/createSummary do.
        config.getResilience().getRetry().setMaxAttempts(0);

        TacConfiguration.MemoryConfig memory = new TacConfiguration.MemoryConfig();
        memory.setStoreId(STORE);
        memory.setMode(MemoryMode.ALWAYS);
        memory.setIdentifierType("phone");
        memory.setObservationsLimit(20);
        memory.setSummariesLimit(5);
        config.setMemory(memory);

        circuitBreaker = CircuitBreaker.ofDefaults("test");
        client = new MemoryClient(redirectingBuilder(server), config, circuitBreaker);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    /**
     * Build a WebClient.Builder whose filter rewrites the host/port of every
     * request to the MockWebServer, so the client's hardcoded baseUrl is
     * transparently redirected. The path (including the real {@code /v1} prefix)
     * is preserved.
     */
    static WebClient.Builder redirectingBuilder(MockWebServer server) {
        URI base = server.url("/").uri();
        ExchangeFilterFunction redirect = ExchangeFilterFunction.ofRequestProcessor(req -> {
            URI original = req.url();
            URI rewritten = URI.create(base.getScheme() + "://" + base.getAuthority()
                + original.getRawPath()
                + (original.getRawQuery() != null ? "?" + original.getRawQuery() : ""));
            return Mono.just(ClientRequest.from(req).url(rewritten).build());
        });
        return WebClient.builder().filter(redirect);
    }

    private Session session(String conversationId) {
        return Session.builder()
            .id("sess-1")
            .conversationId(conversationId)
            .build();
    }

    // ----------------------------------------------------------------------
    // Short-circuit paths (no network calls)
    // ----------------------------------------------------------------------

    @Test
    void retrieveMemory_shortCircuitsWhenModeNever() {
        config.getMemory().setMode(MemoryMode.NEVER);

        StepVerifier.create(client.retrieveMemory("+15551234567", session(null)))
            .assertNext(resp -> assertThat(resp.isEmpty()).isTrue())
            .verifyComplete();

        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void retrieveMemory_shortCircuitsWhenStoreIdBlank() {
        config.getMemory().setStoreId("");

        StepVerifier.create(client.retrieveMemory("+15551234567", session(null)))
            .assertNext(resp -> assertThat(resp.isEmpty()).isTrue())
            .verifyComplete();

        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void retrieveMemory_shortCircuitsWhenStoreIdNull() {
        config.getMemory().setStoreId(null);

        StepVerifier.create(client.retrieveMemory("+15551234567", session(null)))
            .assertNext(resp -> assertThat(resp.isEmpty()).isTrue())
            .verifyComplete();

        assertThat(server.getRequestCount()).isZero();
    }

    // ----------------------------------------------------------------------
    // Full 3-call flow (lookup -> traits + recall)
    // ----------------------------------------------------------------------

    @Test
    void retrieveMemory_happyPath_resolvesLookupTraitsAndRecall() throws InterruptedException {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.endsWith("/Profiles/Lookup")) {
                    return jsonResponse("""
                        { "normalizedValue": "+15551234567", "profiles": ["mem_profile_abc"] }
                        """);
                }
                if (path != null && path.endsWith("/Recall")) {
                    return jsonResponse("""
                        {
                          "observations": [
                            {
                              "id": "obs_1",
                              "content": "Prefers email contact",
                              "score": 0.92,
                              "source": "memory",
                              "conversationIds": ["conv_conversation_1", "conv_conversation_2"]
                            }
                          ],
                          "summaries": [
                            {
                              "id": "sum_1",
                              "content": "Customer called about billing",
                              "score": 0.55,
                              "conversationId": "conv_conversation_1"
                            }
                          ]
                        }
                        """);
                }
                // GET traits
                return jsonResponse("""
                    {
                      "id": "mem_profile_abc",
                      "traits": { "identity": { "firstName": "Ada", "tier": "gold" } }
                    }
                    """);
            }
        });

        StepVerifier.create(client.retrieveMemory("+15551234567", session("conv_conversation_1")))
            .assertNext(resp -> {
                assertThat(resp.getProfileId()).isEqualTo("mem_profile_abc");

                assertThat(resp.getTraits()).containsKey("identity");

                assertThat(resp.getObservations()).hasSize(1);
                assertThat(resp.getObservations().get(0).getId()).isEqualTo("obs_1");
                assertThat(resp.getObservations().get(0).getContent()).isEqualTo("Prefers email contact");
                assertThat(resp.getObservations().get(0).getScore()).isEqualTo(0.92);
                assertThat(resp.getObservations().get(0).getSource()).isEqualTo("memory");
                assertThat(resp.getObservations().get(0).getConversationIds())
                    .containsExactly("conv_conversation_1", "conv_conversation_2");

                assertThat(resp.getSummaries()).hasSize(1);
                assertThat(resp.getSummaries().get(0).getContent()).isEqualTo("Customer called about billing");
                assertThat(resp.getSummaries().get(0).getSummary()).isEqualTo("Customer called about billing");
                assertThat(resp.getSummaries().get(0).getScore()).isEqualTo(0.55);
                assertThat(resp.getSummaries().get(0).getConversationId()).isEqualTo("conv_conversation_1");
            })
            .verifyComplete();

        // 3 calls were made (lookup, traits, recall).
        assertThat(server.getRequestCount()).isEqualTo(3);

        // The lookup request carries the idType + value body.
        RecordedRequest lookup = findRequest("/Profiles/Lookup");
        assertThat(lookup.getMethod()).isEqualTo("POST");
        assertThat(lookup.getPath()).isEqualTo("/v1/Stores/" + STORE + "/Profiles/Lookup");
        assertThat(lookup.getBody().readUtf8())
            .contains("\"value\":\"+15551234567\"", "\"idType\":\"phone\"");

        RecordedRequest recall = findRequest("/Recall");
        assertThat(recall.getMethod()).isEqualTo("POST");
        assertThat(recall.getPath())
            .isEqualTo("/v1/Stores/" + STORE + "/Profiles/mem_profile_abc/Recall");
        String recallBody = recall.getBody().readUtf8();
        assertThat(recallBody).contains("\"observationsLimit\":20", "\"summariesLimit\":5");
        // conversationId is included because the session carries a conv_conversation_ id.
        assertThat(recallBody).contains("\"conversationId\":\"conv_conversation_1\"");
    }

    @Test
    void retrieveMemory_skipsLookupWhenProfileIdAlreadyResolved() {
        // A profileId already in mem_profile_ form must NOT trigger a Lookup call.
        server.setDispatcher(routingDispatcher(
            jsonResponse("""
                { "id": "mem_profile_direct", "traits": { "g": { "k": "v" } } }
                """),
            jsonResponse("""
                { "observations": [], "summaries": [] }
                """)));

        StepVerifier.create(client.retrieveMemory("mem_profile_direct", session(null)))
            .assertNext(resp -> {
                assertThat(resp.getProfileId()).isEqualTo("mem_profile_direct");
                assertThat(resp.getTraits()).containsKey("g");
            })
            .verifyComplete();

        // Only traits + recall, no lookup.
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void retrieveMemory_emptyWhenLookupReturnsNoProfiles() {
        server.enqueue(jsonResponse("""
            { "normalizedValue": "+15551234567", "profiles": [] }
            """));

        StepVerifier.create(client.retrieveMemory("+15551234567", session(null)))
            .assertNext(resp -> assertThat(resp.isEmpty()).isTrue())
            .verifyComplete();
    }

    @Test
    void retrieveMemory_traitsFailureDegradesToEmptyTraitsButKeepsRecall() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.endsWith("/Profiles/Lookup")) {
                    return jsonResponse("{ \"profiles\": [\"mem_profile_xyz\"] }");
                }
                if (path != null && path.endsWith("/Recall")) {
                    return jsonResponse("""
                        { "observations": [ { "id": "o1", "content": "note" } ], "summaries": [] }
                        """);
                }
                // Traits GET fails -> degrades to empty map.
                return new MockResponse().setResponseCode(500);
            }
        });

        StepVerifier.create(client.retrieveMemory("+15551234567", session(null)))
            .assertNext(resp -> {
                assertThat(resp.getProfileId()).isEqualTo("mem_profile_xyz");
                assertThat(resp.getTraits()).isEmpty();
                assertThat(resp.getObservations()).hasSize(1);
                assertThat(resp.getObservations().get(0).getContent()).isEqualTo("note");
                assertThat(resp.getSummaries()).isEmpty();
            })
            .verifyComplete();
    }

    @Test
    void retrieveMemory_recallFailureDegradesToEmptyObservationsAndSummaries() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.endsWith("/Profiles/Lookup")) {
                    return jsonResponse("{ \"profiles\": [\"mem_profile_xyz\"] }");
                }
                if (path != null && path.endsWith("/Recall")) {
                    // Recall fails -> degrades to empty lists.
                    return new MockResponse().setResponseCode(503);
                }
                return jsonResponse("""
                    { "id": "mem_profile_xyz", "traits": { "g": { "k": "v" } } }
                    """);
            }
        });

        StepVerifier.create(client.retrieveMemory("+15551234567", session(null)))
            .assertNext(resp -> {
                assertThat(resp.getProfileId()).isEqualTo("mem_profile_xyz");
                assertThat(resp.getTraits()).containsKey("g");
                assertThat(resp.getObservations()).isEmpty();
                assertThat(resp.getSummaries()).isEmpty();
            })
            .verifyComplete();
    }

    @Test
    void retrieveMemory_lookupFailureFallsBackToEmpty() {
        // Lookup fails -> the whole flow falls back to empty memory.
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(client.retrieveMemory("+15551234567", session(null)))
            .assertNext(resp -> assertThat(resp.isEmpty()).isTrue())
            .verifyComplete();
    }

    @Test
    void retrieveMemory_omitsConversationIdWhenSessionHasNonConversationId() throws InterruptedException {
        server.setDispatcher(routingDispatcher(
            jsonResponse("{ \"id\": \"mem_profile_q\", \"traits\": {} }"),
            jsonResponse("{ \"observations\": [], \"summaries\": [] }")));

        StepVerifier.create(client.retrieveMemory("mem_profile_q", session("CHnotaconv")))
            .assertNext(resp -> assertThat(resp.getProfileId()).isEqualTo("mem_profile_q"))
            .verifyComplete();

        RecordedRequest recall = findRequest("/Recall");
        assertThat(recall.getBody().readUtf8()).doesNotContain("conversationId");
    }

    // ----------------------------------------------------------------------
    // createObservation / createSummary
    // ----------------------------------------------------------------------

    @Test
    void createObservation_shortCircuitsWhenStoreIdBlank() {
        config.getMemory().setStoreId("");
        StepVerifier.create(client.createObservation("mem_profile_a",
                new com.twilio.agentconnect.context.model.Observation()))
            .verifyComplete();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void createObservation_postsToObservationsEndpoint() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(201));

        com.twilio.agentconnect.context.model.Observation obs =
            new com.twilio.agentconnect.context.model.Observation();
        obs.setContent("Customer is happy");

        StepVerifier.create(client.createObservation("mem_profile_a", obs))
            .verifyComplete();

        RecordedRequest req = takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath())
            .isEqualTo("/v1/Stores/" + STORE + "/Profiles/mem_profile_a/Observations");
        assertThat(req.getBody().readUtf8()).contains("Customer is happy");
    }

    @Test
    void createSummary_shortCircuitsWhenStoreIdNull() {
        config.getMemory().setStoreId(null);
        StepVerifier.create(client.createSummary("mem_profile_a", "a summary"))
            .verifyComplete();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void createSummary_postsToConversationSummariesEndpoint() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(201));

        StepVerifier.create(client.createSummary("mem_profile_a", "a summary"))
            .verifyComplete();

        RecordedRequest req = takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath())
            .isEqualTo("/v1/Stores/" + STORE + "/Profiles/mem_profile_a/ConversationSummaries");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("summaries", "content", "a summary");
    }

    @Test
    void getConfig_returnsConfiguration() {
        assertThat(client.getConfig()).isSameAs(config);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    /** Dispatcher routing the recall POST by path and everything else to {@code traits}. */
    private Dispatcher routingDispatcher(MockResponse traits, MockResponse recall) {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.endsWith("/Recall")) {
                    return recall;
                }
                return traits;
            }
        };
    }

    /** Drain recorded requests until one whose path ends with {@code suffix} is found. */
    private RecordedRequest findRequest(String suffix) throws InterruptedException {
        int count = server.getRequestCount();
        for (int i = 0; i < count; i++) {
            RecordedRequest req = takeRequest();
            if (req != null && req.getPath() != null && req.getPath().endsWith(suffix)) {
                return req;
            }
        }
        throw new AssertionError("No recorded request ending with " + suffix);
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        return server.takeRequest(5, TimeUnit.SECONDS);
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body);
    }
}
