package com.twilio.agentconnect.context.client;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.twilio.agentconnect.context.model.ConversationSummary;
import com.twilio.agentconnect.context.model.MemoryResponse;
import com.twilio.agentconnect.context.model.Observation;
import com.twilio.agentconnect.core.MemoryMode;
import com.twilio.agentconnect.core.TacConfiguration;
import com.twilio.agentconnect.session.Session;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for Twilio Conversation Memory API.
 */
@Service
public class MemoryClient extends AbstractContextClient {

    private static final Logger log = LoggerFactory.getLogger(MemoryClient.class);
    private static final String BASE_URL = "https://memory.twilio.com/v1";

    public MemoryClient(WebClient.Builder webClientBuilder,
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
     * Retrieve memory for a profile.
     * Caches result if memory mode is ONCE.
     *
     * @param profileId The profile ID
     * @param session The session
     * @return Mono containing memory response
     */
    @Cacheable(value = "memory",
               key = "#profileId + '-' + #session.id",
               condition = "#root.target.config.memory.mode.name() == 'ONCE'")
    public Mono<MemoryResponse> retrieveMemory(String profileId, Session session) {
        if (config.getMemory().getMode() == MemoryMode.NEVER) {
            return Mono.just(MemoryResponse.empty());
        }

        String memoryStoreId = config.getMemory().getStoreId();
        if (memoryStoreId == null || memoryStoreId.isEmpty()) {
            log.debug("No memory store configured, returning empty memory");
            return Mono.just(MemoryResponse.empty());
        }

        // The Memory API has no single "get everything" endpoint. We:
        //   1. resolve the caller's address to a profile (if not already known)
        //   2. fetch the profile's traits
        //   3. recall observations + summaries
        // and assemble a single MemoryResponse for the message handler.
        return resolveProfileId(memoryStoreId, profileId)
            .flatMap(resolvedProfileId -> Mono.zip(
                    fetchTraits(memoryStoreId, resolvedProfileId),
                    recall(memoryStoreId, resolvedProfileId, session))
                .map(tuple -> MemoryResponse.builder()
                    .profileId(resolvedProfileId)
                    .traits(tuple.getT1())
                    .observations(tuple.getT2().observations())
                    .summaries(tuple.getT2().summaries())
                    .build()))
            .switchIfEmpty(Mono.fromSupplier(() -> {
                log.debug("No profile resolved for '{}', returning empty memory", profileId);
                return MemoryResponse.empty();
            }))
            .onErrorResume(error -> {
                log.warn("Failed to retrieve memory, using empty response: {}", error.getMessage());
                return Mono.just(MemoryResponse.empty());
            });
    }

    /**
     * Resolve a Memory profile ID. If the session already has a TTID-format
     * profile ID ({@code mem_profile_...}) it is used directly; otherwise the
     * given value is treated as a raw identifier (e.g. phone number) and looked
     * up via the Profiles/Lookup endpoint.
     */
    private Mono<String> resolveProfileId(String storeId, String profileId) {
        if (profileId != null && profileId.startsWith("mem_profile_")) {
            return Mono.just(profileId);
        }

        String idType = config.getMemory().getIdentifierType();
        Map<String, String> body = Map.of("idType", idType, "value", profileId);

        return webClient.post()
            .uri("/Stores/{storeId}/Profiles/Lookup", storeId)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(LookupResponse.class)
            .flatMap(lookup -> {
                if (lookup.profiles() == null || lookup.profiles().isEmpty()) {
                    return Mono.empty();
                }
                // Note: we intentionally do NOT overwrite session.profileId here.
                // The session keeps the original lookup identifier so the ONCE
                // cache key stays stable across turns. The resolved profile ID is
                // surfaced on MemoryResponse.profileId for handlers that need it.
                String resolved = lookup.profiles().get(0);
                log.debug("Resolved {} '{}' to profile {}", idType, profileId, resolved);
                return Mono.just(resolved);
            });
    }

    /**
     * Fetch the profile's traits (grouped map). Returns an empty map on failure
     * so memory retrieval degrades gracefully.
     */
    private Mono<Map<String, Object>> fetchTraits(String storeId, String profileId) {
        List<String> traitGroups = config.getMemory().getTraitGroups();
        return webClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path("/Stores/{storeId}/Profiles/{profileId}");
                if (traitGroups != null && !traitGroups.isEmpty()) {
                    uriBuilder.queryParam("traitGroups", String.join(",", traitGroups));
                }
                return uriBuilder.build(storeId, profileId);
            })
            .retrieve()
            .bodyToMono(ProfileResponse.class)
            .map(profile -> profile.traits() != null ? profile.traits() : new HashMap<String, Object>())
            .onErrorResume(error -> {
                log.warn("Failed to fetch traits for profile {}: {}", profileId, error.getMessage());
                return Mono.just(new HashMap<>());
            });
    }

    /**
     * Recall observations and summaries for the profile.
     */
    private Mono<RecallResponse> recall(String storeId, String profileId, Session session) {
        Map<String, Object> body = new HashMap<>();
        body.put("observationsLimit", config.getMemory().getObservationsLimit());
        body.put("summariesLimit", config.getMemory().getSummariesLimit());
        // conversationId enables query expansion; include it when available.
        if (session != null && session.getConversationId() != null
                && session.getConversationId().startsWith("conv_conversation_")) {
            body.put("conversationId", session.getConversationId());
        }

        return webClient.post()
            .uri("/Stores/{storeId}/Profiles/{profileId}/Recall", storeId, profileId)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(RecallResponse.class)
            .map(recall -> new RecallResponse(
                recall.observations() != null ? recall.observations() : new ArrayList<>(),
                recall.summaries() != null ? recall.summaries() : new ArrayList<>()))
            .onErrorResume(error -> {
                log.warn("Failed to recall memory for profile {}: {}", profileId, error.getMessage());
                return Mono.just(new RecallResponse(new ArrayList<>(), new ArrayList<>()));
            });
    }

    /** Response of {@code POST /Stores/{id}/Profiles/Lookup}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LookupResponse(String normalizedValue, List<String> profiles) {}

    /** Response of {@code GET /Stores/{id}/Profiles/{id}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProfileResponse(String id, Map<String, Object> traits) {}

    /** Response of {@code POST /Stores/{id}/Profiles/{id}/Recall}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RecallResponse(List<Observation> observations, List<ConversationSummary> summaries) {}

    /**
     * Create an observation for a profile.
     *
     * @param profileId The profile ID
     * @param observation The observation to create
     * @return Mono signaling completion
     */
    public Mono<Void> createObservation(String profileId, Observation observation) {
        String memoryStoreId = config.getMemory().getStoreId();
        if (memoryStoreId == null || memoryStoreId.isEmpty()) {
            return Mono.empty();
        }

        log.debug("Creating observation for profile: {}", profileId);

        return executeRequest(
            client -> client.post()
                .uri("/Stores/{memoryStoreId}/Profiles/{profileId}/Observations",
                     memoryStoreId, profileId)
                .bodyValue(observation)
                .retrieve()
                .bodyToMono(Void.class),
            () -> null
        );
    }

    /**
     * Create a conversation summary for a profile.
     *
     * @param profileId The profile ID
     * @param summary The summary text
     * @return Mono signaling completion
     */
    public Mono<Void> createSummary(String profileId, String summary) {
        String memoryStoreId = config.getMemory().getStoreId();
        if (memoryStoreId == null || memoryStoreId.isEmpty()) {
            return Mono.empty();
        }

        log.debug("Creating summary for profile: {}", profileId);

        return executeRequest(
            client -> client.post()
                .uri("/Stores/{memoryStoreId}/Profiles/{profileId}/ConversationSummaries",
                     memoryStoreId, profileId)
                .bodyValue(java.util.Map.of("summaries", java.util.List.of(
                    java.util.Map.of("content", summary))))
                .retrieve()
                .bodyToMono(Void.class),
            () -> null
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
