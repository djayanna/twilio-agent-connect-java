package com.twilio.agentconnect.channels.voice;


import com.twilio.agentconnect.cache.IdempotencyCache;
import com.twilio.agentconnect.channels.AbstractChannel;
import com.twilio.agentconnect.context.model.InboundMessage;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.core.TacConfiguration;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.validation.TwilioSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voice channel implementation with Conversation Relay support.
 */
@Component
@ConditionalOnProperty(prefix = "twilio.agent-connect.voice", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VoiceChannel extends AbstractChannel {

    private static final Logger log = LoggerFactory.getLogger(VoiceChannel.class);

    private final TacConfiguration config;
    private final TwimlGenerator twimlGenerator;
    private final ConversationRelayProtocol relayProtocol;
    private final IdempotencyCache idempotencyCache;

    /**
     * Per-WebSocket call metadata captured from the "setup" message. Subsequent
     * "prompt" messages don't repeat callSid/from/to, so we cache it by session.
     */
    private final Map<String, CallMetadata> callMetadataBySession = new ConcurrentHashMap<>();

    public VoiceChannel(
            TwilioAgentConnect tac,
            TwilioSignatureValidator signatureValidator,
            TacConfiguration config,
            TwimlGenerator twimlGenerator,
            ConversationRelayProtocol relayProtocol,
            IdempotencyCache idempotencyCache) {
        super(tac, signatureValidator);
        this.config = config;
        this.twimlGenerator = twimlGenerator;
        this.relayProtocol = relayProtocol;
        this.idempotencyCache = idempotencyCache;
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.VOICE;
    }

    @Override
    public Mono<MessageContext> processInbound(
            InboundMessage message,
            Map<String, String> webhookParams) {

        String idempotencyToken = webhookParams.get("i-twilio-idempotency-token");

        return idempotencyCache.checkAndSet(idempotencyToken)
            .filter(isNew -> isNew)
            .switchIfEmpty(Mono.defer(() -> {
                log.info("Duplicate voice message detected, skipping processing");
                return Mono.empty();
            }))
            .flatMap(isNew -> tac.processInboundMessage(
                getChannelType(),
                message,
                webhookParams
            ));
    }

    @Override
    public Mono<OutboundMessage> sendMessage(String conversationId, String messageContent) {
        return tac.sendMessage(conversationId, messageContent, getChannelType());
    }

    /**
     * Generate TwiML for incoming voice call.
     */
    public Mono<String> generateTwiml(Map<String, String> params) {
        String callSid = params.get("CallSid");
        log.info("Generating TwiML for call: {}", callSid);

        String websocketUrl = buildWebSocketUrl();
        TacConfiguration.VoiceConfig voiceConfig = config.getVoice();

        return Mono.fromCallable(() ->
            twimlGenerator.generateConnectTwiml(
                websocketUrl,
                config.getConversationConfigurationId(),
                voiceConfig.getVoice(),
                voiceConfig.getLanguage(),
                voiceConfig.getWelcomeGreeting(),
                params)
        );
    }

    /**
     * Handle WebSocket connection for Conversation Relay.
     */
    public Mono<Void> handleWebSocketConnection(
            WebSocketSession session,
            Flux<String> inboundMessages) {

        log.info("WebSocket connection established: {}", session.getId());

        return inboundMessages
            .flatMap(message -> relayProtocol.parseMessage(message)
                .flatMap(relayMessage -> handleRelayMessage(session, relayMessage))
            )
            .then()
            .doOnError(error -> log.error("WebSocket error", error))
            .doFinally(signal -> {
                callMetadataBySession.remove(session.getId());
                log.info("WebSocket connection closed: {}", signal);
            });
    }

    /**
     * Handle individual Conversation Relay message.
     */
    private Mono<Void> handleRelayMessage(
            WebSocketSession session,
            ConversationRelayMessage relayMessage) {

        return switch (relayMessage.getType()) {
            case SETUP -> handleSetup(session, relayMessage);
            case PROMPT -> handlePrompt(session, relayMessage);
            case INTERRUPT -> handleInterrupt(session, relayMessage);
            case MARK -> handleMark(session, relayMessage);
        };
    }

    /**
     * Handle setup message (call metadata).
     *
     * <p>The setup message is the only one that carries callSid/from/to, so we
     * cache it per session for use by later prompt messages.
     */
    private Mono<Void> handleSetup(WebSocketSession session, ConversationRelayMessage message) {
        log.info("Received setup message for call: {}", message.getCallSid());
        callMetadataBySession.put(session.getId(), new CallMetadata(
            message.getCallSid(), message.getFrom(), message.getTo()));
        // Initialize conversation, retrieve memory, etc.
        return Mono.empty();
    }

    /**
     * Handle prompt message (transcribed speech).
     */
    private Mono<Void> handlePrompt(WebSocketSession session, ConversationRelayMessage message) {
        log.info("Received prompt: {}", message.getText());

        // Prompt messages don't repeat call metadata; pull it from the setup cache.
        CallMetadata metadata = callMetadataBySession.getOrDefault(
            session.getId(), CallMetadata.EMPTY);

        InboundMessage inboundMessage = InboundMessage.builder()
            .content(message.getText())
            .channelType(ChannelType.VOICE)
            .conversationId(metadata.callSid())
            .from(metadata.from())
            .to(metadata.to())
            .timestamp(Instant.now())
            .build();

        return processInbound(inboundMessage, Map.of())
            .flatMap(tac::handleMessageContext)
            .flatMap(response -> sendVoiceResponse(session, response))
            .then();
    }

    /**
     * Handle interrupt message (user interrupted agent speech).
     */
    private Mono<Void> handleInterrupt(WebSocketSession session, ConversationRelayMessage message) {
        log.info("Received interrupt");
        // Cancel ongoing TTS, clear queue
        return Mono.empty();
    }

    /**
     * Handle mark message (TTS playback milestone).
     */
    private Mono<Void> handleMark(WebSocketSession session, ConversationRelayMessage message) {
        log.debug("Received mark: {}", message.getMarkName());
        return Mono.empty();
    }

    /**
     * Send voice response over WebSocket.
     */
    private Mono<Void> sendVoiceResponse(WebSocketSession session, OutboundMessage message) {
        String responseJson = relayProtocol.buildResponseMessage(message.getContent());

        return session.send(Mono.just(session.textMessage(responseJson)))
            .doOnSuccess(v -> log.info("Sent voice response"))
            .doOnError(error -> log.error("Error sending voice response", error));
    }

    /**
     * Build WebSocket URL for Conversation Relay.
     */
    private String buildWebSocketUrl() {
        String domain = config.getVoicePublicDomain();
        if (domain == null || domain.isEmpty()) {
            throw new IllegalStateException("Voice public domain not configured");
        }

        // Ensure domain has protocol, default to https://
        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
            domain = "https://" + domain;
        }

        // Convert https:// to wss://, http:// to ws://
        String wssDomain = domain.replace("https://", "wss://").replace("http://", "ws://");
        return wssDomain + "/voice/ws";
    }

    /**
     * Call metadata captured from the Conversation Relay setup message.
     */
    private record CallMetadata(String callSid, String from, String to) {
        static final CallMetadata EMPTY = new CallMetadata(null, null, null);
    }
}
