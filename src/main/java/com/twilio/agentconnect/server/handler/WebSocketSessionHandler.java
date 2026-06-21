package com.twilio.agentconnect.server.handler;


import com.twilio.agentconnect.channels.voice.VoiceChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * WebSocket handler for Conversation Relay.
 */
@Component
@ConditionalOnBean(VoiceChannel.class)
public class WebSocketSessionHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionHandler.class);

    private final VoiceChannel voiceChannel;
    private final ObjectMapper objectMapper;

    public WebSocketSessionHandler(VoiceChannel voiceChannel, ObjectMapper objectMapper) {
        this.voiceChannel = voiceChannel;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("WebSocket connection established: {}", session.getId());

        Flux<String> inbound = session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .doOnNext(message -> log.debug("Received: {}", message))
            .doOnError(error -> log.error("WebSocket receive error", error));

        return voiceChannel.handleWebSocketConnection(session, inbound)
            .doOnError(error -> log.error("WebSocket handler error", error))
            .doFinally(signal -> log.info("WebSocket connection closed: {} ({})",
                                         session.getId(), signal));
    }
}
