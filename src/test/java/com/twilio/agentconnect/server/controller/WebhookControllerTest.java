package com.twilio.agentconnect.server.controller;

import com.twilio.agentconnect.channels.Channel;
import com.twilio.agentconnect.context.model.InboundMessage;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebhookController}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookControllerTest {

    @Mock
    private TwilioAgentConnect tac;

    @Mock
    private Channel smsChannel;

    @Mock
    private Channel whatsAppChannel;

    @Mock
    private Channel chatChannel;

    private Map<ChannelType, Channel> channels;
    private WebhookController controller;

    @BeforeEach
    void setUp() {
        channels = new EnumMap<>(ChannelType.class);
        channels.put(ChannelType.SMS, smsChannel);
        channels.put(ChannelType.WHATSAPP, whatsAppChannel);
        channels.put(ChannelType.CHAT, chatChannel);
        controller = new WebhookController(tac, channels);
    }

    @Test
    void handleWebhookReturns200OkImmediately() {
        Map<String, String> params = new HashMap<>();
        params.put("From", "+15551234567");
        params.put("Body", "hi");
        Map<String, String> headers = new HashMap<>();

        // No channel stubs needed: signature validation defaults to a null Mono
        // and the fire-and-forget subscription completes without affecting the response.
        when(smsChannel.validateSignature(any(), any(), anyMap()))
            .thenReturn(Mono.just(false));

        StepVerifier.create(controller.handleWebhook(params, headers))
            .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatusCode()))
            .verifyComplete();
    }

    @Test
    void handleWebhookRoutesSmsAndProcessesThroughChannelAndTac() {
        Map<String, String> params = new HashMap<>();
        params.put("From", "+15551234567");
        params.put("To", "+15557654321");
        params.put("Body", "hello");
        params.put("ConversationSid", "CH123");
        params.put("MessageSid", "SM123");
        params.put("ParticipantSid", "MB123");

        Map<String, String> headers = new HashMap<>();
        headers.put("x-twilio-signature", "sig-value");

        MessageContext context = MessageContext.builder()
            .conversationId("CH123")
            .channelType(ChannelType.SMS)
            .build();
        OutboundMessage outbound = OutboundMessage.builder()
            .content("reply")
            .conversationId("CH123")
            .build();

        when(smsChannel.validateSignature(eq("sig-value"), anyString(), anyMap()))
            .thenReturn(Mono.just(true));
        when(smsChannel.processInbound(any(InboundMessage.class), anyMap()))
            .thenReturn(Mono.just(context));
        when(tac.handleMessageContext(context)).thenReturn(Mono.just(outbound));

        StepVerifier.create(controller.handleWebhook(params, headers))
            .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatusCode()))
            .verifyComplete();

        // The fire-and-forget pipeline runs asynchronously; wait for the calls.
        ArgumentCaptor<InboundMessage> captor = ArgumentCaptor.forClass(InboundMessage.class);
        verify(smsChannel, timeout(1000)).validateSignature(eq("sig-value"), anyString(), anyMap());
        verify(smsChannel, timeout(1000)).processInbound(captor.capture(), anyMap());
        verify(tac, timeout(1000)).handleMessageContext(context);

        InboundMessage built = captor.getValue();
        assertEquals("hello", built.getContent());
        assertEquals(ChannelType.SMS, built.getChannelType());
        assertEquals("CH123", built.getConversationId());
        assertEquals("SM123", built.getMessageSid());
        assertEquals("MB123", built.getParticipantSid());
        assertEquals("+15551234567", built.getFrom());
        assertEquals("+15557654321", built.getTo());
    }

    @Test
    void handleWebhookRoutesWhatsAppByFromPrefix() {
        Map<String, String> params = new HashMap<>();
        params.put("From", "whatsapp:+15551234567");
        params.put("Body", "hola");
        Map<String, String> headers = new HashMap<>();

        MessageContext context = MessageContext.builder().conversationId("CH9").build();
        when(whatsAppChannel.validateSignature(any(), anyString(), anyMap()))
            .thenReturn(Mono.just(true));
        when(whatsAppChannel.processInbound(any(InboundMessage.class), anyMap()))
            .thenReturn(Mono.just(context));
        when(tac.handleMessageContext(context)).thenReturn(Mono.empty());

        StepVerifier.create(controller.handleWebhook(params, headers))
            .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatusCode()))
            .verifyComplete();

        verify(whatsAppChannel, timeout(1000)).processInbound(any(InboundMessage.class), anyMap());
        verify(smsChannel, never()).processInbound(any(), anyMap());
    }

    @Test
    void handleWebhookRoutesByMessagingBindingTypeChat() {
        Map<String, String> params = new HashMap<>();
        params.put("MessagingBinding.Type", "chat");
        params.put("Body", "msg");
        Map<String, String> headers = new HashMap<>();

        MessageContext context = MessageContext.builder().conversationId("CH-chat").build();
        when(chatChannel.validateSignature(any(), anyString(), anyMap()))
            .thenReturn(Mono.just(true));
        when(chatChannel.processInbound(any(InboundMessage.class), anyMap()))
            .thenReturn(Mono.just(context));
        when(tac.handleMessageContext(context)).thenReturn(Mono.empty());

        StepVerifier.create(controller.handleWebhook(params, headers))
            .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatusCode()))
            .verifyComplete();

        verify(chatChannel, timeout(1000)).processInbound(any(InboundMessage.class), anyMap());
    }

    @Test
    void handleWebhookSkipsProcessingWhenSignatureInvalid() {
        Map<String, String> params = new HashMap<>();
        params.put("From", "+15551234567");
        params.put("Body", "hi");
        Map<String, String> headers = new HashMap<>();
        headers.put("x-twilio-signature", "bad");

        when(smsChannel.validateSignature(eq("bad"), anyString(), anyMap()))
            .thenReturn(Mono.just(false));

        StepVerifier.create(controller.handleWebhook(params, headers))
            .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatusCode()))
            .verifyComplete();

        verify(smsChannel, timeout(1000)).validateSignature(eq("bad"), anyString(), anyMap());
        // filter(Boolean::booleanValue) drops the invalid signature, so nothing downstream runs.
        verify(smsChannel, never()).processInbound(any(), anyMap());
        verify(tac, never()).handleMessageContext(any());
    }

    @Test
    void handleWebhookSwallowsDownstreamErrors() {
        Map<String, String> params = new HashMap<>();
        params.put("From", "+15551234567");
        params.put("Body", "boom");
        Map<String, String> headers = new HashMap<>();

        when(smsChannel.validateSignature(any(), anyString(), anyMap()))
            .thenReturn(Mono.just(true));
        when(smsChannel.processInbound(any(InboundMessage.class), anyMap()))
            .thenReturn(Mono.error(new RuntimeException("downstream failure")));

        // onErrorResume in processWebhookAsync swallows the error; the response is still 200.
        StepVerifier.create(controller.handleWebhook(params, headers))
            .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatusCode()))
            .verifyComplete();

        verify(smsChannel, timeout(1000)).processInbound(any(InboundMessage.class), anyMap());
        verify(tac, never()).handleMessageContext(any());
    }

    @Test
    void handleWebhookReturns200EvenWhenNoChannelRegistered() {
        // RCS has no channel registered in the map -> processWebhookAsync returns Mono.empty(),
        // but routing for an unknown messaging binding maps to SMS, so register only WHATSAPP/CHAT
        // and use a From with no recognizable prefix to fall through to SMS default and remove it.
        Map<ChannelType, Channel> sparse = new EnumMap<>(ChannelType.class);
        WebhookController sparseController = new WebhookController(tac, sparse);

        Map<String, String> params = new HashMap<>();
        params.put("From", "+15551234567"); // resolves to SMS, which is absent from the map
        params.put("Body", "hi");
        Map<String, String> headers = new HashMap<>();

        StepVerifier.create(sparseController.handleWebhook(params, headers))
            .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatusCode()))
            .verifyComplete();

        verify(tac, never()).handleMessageContext(any());
        verify(smsChannel, never()).processInbound(any(), anyMap());
    }

    @Test
    void responseBodyIsEmpty() {
        Map<String, String> params = new HashMap<>();
        params.put("From", "+15551234567");
        Map<String, String> headers = new HashMap<>();
        when(smsChannel.validateSignature(any(), any(), anyMap()))
            .thenReturn(Mono.just(false));

        ResponseEntity<Void> response = controller.handleWebhook(params, headers).block();
        assertNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
