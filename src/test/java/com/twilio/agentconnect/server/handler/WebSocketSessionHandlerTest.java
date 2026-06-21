package com.twilio.agentconnect.server.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.agentconnect.channels.voice.VoiceChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebSocketSessionHandler}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketSessionHandlerTest {

    @Mock
    private VoiceChannel voiceChannel;

    @Mock
    private WebSocketSession session;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketSessionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WebSocketSessionHandler(voiceChannel, objectMapper);
        when(session.getId()).thenReturn("ws-session-1");
    }

    @Test
    void handleDelegatesToVoiceChannelAndCompletes() {
        when(session.receive()).thenReturn(Flux.empty());
        when(voiceChannel.handleWebSocketConnection(eq(session), any()))
            .thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(session))
            .verifyComplete();

        verify(voiceChannel).handleWebSocketConnection(eq(session), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleMapsInboundPayloadsToTextAndWiresThemThrough() {
        WebSocketMessage msg1 = mock(WebSocketMessage.class);
        WebSocketMessage msg2 = mock(WebSocketMessage.class);
        when(msg1.getPayloadAsText()).thenReturn("{\"type\":\"setup\"}");
        when(msg2.getPayloadAsText()).thenReturn("{\"type\":\"prompt\"}");
        when(session.receive()).thenReturn(Flux.just(msg1, msg2));
        when(voiceChannel.handleWebSocketConnection(eq(session), any()))
            .thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(session))
            .verifyComplete();

        ArgumentCaptor<Flux<String>> inboundCaptor = ArgumentCaptor.forClass(Flux.class);
        verify(voiceChannel).handleWebSocketConnection(eq(session), inboundCaptor.capture());

        // The captured Flux should emit the decoded text payloads in order.
        StepVerifier.create(inboundCaptor.getValue())
            .expectNext("{\"type\":\"setup\"}")
            .expectNext("{\"type\":\"prompt\"}")
            .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void inboundFluxPropagatesReceiveErrors() {
        RuntimeException boom = new RuntimeException("receive blew up");
        when(session.receive()).thenReturn(Flux.error(boom));
        when(voiceChannel.handleWebSocketConnection(eq(session), any()))
            .thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(session))
            .verifyComplete();

        ArgumentCaptor<Flux<String>> inboundCaptor = ArgumentCaptor.forClass(Flux.class);
        verify(voiceChannel).handleWebSocketConnection(eq(session), inboundCaptor.capture());

        // The inbound Flux passed to the channel carries the receive() error through.
        StepVerifier.create(inboundCaptor.getValue())
            .expectErrorMatches(err -> err == boom)
            .verify();
    }

    @Test
    void handlePropagatesErrorFromVoiceChannel() {
        when(session.receive()).thenReturn(Flux.empty());
        RuntimeException handlerError = new RuntimeException("handler error");
        when(voiceChannel.handleWebSocketConnection(eq(session), any()))
            .thenReturn(Mono.error(handlerError));

        // The handler logs but re-surfaces the error from the channel.
        StepVerifier.create(handler.handle(session))
            .expectErrorMatches(err -> err == handlerError)
            .verify();
    }
}
