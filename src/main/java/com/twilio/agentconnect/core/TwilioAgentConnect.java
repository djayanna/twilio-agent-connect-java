package com.twilio.agentconnect.core;


import com.twilio.agentconnect.callback.CallbackRegistry;
import com.twilio.agentconnect.callback.ConversationEndedCallback;
import com.twilio.agentconnect.callback.MessageReadyCallback;
import com.twilio.agentconnect.callback.MessageStreamCallback;
import com.twilio.agentconnect.context.client.ConversationClient;
import com.twilio.agentconnect.context.client.KnowledgeClient;
import com.twilio.agentconnect.context.client.MemoryClient;
import com.twilio.agentconnect.context.model.InboundMessage;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.MemoryResponse;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.session.Session;
import com.twilio.agentconnect.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Main orchestrator for Twilio Agent Connect SDK.
 * This is the central coordination point for all TAC operations.
 */
@Component

public class TwilioAgentConnect {

    private static final Logger log = LoggerFactory.getLogger(TwilioAgentConnect.class);

    private final TacConfiguration config;
    private final MemoryClient memoryClient;
    private final ConversationClient conversationClient;
    private final KnowledgeClient knowledgeClient;
    private final SessionManager sessionManager;
    private final CallbackRegistry callbackRegistry;

    public TwilioAgentConnect(TacConfiguration config,
                              MemoryClient memoryClient,
                              ConversationClient conversationClient,
                              KnowledgeClient knowledgeClient,
                              SessionManager sessionManager,
                              CallbackRegistry callbackRegistry) {
        this.config = config;
        this.memoryClient = memoryClient;
        this.conversationClient = conversationClient;
        this.knowledgeClient = knowledgeClient;
        this.sessionManager = sessionManager;
        this.callbackRegistry = callbackRegistry;
    }

    /**
     * Register a callback for when a message is ready to be processed.
     *
     * @param callback The callback to handle messages
     */
    public void onMessageReady(MessageReadyCallback callback) {
        callbackRegistry.onMessageReady(callback);
    }

    /**
     * Register a streaming callback invoked when a message is ready. The response
     * is emitted token-by-token; streaming-capable channels (Voice) forward each
     * token as it arrives. Takes precedence over {@link #onMessageReady} on those
     * channels.
     *
     * @param callback The streaming callback
     */
    public void onMessageStream(MessageStreamCallback callback) {
        callbackRegistry.onMessageStream(callback);
    }

    /**
     * Register a callback for when a conversation ends.
     *
     * @param callback The callback to handle conversation end
     */
    public void onConversationEnded(ConversationEndedCallback callback) {
        callbackRegistry.onConversationEnded(callback);
    }

    /**
     * Process an inbound message through the TAC pipeline.
     *
     * @param channelType The channel type
     * @param message The inbound message
     * @param webhookParams Additional webhook parameters
     * @return A Mono containing the message context
     */
    public Mono<MessageContext> processInboundMessage(
            ChannelType channelType,
            InboundMessage message,
            Map<String, String> webhookParams) {

        log.debug("Processing inbound message from {} on channel {}",
                  message.getFrom(), channelType);

        return sessionManager.getOrCreate(message.getConversationId(), channelType)
            .flatMap(session -> retrieveMemoryForSession(message, session)
                .map(memory -> buildMessageContext(message, memory, session, channelType))
            );
    }

    /**
     * Handle a message context by invoking the registered callback.
     *
     * @param context The message context
     * @return A Mono containing the outbound message
     */
    public Mono<OutboundMessage> handleMessageContext(MessageContext context) {
        if (!callbackRegistry.hasMessageReadyCallback()) {
            log.warn("No message ready callback registered");
            return Mono.empty();
        }

        return callbackRegistry.getMessageReadyCallback()
            .onMessageReady(context)
            .doOnSuccess(response -> log.debug("Generated response for conversation: {}",
                                               context.getConversationId()))
            .doOnError(error -> log.error("Error handling message", error));
    }

    /**
     * Whether a streaming message callback is registered.
     */
    public boolean hasMessageStreamCallback() {
        return callbackRegistry.hasMessageStreamCallback();
    }

    /**
     * Handle a message context by invoking the registered streaming callback.
     * The callback pushes the response itself (e.g. via
     * {@code VoiceChannel.sendResponse}); this returns when that push completes.
     *
     * @param context The message context
     * @return A Mono that completes when the streamed response has been pushed
     *         (empty if no stream callback registered)
     */
    public Mono<Void> handleMessageContextStream(MessageContext context) {
        if (!callbackRegistry.hasMessageStreamCallback()) {
            log.warn("No message stream callback registered");
            return Mono.empty();
        }

        return callbackRegistry.getMessageStreamCallback()
            .onMessageStream(context)
            .doOnSuccess(v -> log.debug("Completed streamed response for conversation: {}",
                                        context.getConversationId()))
            .doOnError(error -> log.error("Error handling streamed message", error));
    }

    /**
     * Send an outbound message through a specific channel.
     *
     * @param conversationId The conversation ID
     * @param message The message content
     * @param channelType The channel type
     * @return A Mono containing the outbound message
     */
    public Mono<OutboundMessage> sendMessage(
            String conversationId,
            String message,
            ChannelType channelType) {

        return conversationClient.sendMessage(conversationId, message)
            .doOnSuccess(msg -> log.debug("Sent message to conversation: {}", conversationId))
            .doOnError(error -> log.error("Error sending message", error));
    }

    /**
     * Handle conversation end event.
     *
     * @param conversationId The conversation ID
     * @return A Mono signaling completion
     */
    public Mono<Void> handleConversationEnded(String conversationId) {
        return sessionManager.get(conversationId)
            .flatMap(session -> {
                if (callbackRegistry.hasConversationEndedCallback()) {
                    return callbackRegistry.getConversationEndedCallback()
                        .onConversationEnded(session);
                }
                return Mono.empty();
            })
            .then(sessionManager.delete(conversationId))
            .doOnSuccess(v -> log.debug("Handled conversation end: {}", conversationId))
            .doOnError(error -> log.error("Error handling conversation end", error));
    }

    /**
     * Retrieve memory for a session based on configuration.
     */
    private Mono<MemoryResponse> retrieveMemoryForSession(InboundMessage message, Session session) {
        if (config.getMemory().getMode() == MemoryMode.NEVER) {
            log.debug("Memory mode is NEVER, skipping memory retrieval");
            return Mono.just(MemoryResponse.empty());
        }

        // Identity resolution: prefer an already-resolved profile ID on the
        // session, otherwise fall back to the caller's address as the lookup
        // identifier. MemoryClient.retrieveMemory resolves it to a profile via
        // the Profiles/Lookup endpoint when it isn't already a mem_profile_ ID.
        String identifier = session.getProfileId();
        if (identifier == null) {
            identifier = message.getFrom();
        }

        return memoryClient.retrieveMemory(identifier, session)
            .onErrorResume(error -> {
                log.warn("Failed to retrieve memory, using empty response", error);
                return Mono.just(MemoryResponse.empty());
            });
    }

    /**
     * Build a message context from components.
     */
    private MessageContext buildMessageContext(
            InboundMessage message,
            MemoryResponse memory,
            Session session,
            ChannelType channelType) {

        return MessageContext.builder()
            .message(message)
            .memory(memory)
            .session(session)
            .conversationId(message.getConversationId())
            .profileId(session.getProfileId())
            .channelType(channelType)
            .build();
    }
}
