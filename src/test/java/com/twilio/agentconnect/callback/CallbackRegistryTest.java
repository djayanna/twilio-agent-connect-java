package com.twilio.agentconnect.callback;

import com.twilio.agentconnect.context.model.OutboundMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CallbackRegistry}.
 */
class CallbackRegistryTest {

    private CallbackRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CallbackRegistry();
    }

    @Test
    void hasNoCallbacksByDefault() {
        assertThat(registry.hasMessageReadyCallback()).isFalse();
        assertThat(registry.hasMessageStreamCallback()).isFalse();
        assertThat(registry.hasConversationEndedCallback()).isFalse();
        assertThat(registry.getMessageReadyCallback()).isNull();
        assertThat(registry.getMessageStreamCallback()).isNull();
        assertThat(registry.getConversationEndedCallback()).isNull();
    }

    @Test
    void registersAndReturnsStreamCallback() {
        MessageStreamCallback stream = ctx -> Mono.empty();

        registry.onMessageStream(stream);

        assertThat(registry.hasMessageStreamCallback()).isTrue();
        assertThat(registry.getMessageStreamCallback()).isSameAs(stream);
    }

    @Test
    void registersMessageReadyCallback() {
        MessageReadyCallback callback = ctx -> Mono.just(new OutboundMessage());

        registry.onMessageReady(callback);

        assertThat(registry.hasMessageReadyCallback()).isTrue();
        assertThat(registry.getMessageReadyCallback()).isSameAs(callback);
        // registering one callback type does not affect the other
        assertThat(registry.hasConversationEndedCallback()).isFalse();
    }

    @Test
    void registersConversationEndedCallback() {
        ConversationEndedCallback callback = session -> Mono.empty();

        registry.onConversationEnded(callback);

        assertThat(registry.hasConversationEndedCallback()).isTrue();
        assertThat(registry.getConversationEndedCallback()).isSameAs(callback);
        assertThat(registry.hasMessageReadyCallback()).isFalse();
    }

    @Test
    void latestRegistrationWins() {
        MessageReadyCallback first = ctx -> Mono.empty();
        MessageReadyCallback second = ctx -> Mono.just(new OutboundMessage());

        registry.onMessageReady(first);
        registry.onMessageReady(second);

        assertThat(registry.getMessageReadyCallback()).isSameAs(second);
    }

    @Test
    void registersBothCallbacksIndependently() {
        MessageReadyCallback messageReady = ctx -> Mono.just(new OutboundMessage());
        ConversationEndedCallback conversationEnded = session -> Mono.empty();

        registry.onMessageReady(messageReady);
        registry.onConversationEnded(conversationEnded);

        assertThat(registry.hasMessageReadyCallback()).isTrue();
        assertThat(registry.hasConversationEndedCallback()).isTrue();
        assertThat(registry.getMessageReadyCallback()).isSameAs(messageReady);
        assertThat(registry.getConversationEndedCallback()).isSameAs(conversationEnded);
    }
}
