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
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

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

    /**
     * Outbound frame sinks, one per active call, keyed by conversationId (callSid).
     * Each holds serialized ConversationRelay JSON frames. The single WebSocket
     * {@code send()} subscribes to the sink's Flux (mapping each JSON string to a
     * text frame); both the inbound loop and out-of-band {@link #sendResponse}
     * pushes emit into it, so all outbound goes through one ordered stream.
     */
    private final Map<String, Sinks.Many<String>> outboundByConversation =
        new ConcurrentHashMap<>();

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

        // One outbound sink per connection holds serialized JSON frames. Both the
        // inbound loop and out-of-band sendResponse() pushes emit into it; the
        // single session.send() below drains it in order, mapping each JSON
        // string to a WebSocket text frame.
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> inboundProcessing = inboundMessages
            .concatMap(message -> relayProtocol.parseMessage(message)
                .flatMap(relayMessage -> handleRelayMessage(session, outbound, relayMessage)))
            .doOnError(error -> log.error("WebSocket inbound error", error))
            .then()
            // When inbound ends, complete the outbound sink so send() can finish.
            .doFinally(signal -> outbound.tryEmitComplete());

        Mono<Void> outboundDelivery = session.send(
            outbound.asFlux().map(session::textMessage));

        return Mono.when(inboundProcessing, outboundDelivery)
            .doOnError(error -> log.error("WebSocket error", error))
            .doFinally(signal -> {
                cleanupSession(session.getId());
                log.info("WebSocket connection closed: {}", signal);
            });
    }

    /**
     * Push a single response frame to an active voice call out-of-band.
     *
     * <p>This is the entry point a {@link com.twilio.agentconnect.callback.MessageStreamCallback}
     * uses to forward LLM tokens to the caller as they arrive: call it once per
     * token with {@code last=false}, then once more with {@code last=true} to
     * signal the end of the response (Conversation Relay requires a terminal
     * frame). Empty non-terminal tokens are skipped.
     *
     * <p>Frames are queued onto the connection's single outbound stream, so calls
     * for the same conversation are delivered to the caller in the order made.
     *
     * @param conversationId the call's conversation ID (callSid)
     * @param token          the text token (ignored for a {@code last=true} terminal frame)
     * @param last           whether this is the final frame of the response
     */
    public void sendResponse(String conversationId, String token, boolean last) {
        Sinks.Many<String> outbound = outboundByConversation.get(conversationId);
        if (outbound == null) {
            log.warn("No active voice connection for conversation {}; dropping frame",
                     conversationId);
            return;
        }

        // Nothing to send for an empty, non-terminal token.
        if (!last && (token == null || token.isEmpty())) {
            return;
        }

        log.debug("➡️ Voice frame (conv {}) last={} token={}", conversationId, last, token);
        emit(outbound, relayProtocol.buildTokenMessage(token, last));
    }

    /**
     * Emit a frame into a connection's outbound sink, serializing concurrent
     * pushes (e.g. an in-flight stream and a new one) with a small busy-spin
     * since unicast sinks reject concurrent emitters.
     */
    private void emit(Sinks.Many<String> sink, String frame) {
        Sinks.EmitResult result;
        while ((result = sink.tryEmitNext(frame)) == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
            Thread.onSpinWait();
        }
        if (result.isFailure()) {
            log.warn("Dropped outbound voice frame: {}", result);
        }
    }

    /**
     * Remove per-connection state for the given WebSocket session id, including
     * any outbound sink registered for its conversation.
     */
    private void cleanupSession(String sessionId) {
        CallMetadata metadata = callMetadataBySession.remove(sessionId);
        if (metadata != null && metadata.callSid() != null) {
            outboundByConversation.remove(metadata.callSid());
        }
    }

    /**
     * Handle individual Conversation Relay message.
     */
    private Mono<Void> handleRelayMessage(
            WebSocketSession session,
            Sinks.Many<String> outbound,
            ConversationRelayMessage relayMessage) {

        return switch (relayMessage.getType()) {
            case SETUP -> handleSetup(session, outbound, relayMessage);
            case PROMPT -> handlePrompt(session, outbound, relayMessage);
            case INTERRUPT -> handleInterrupt(session, relayMessage);
            case MARK -> handleMark(session, relayMessage);
        };
    }

    /**
     * Handle setup message (call metadata).
     *
     * <p>The setup message is the only one that carries callSid/from/to, so we
     * cache it per session and register this connection's outbound sink under the
     * conversation ID so out-of-band {@link #sendResponse} pushes can reach it.
     */
    private Mono<Void> handleSetup(
            WebSocketSession session,
            Sinks.Many<String> outbound,
            ConversationRelayMessage message) {
        log.info("Received setup message for call: {}", message.getCallSid());
        callMetadataBySession.put(session.getId(), new CallMetadata(
            message.getCallSid(), message.getFrom(), message.getTo()));
        if (message.getCallSid() != null) {
            outboundByConversation.put(message.getCallSid(), outbound);
        }
        return Mono.empty();
    }

    /**
     * Handle prompt message (transcribed speech).
     */
    private Mono<Void> handlePrompt(
            WebSocketSession session,
            Sinks.Many<String> outbound,
            ConversationRelayMessage message) {
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

        // Streaming callback (push model): the handler calls sendResponse() itself,
        // so we just invoke it. Otherwise fall back to the single-response callback.
        return processInbound(inboundMessage, Map.of())
            .flatMap(context -> tac.hasMessageStreamCallback()
                ? tac.handleMessageContextStream(context)
                : tac.handleMessageContext(context)
                    .doOnNext(response ->
                        emit(outbound, relayProtocol.buildResponseMessage(response.getContent())))
                    .then())
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
        return wssDomain + "/ws/voice";
    }

    /**
     * Call metadata captured from the Conversation Relay setup message.
     */
    private record CallMetadata(String callSid, String from, String to) {
        static final CallMetadata EMPTY = new CallMetadata(null, null, null);
    }
}
