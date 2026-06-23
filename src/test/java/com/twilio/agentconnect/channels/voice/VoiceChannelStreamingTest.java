package com.twilio.agentconnect.channels.voice;

import com.twilio.agentconnect.cache.IdempotencyCache;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.core.TacConfiguration;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.validation.TwilioSignatureValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the streaming/push path of {@link VoiceChannel}:
 * {@link VoiceChannel#sendResponse(String, String, boolean)}, the per-connection
 * outbound sink wiring, and the stream-callback branch of prompt handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoiceChannelStreamingTest {

    @Mock
    private TwilioAgentConnect tac;
    @Mock
    private TwilioSignatureValidator signatureValidator;
    @Mock
    private TacConfiguration config;
    @Mock
    private TwimlGenerator twimlGenerator;
    @Mock
    private IdempotencyCache idempotencyCache;

    // Use a real protocol + ObjectMapper so we assert on the actual JSON frames.
    private final ConversationRelayProtocol relayProtocol =
        new ConversationRelayProtocol(new com.fasterxml.jackson.databind.ObjectMapper());

    private VoiceChannel voiceChannel;

    @BeforeEach
    void setUp() {
        voiceChannel = new VoiceChannel(
            tac, signatureValidator, config, twimlGenerator, relayProtocol, idempotencyCache);
    }

    /**
     * Opens a WebSocket connection, feeds it a setup message (which registers the
     * outbound sink under the callSid), and captures every frame the channel sends.
     * The returned sink lets the test keep the inbound stream open while it pushes.
     */
    private CapturedConnection openConnection(String callSid) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-" + callSid);

        // Echo textMessage payloads into a captured list via a fake WebSocketMessage.
        List<String> sent = new CopyOnWriteArrayList<>();
        when(session.textMessage(anyString())).thenAnswer(inv -> {
            String payload = inv.getArgument(0);
            WebSocketMessage wsm = mock(WebSocketMessage.class);
            lenient().when(wsm.getPayloadAsText()).thenReturn(payload);
            sent.add(payload);
            return wsm;
        });
        // session.send subscribes to the outbound flux; just drain it.
        when(session.send(any())).thenAnswer(inv -> {
            Flux<WebSocketMessage> frames = Flux.from(inv.getArgument(0));
            return frames.then();
        });

        // Inbound: a single setup message, then stay open via never() so the
        // connection (and its sink) remains active while we push responses.
        ConversationRelayMessage setup = ConversationRelayMessage.builder()
            .type(ConversationRelayMessage.MessageType.SETUP)
            .callSid(callSid)
            .from("+15551112222")
            .to("+15553334444")
            .build();
        Sinks.Many<String> inbound = Sinks.many().unicast().onBackpressureBuffer();
        // setup is delivered as raw JSON the protocol can parse.
        inbound.tryEmitNext("{\"type\":\"setup\",\"callSid\":\"" + callSid
            + "\",\"from\":\"+15551112222\",\"to\":\"+15553334444\"}");

        when(session.receive()).thenReturn(Flux.never()); // not used; we call handle with our flux

        // Drive the connection with our controllable inbound flux.
        Mono<Void> connection = voiceChannel.handleWebSocketConnection(session, inbound.asFlux());
        // Subscribe so setup is processed and the sink is registered.
        connection.subscribe();

        return new CapturedConnection(sent, inbound);
    }

    private record CapturedConnection(List<String> sent, Sinks.Many<String> inbound) {}

    @Test
    void sendResponseStreamsTokenThenTerminalFrame() {
        CapturedConnection conn = openConnection("CAstream");

        voiceChannel.sendResponse("CAstream", "Hello", false);
        voiceChannel.sendResponse("CAstream", " world", false);
        voiceChannel.sendResponse("CAstream", "", true);

        List<String> frames = conn.sent();
        assertEquals(3, frames.size(), "two token frames + one terminal frame");
        assertTrue(frames.get(0).contains("\"token\":\"Hello\"") && frames.get(0).contains("\"last\":false"));
        assertTrue(frames.get(1).contains("\"token\":\" world\"") && frames.get(1).contains("\"last\":false"));
        assertTrue(frames.get(2).contains("\"last\":true"));
    }

    @Test
    void sendResponseSkipsEmptyNonTerminalToken() {
        CapturedConnection conn = openConnection("CAskip");

        voiceChannel.sendResponse("CAskip", "", false);   // skipped
        voiceChannel.sendResponse("CAskip", null, false);  // skipped
        voiceChannel.sendResponse("CAskip", "hi", false);  // sent

        List<String> frames = conn.sent();
        assertEquals(1, frames.size());
        assertTrue(frames.get(0).contains("\"token\":\"hi\""));
    }

    @Test
    void sendResponseForUnknownConversationIsDroppedSilently() {
        // No connection opened for this id -> no sink -> warn + no-op, no exception.
        voiceChannel.sendResponse("CAghost", "hi", false);
        voiceChannel.sendResponse("CAghost", "", true);
        // Nothing to assert beyond "did not throw"; reaching here is success.
    }

    @Test
    void promptUsesStreamCallbackWhenRegistered() {
        // Arrange a connection and route the prompt through the stream path.
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-CAprompt");
        when(session.textMessage(anyString())).thenAnswer(inv -> mock(WebSocketMessage.class));
        when(session.send(any())).thenAnswer(inv -> Flux.from(inv.getArgument(0)).then());

        MessageContext ctx = MessageContext.builder().conversationId("CAprompt").build();
        when(idempotencyCache.checkAndSet(any())).thenReturn(Mono.just(true));
        when(tac.processInboundMessage(any(), any(), any())).thenReturn(Mono.just(ctx));
        when(tac.hasMessageStreamCallback()).thenReturn(true);
        when(tac.handleMessageContextStream(ctx)).thenReturn(Mono.empty());

        String setupJson = "{\"type\":\"setup\",\"callSid\":\"CAprompt\",\"from\":\"+1\",\"to\":\"+2\"}";
        String promptJson = "{\"type\":\"prompt\",\"voicePrompt\":\"hi\"}";

        StepVerifier.create(
                voiceChannel.handleWebSocketConnection(session, Flux.just(setupJson, promptJson)))
            .verifyComplete();

        // Stream path taken; single-response path not used.
        org.mockito.Mockito.verify(tac).handleMessageContextStream(ctx);
        org.mockito.Mockito.verify(tac, org.mockito.Mockito.never()).handleMessageContext(any());
    }

    @Test
    void promptUsesSingleResponseWhenNoStreamCallback() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-CAsingle");
        List<String> sent = new ArrayList<>();
        when(session.textMessage(anyString())).thenAnswer(inv -> {
            sent.add(inv.getArgument(0));
            return mock(WebSocketMessage.class);
        });
        when(session.send(any())).thenAnswer(inv -> Flux.from(inv.getArgument(0)).then());

        MessageContext ctx = MessageContext.builder().conversationId("CAsingle").build();
        com.twilio.agentconnect.context.model.OutboundMessage out =
            com.twilio.agentconnect.context.model.OutboundMessage.builder().content("done").build();
        when(idempotencyCache.checkAndSet(any())).thenReturn(Mono.just(true));
        when(tac.processInboundMessage(any(), any(), any())).thenReturn(Mono.just(ctx));
        when(tac.hasMessageStreamCallback()).thenReturn(false);
        when(tac.handleMessageContext(ctx)).thenReturn(Mono.just(out));

        String setupJson = "{\"type\":\"setup\",\"callSid\":\"CAsingle\",\"from\":\"+1\",\"to\":\"+2\"}";
        String promptJson = "{\"type\":\"prompt\",\"voicePrompt\":\"hi\"}";

        StepVerifier.create(
                voiceChannel.handleWebSocketConnection(session, Flux.just(setupJson, promptJson)))
            .verifyComplete();

        org.mockito.Mockito.verify(tac).handleMessageContext(ctx);
        org.mockito.Mockito.verify(tac, org.mockito.Mockito.never()).handleMessageContextStream(any());
        // The single complete response was sent as one frame.
        assertTrue(sent.stream().anyMatch(f -> f.contains("\"token\":\"done\"") && f.contains("\"last\":true")));
    }
}
