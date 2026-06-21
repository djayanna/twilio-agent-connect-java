package com.twilio.agentconnect.channels.messaging;

import com.twilio.agentconnect.cache.IdempotencyCache;
import com.twilio.agentconnect.context.model.InboundMessage;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.validation.TwilioSignatureValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the messaging channels (SMS, WhatsApp, RCS, Chat), which all
 * extend {@link MessagingChannel} and only differ by their channel type.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessagingChannelTest {

    @Mock
    private TwilioAgentConnect tac;

    @Mock
    private TwilioSignatureValidator signatureValidator;

    @Mock
    private IdempotencyCache idempotencyCache;

    private SmsChannel smsChannel;
    private WhatsAppChannel whatsAppChannel;
    private RcsChannel rcsChannel;
    private ChatChannel chatChannel;

    @BeforeEach
    void setUp() {
        smsChannel = new SmsChannel(tac, signatureValidator, idempotencyCache);
        whatsAppChannel = new WhatsAppChannel(tac, signatureValidator, idempotencyCache);
        rcsChannel = new RcsChannel(tac, signatureValidator, idempotencyCache);
        chatChannel = new ChatChannel(tac, signatureValidator, idempotencyCache);
    }

    @Test
    void channelTypesAreCorrect() {
        assertEquals(ChannelType.SMS, smsChannel.getChannelType());
        assertEquals(ChannelType.WHATSAPP, whatsAppChannel.getChannelType());
        assertEquals(ChannelType.RCS, rcsChannel.getChannelType());
        assertEquals(ChannelType.CHAT, chatChannel.getChannelType());
    }

    @Test
    void processInboundNewMessageDelegatesWithChannelType() {
        InboundMessage message = InboundMessage.builder()
            .content("hi")
            .conversationId("CH1")
            .build();
        MessageContext context = MessageContext.builder().conversationId("CH1").build();

        when(idempotencyCache.checkAndSet("tok")).thenReturn(Mono.just(true));
        when(tac.processInboundMessage(eq(ChannelType.SMS), eq(message), any()))
            .thenReturn(Mono.just(context));

        StepVerifier.create(
                smsChannel.processInbound(message, Map.of("i-twilio-idempotency-token", "tok")))
            .expectNext(context)
            .verifyComplete();

        verify(tac).processInboundMessage(eq(ChannelType.SMS), eq(message), any());
    }

    @Test
    void processInboundDuplicateMessageIsSkipped() {
        InboundMessage message = InboundMessage.builder().content("hi").build();

        when(idempotencyCache.checkAndSet("tok")).thenReturn(Mono.just(false));

        StepVerifier.create(
                whatsAppChannel.processInbound(message, Map.of("i-twilio-idempotency-token", "tok")))
            .verifyComplete();

        verify(tac, never()).processInboundMessage(any(), any(), any());
    }

    @Test
    void processInboundEmptyCacheResultIsSkipped() {
        InboundMessage message = InboundMessage.builder().content("hi").build();

        when(idempotencyCache.checkAndSet(any())).thenReturn(Mono.empty());

        StepVerifier.create(chatChannel.processInbound(message, Map.of()))
            .verifyComplete();

        verify(tac, never()).processInboundMessage(any(), any(), any());
    }

    @Test
    void sendMessageDelegatesWithChannelType() {
        OutboundMessage outbound = OutboundMessage.builder()
            .content("reply")
            .conversationId("CH1")
            .build();
        when(tac.sendMessage("CH1", "reply", ChannelType.RCS)).thenReturn(Mono.just(outbound));

        StepVerifier.create(rcsChannel.sendMessage("CH1", "reply"))
            .expectNext(outbound)
            .verifyComplete();

        verify(tac).sendMessage("CH1", "reply", ChannelType.RCS);
    }

    @Test
    void validateSignatureDelegatesToValidator() {
        when(signatureValidator.validate("sig", "url", Map.of())).thenReturn(true);

        StepVerifier.create(smsChannel.validateSignature("sig", "url", Map.of()))
            .expectNext(true)
            .verifyComplete();

        verify(signatureValidator).validate("sig", "url", Map.of());
    }
}
