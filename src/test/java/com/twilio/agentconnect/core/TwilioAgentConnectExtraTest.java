package com.twilio.agentconnect.core;

import com.twilio.agentconnect.callback.CallbackRegistry;
import com.twilio.agentconnect.callback.ConversationEndedCallback;
import com.twilio.agentconnect.callback.MessageReadyCallback;
import com.twilio.agentconnect.callback.MessageStreamCallback;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Additional unit tests for {@link TwilioAgentConnect} covering delegation paths
 * not exercised by {@code TwilioAgentConnectTest}: callback registration,
 * sendMessage delegation, conversation-end handling (with and without a
 * registered callback) and the NEVER memory-mode branch.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwilioAgentConnectExtraTest {

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
    }

    @Test
    void onMessageReadyDelegatesToRegistry() {
        MessageReadyCallback callback = ctx -> Mono.empty();

        tac.onMessageReady(callback);

        verify(callbackRegistry).onMessageReady(callback);
    }

    @Test
    void onConversationEndedDelegatesToRegistry() {
        ConversationEndedCallback callback = session -> Mono.empty();

        tac.onConversationEnded(callback);

        verify(callbackRegistry).onConversationEnded(callback);
    }

    @Test
    void sendMessageDelegatesToConversationClient() {
        OutboundMessage outbound = OutboundMessage.builder()
            .content("Hello back")
            .conversationId("CH123")
            .build();

        when(conversationClient.sendMessage("CH123", "Hello back"))
            .thenReturn(Mono.just(outbound));

        StepVerifier.create(tac.sendMessage("CH123", "Hello back", ChannelType.SMS))
            .expectNext(outbound)
            .verifyComplete();

        verify(conversationClient).sendMessage("CH123", "Hello back");
    }

    @Test
    void sendMessagePropagatesError() {
        RuntimeException boom = new RuntimeException("send failed");
        when(conversationClient.sendMessage(anyString(), anyString()))
            .thenReturn(Mono.error(boom));

        StepVerifier.create(tac.sendMessage("CH123", "oops", ChannelType.SMS))
            .expectErrorMatches(err -> err == boom)
            .verify();
    }

    @Test
    void handleConversationEndedInvokesRegisteredCallback() {
        Session session = Session.builder()
            .id("CH123")
            .conversationId("CH123")
            .channelType(ChannelType.SMS)
            .build();

        ConversationEndedCallback callback = s -> Mono.empty();

        when(sessionManager.get("CH123")).thenReturn(Mono.just(session));
        when(callbackRegistry.hasConversationEndedCallback()).thenReturn(true);
        when(callbackRegistry.getConversationEndedCallback()).thenReturn(callback);
        when(sessionManager.delete("CH123")).thenReturn(Mono.empty());

        StepVerifier.create(tac.handleConversationEnded("CH123"))
            .verifyComplete();

        verify(callbackRegistry).getConversationEndedCallback();
        verify(sessionManager).delete("CH123");
    }

    @Test
    void handleConversationEndedWithoutCallbackStillDeletesSession() {
        Session session = Session.builder()
            .id("CH456")
            .conversationId("CH456")
            .channelType(ChannelType.SMS)
            .build();

        when(sessionManager.get("CH456")).thenReturn(Mono.just(session));
        when(callbackRegistry.hasConversationEndedCallback()).thenReturn(false);
        when(sessionManager.delete("CH456")).thenReturn(Mono.empty());

        StepVerifier.create(tac.handleConversationEnded("CH456"))
            .verifyComplete();

        verify(callbackRegistry, never()).getConversationEndedCallback();
        verify(sessionManager).delete("CH456");
    }

    @Test
    void handleMessageContextReturnsEmptyWhenNoCallback() {
        when(callbackRegistry.hasMessageReadyCallback()).thenReturn(false);

        MessageContext context = MessageContext.builder()
            .conversationId("CH123")
            .build();

        StepVerifier.create(tac.handleMessageContext(context))
            .verifyComplete();

        verify(callbackRegistry, never()).getMessageReadyCallback();
    }

    @Test
    void processInboundMessageSkipsMemoryWhenModeNever() {
        TacConfiguration.MemoryConfig memoryConfig = new TacConfiguration.MemoryConfig();
        memoryConfig.setMode(MemoryMode.NEVER);
        when(config.getMemory()).thenReturn(memoryConfig);

        InboundMessage message = InboundMessage.builder()
            .content("Hi")
            .conversationId("CH-NEVER")
            .from("+15550001111")
            .channelType(ChannelType.SMS)
            .timestamp(Instant.now())
            .build();

        Session session = Session.builder()
            .id("CH-NEVER")
            .conversationId("CH-NEVER")
            .channelType(ChannelType.SMS)
            .build();

        when(sessionManager.getOrCreate(eq("CH-NEVER"), eq(ChannelType.SMS)))
            .thenReturn(Mono.just(session));

        StepVerifier.create(tac.processInboundMessage(ChannelType.SMS, message, new HashMap<>()))
            .expectNextMatches(ctx ->
                ctx.getConversationId().equals("CH-NEVER") &&
                ctx.getMemory() != null &&
                ctx.getMemory().isEmpty())
            .verifyComplete();

        // NEVER mode must not touch the memory client at all.
        verifyNoInteractions(memoryClient);
    }

    @Test
    void processInboundMessageFallsBackToEmptyMemoryOnError() {
        TacConfiguration.MemoryConfig memoryConfig = new TacConfiguration.MemoryConfig();
        memoryConfig.setMode(MemoryMode.ALWAYS);
        when(config.getMemory()).thenReturn(memoryConfig);

        InboundMessage message = InboundMessage.builder()
            .content("Hi")
            .conversationId("CH-ERR")
            .from("+15550002222")
            .channelType(ChannelType.SMS)
            .timestamp(Instant.now())
            .build();

        Session session = Session.builder()
            .id("CH-ERR")
            .conversationId("CH-ERR")
            .channelType(ChannelType.SMS)
            .build();

        when(sessionManager.getOrCreate(eq("CH-ERR"), eq(ChannelType.SMS)))
            .thenReturn(Mono.just(session));
        when(memoryClient.retrieveMemory(anyString(), any(Session.class)))
            .thenReturn(Mono.error(new RuntimeException("recall failed")));

        StepVerifier.create(tac.processInboundMessage(ChannelType.SMS, message, new HashMap<>()))
            .expectNextMatches(ctx -> ctx.getMemory() != null && ctx.getMemory().isEmpty())
            .verifyComplete();
    }

    @Test
    void processInboundMessageUsesProfileIdAsMemoryIdentifierWhenPresent() {
        TacConfiguration.MemoryConfig memoryConfig = new TacConfiguration.MemoryConfig();
        memoryConfig.setMode(MemoryMode.ALWAYS);
        when(config.getMemory()).thenReturn(memoryConfig);

        InboundMessage message = InboundMessage.builder()
            .content("Hi")
            .conversationId("CH-ID")
            .from("+15550003333")
            .channelType(ChannelType.SMS)
            .timestamp(Instant.now())
            .build();

        Session session = Session.builder()
            .id("CH-ID")
            .conversationId("CH-ID")
            .channelType(ChannelType.SMS)
            .profileId("mem_profile_resolved")
            .build();

        when(sessionManager.getOrCreate(eq("CH-ID"), eq(ChannelType.SMS)))
            .thenReturn(Mono.just(session));
        when(memoryClient.retrieveMemory(anyString(), any(Session.class)))
            .thenReturn(Mono.just(MemoryResponse.empty()));

        StepVerifier.create(tac.processInboundMessage(ChannelType.SMS, message, new HashMap<>()))
            .expectNextCount(1)
            .verifyComplete();

        ArgumentCaptor<String> identifierCaptor = ArgumentCaptor.forClass(String.class);
        verify(memoryClient, times(1)).retrieveMemory(identifierCaptor.capture(), any(Session.class));
        // Existing profile id on the session is preferred over the caller address.
        org.assertj.core.api.Assertions.assertThat(identifierCaptor.getValue())
            .isEqualTo("mem_profile_resolved");
    }

    @Test
    void onMessageStreamDelegatesToRegistry() {
        MessageStreamCallback callback = ctx -> Mono.empty();

        tac.onMessageStream(callback);

        verify(callbackRegistry).onMessageStream(callback);
    }

    @Test
    void hasMessageStreamCallbackDelegatesToRegistry() {
        when(callbackRegistry.hasMessageStreamCallback()).thenReturn(true);
        org.assertj.core.api.Assertions.assertThat(tac.hasMessageStreamCallback()).isTrue();
        verify(callbackRegistry).hasMessageStreamCallback();
    }

    @Test
    void handleMessageContextStreamInvokesRegisteredCallback() {
        MessageContext context = MessageContext.builder().conversationId("CA1").build();
        when(callbackRegistry.hasMessageStreamCallback()).thenReturn(true);
        when(callbackRegistry.getMessageStreamCallback())
            .thenReturn(ctx -> Mono.empty());

        StepVerifier.create(tac.handleMessageContextStream(context))
            .verifyComplete();

        verify(callbackRegistry).getMessageStreamCallback();
    }

    @Test
    void handleMessageContextStreamWithoutCallbackReturnsEmpty() {
        MessageContext context = MessageContext.builder().conversationId("CA1").build();
        when(callbackRegistry.hasMessageStreamCallback()).thenReturn(false);

        StepVerifier.create(tac.handleMessageContextStream(context))
            .verifyComplete();

        verify(callbackRegistry, never()).getMessageStreamCallback();
    }
}
