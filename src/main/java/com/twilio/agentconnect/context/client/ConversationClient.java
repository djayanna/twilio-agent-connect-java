package com.twilio.agentconnect.context.client;


import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.TacConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Client for Twilio Conversations API.
 */
@Service
public class ConversationClient extends AbstractContextClient {

    private static final Logger log = LoggerFactory.getLogger(ConversationClient.class);
    private static final String BASE_URL = "https://conversations.twilio.com/v1";

    public ConversationClient(WebClient.Builder webClientBuilder,
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
     * Send a message to a conversation.
     *
     * @param conversationId The conversation ID
     * @param messageContent The message content
     * @return Mono containing the outbound message
     */
    public Mono<OutboundMessage> sendMessage(String conversationId, String messageContent) {
        log.debug("Sending message to conversation: {}", conversationId);

        return executeRequest(
            client -> client.post()
                .uri("/Conversations/{conversationSid}/Messages", conversationId)
                .bodyValue(Map.of(
                    "Author", "AI_AGENT",
                    "Body", messageContent
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> OutboundMessage.builder()
                    .content(messageContent)
                    .conversationId(conversationId)
                    .build()),
            () -> null
        );
    }

    /**
     * Get conversation details.
     *
     * @param conversationId The conversation ID
     * @return Mono containing conversation data
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getConversation(String conversationId) {
        log.debug("Getting conversation: {}", conversationId);

        return executeRequest(
            client -> client.get()
                .uri("/Conversations/{conversationSid}", conversationId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m),
            Map::of
        );
    }

    private static String buildAuth(TacConfiguration config) {
        String credentials = config.getAccountSid() + ":" + config.getAuthToken();
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
