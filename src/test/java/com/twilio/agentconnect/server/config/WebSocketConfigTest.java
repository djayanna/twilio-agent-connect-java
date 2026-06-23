package com.twilio.agentconnect.server.config;

import com.twilio.agentconnect.server.handler.WebSocketSessionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WebSocketConfig}.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private WebSocketSessionHandler webSocketHandler;

    private WebSocketConfig config;

    @BeforeEach
    void setUp() {
        config = new WebSocketConfig(webSocketHandler);
    }

    @Test
    void webSocketHandlerMappingMapsVoiceWsToHandler() {
        HandlerMapping mapping = config.webSocketHandlerMapping();

        assertNotNull(mapping);
        SimpleUrlHandlerMapping urlMapping =
            assertInstanceOf(SimpleUrlHandlerMapping.class, mapping);

        Map<String, ?> urlMap = urlMapping.getUrlMap();
        assertTrue(urlMap.containsKey("/ws/voice"),
            "Expected /ws/voice to be mapped");
        assertSame(webSocketHandler, urlMap.get("/ws/voice"),
            "Expected /ws/voice to map to the injected handler");
        assertEquals(1, urlMap.size(), "Only /ws/voice should be mapped");
    }

    @Test
    void webSocketHandlerMappingHasOrderOne() {
        SimpleUrlHandlerMapping urlMapping =
            (SimpleUrlHandlerMapping) config.webSocketHandlerMapping();

        assertEquals(1, urlMapping.getOrder());
    }

    @Test
    void mappedHandlerIsAWebSocketHandler() {
        SimpleUrlHandlerMapping urlMapping =
            (SimpleUrlHandlerMapping) config.webSocketHandlerMapping();

        Object mapped = urlMapping.getUrlMap().get("/ws/voice");
        assertInstanceOf(WebSocketHandler.class, mapped);
    }

    @Test
    void handlerAdapterBeanIsCreated() {
        WebSocketHandlerAdapter adapter = config.handlerAdapter();
        assertNotNull(adapter);
    }
}
