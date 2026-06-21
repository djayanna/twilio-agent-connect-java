package com.twilio.agentconnect.core;

import com.twilio.agentconnect.callback.CallbackRegistry;
import com.twilio.agentconnect.context.client.ConversationClient;
import com.twilio.agentconnect.context.client.KnowledgeClient;
import com.twilio.agentconnect.context.client.MemoryClient;
import com.twilio.agentconnect.context.model.InboundMessage;
import com.twilio.agentconnect.context.model.MemoryResponse;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.session.Session;
import com.twilio.agentconnect.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TwilioAgentConnect.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwilioAgentConnectTest {

    @Mock
    private TacConfiguration config;

    @Mock
    private MemoryClient memoryClient;

    @Mock
    private ConversationClient conversationClient;

    @Mock
    private KnowledgeClient knowledgeClient;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private CallbackRegistry callbackRegistry;

    private TwilioAgentConnect tac;

    @BeforeEach
    void setUp() {
        tac = new TwilioAgentConnect(
            config,
            memoryClient,
            conversationClient,
            knowledgeClient,
            sessionManager,
            callbackRegistry
        );

        // Setup default mock behavior
        TacConfiguration.MemoryConfig memoryConfig = new TacConfiguration.MemoryConfig();
        memoryConfig.setMode(MemoryMode.ONCE);
        when(config.getMemory()).thenReturn(memoryConfig);
    }

    @Test
    void testProcessInboundMessage() {
        // Arrange
        InboundMessage message = InboundMessage.builder()
            .content("Hello")
            .conversationId("CHXXX")
            .from("+1234567890")
            .channelType(ChannelType.SMS)
            .timestamp(Instant.now())
            .build();

        Session session = Session.builder()
            .id("CHXXX")
            .conversationId("CHXXX")
            .channelType(ChannelType.SMS)
            .profileId("+1234567890")
            .createdAt(Instant.now())
            .build();

        MemoryResponse memory = MemoryResponse.empty();

        when(sessionManager.getOrCreate(anyString(), any(ChannelType.class)))
            .thenReturn(Mono.just(session));
        when(memoryClient.retrieveMemory(anyString(), any(Session.class)))
            .thenReturn(Mono.just(memory));

        // Act & Assert
        StepVerifier.create(tac.processInboundMessage(ChannelType.SMS, message, new HashMap<>()))
            .expectNextMatches(context ->
                context.getConversationId().equals("CHXXX") &&
                context.getMessage().getContent().equals("Hello")
            )
            .verifyComplete();
    }

    @Test
    void testHandleMessageContext() {
        // Arrange
        MessageContext context = MessageContext.builder()
            .conversationId("CHXXX")
            .build();

        OutboundMessage response = OutboundMessage.builder()
            .content("Response")
            .conversationId("CHXXX")
            .build();

        when(callbackRegistry.hasMessageReadyCallback()).thenReturn(true);
        when(callbackRegistry.getMessageReadyCallback())
            .thenReturn(ctx -> Mono.just(response));

        // Act & Assert
        StepVerifier.create(tac.handleMessageContext(context))
            .expectNext(response)
            .verifyComplete();
    }
}
