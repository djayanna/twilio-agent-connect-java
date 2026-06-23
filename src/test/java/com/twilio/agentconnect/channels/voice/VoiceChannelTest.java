package com.twilio.agentconnect.channels.voice;

import com.twilio.agentconnect.cache.IdempotencyCache;
import com.twilio.agentconnect.context.model.InboundMessage;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.core.TacConfiguration;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.validation.TwilioSignatureValidator;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VoiceChannel}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoiceChannelTest {

    @Mock
    private TwilioAgentConnect tac;

    @Mock
    private TwilioSignatureValidator signatureValidator;

    @Mock
    private TacConfiguration config;

    @Mock
    private TwimlGenerator twimlGenerator;

    @Mock
    private ConversationRelayProtocol relayProtocol;

    @Mock
    private IdempotencyCache idempotencyCache;

    private VoiceChannel voiceChannel;

    @BeforeEach
    void setUp() {
        voiceChannel = new VoiceChannel(
            tac, signatureValidator, config, twimlGenerator, relayProtocol, idempotencyCache);
    }

    @Test
    void getChannelTypeIsVoice() {
        assertEquals(ChannelType.VOICE, voiceChannel.getChannelType());
    }

    @Test
    void processInboundNewMessageDelegatesToTac() {
        InboundMessage message = InboundMessage.builder()
            .content("hi")
            .conversationId("CA1")
            .channelType(ChannelType.VOICE)
            .build();
        MessageContext context = MessageContext.builder().conversationId("CA1").build();

        when(idempotencyCache.checkAndSet("tok")).thenReturn(Mono.just(true));
        when(tac.processInboundMessage(eq(ChannelType.VOICE), eq(message), any()))
            .thenReturn(Mono.just(context));

        StepVerifier.create(
                voiceChannel.processInbound(message, Map.of("i-twilio-idempotency-token", "tok")))
            .expectNext(context)
            .verifyComplete();

        verify(tac).processInboundMessage(eq(ChannelType.VOICE), eq(message), any());
    }

    @Test
    void processInboundDuplicateMessageIsSkipped() {
        InboundMessage message = InboundMessage.builder().content("hi").build();

        when(idempotencyCache.checkAndSet("tok")).thenReturn(Mono.just(false));

        StepVerifier.create(
                voiceChannel.processInbound(message, Map.of("i-twilio-idempotency-token", "tok")))
            .verifyComplete();

        verify(tac, never()).processInboundMessage(any(), any(), any());
    }

    @Test
    void processInboundEmptyCacheResultIsSkipped() {
        InboundMessage message = InboundMessage.builder().content("hi").build();

        when(idempotencyCache.checkAndSet(any())).thenReturn(Mono.empty());

        StepVerifier.create(voiceChannel.processInbound(message, Map.of()))
            .verifyComplete();

        verify(tac, never()).processInboundMessage(any(), any(), any());
    }

    @Test
    void sendMessageDelegatesToTac() {
        OutboundMessage outbound = OutboundMessage.builder()
            .content("reply")
            .conversationId("CA1")
            .build();
        when(tac.sendMessage("CA1", "reply", ChannelType.VOICE)).thenReturn(Mono.just(outbound));

        StepVerifier.create(voiceChannel.sendMessage("CA1", "reply"))
            .expectNext(outbound)
            .verifyComplete();

        verify(tac).sendMessage("CA1", "reply", ChannelType.VOICE);
    }

    @Test
    void generateTwimlBuildsWssUrlAndPassesVoiceConfig() {
        TacConfiguration.VoiceConfig voiceConfig = new TacConfiguration.VoiceConfig();
        voiceConfig.setVoice("en-US-Journey-O");
        voiceConfig.setLanguage("en-US");
        voiceConfig.setWelcomeGreeting("Hi");

        when(config.getVoicePublicDomain()).thenReturn("example.com");
        when(config.getConversationConfigurationId()).thenReturn("config_123");
        when(config.getVoice()).thenReturn(voiceConfig);
        when(twimlGenerator.generateConnectTwiml(
                anyString(), anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn("<Response/>");

        StepVerifier.create(voiceChannel.generateTwiml(Map.of("CallSid", "CA1")))
            .expectNext("<Response/>")
            .verifyComplete();

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(twimlGenerator).generateConnectTwiml(
            urlCaptor.capture(),
            eq("config_123"),
            eq("en-US-Journey-O"),
            eq("en-US"),
            eq("Hi"),
            any());

        assertEquals("wss://example.com/ws/voice", urlCaptor.getValue());
    }

    @Test
    void generateTwimlConvertsHttpsDomainToWss() {
        TacConfiguration.VoiceConfig voiceConfig = new TacConfiguration.VoiceConfig();

        when(config.getVoicePublicDomain()).thenReturn("https://my-host.ngrok.io");
        when(config.getConversationConfigurationId()).thenReturn(null);
        when(config.getVoice()).thenReturn(voiceConfig);
        when(twimlGenerator.generateConnectTwiml(
                anyString(), any(), any(), any(), any(), any()))
            .thenReturn("<Response/>");

        StepVerifier.create(voiceChannel.generateTwiml(Map.of("CallSid", "CA1")))
            .expectNext("<Response/>")
            .verifyComplete();

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(twimlGenerator).generateConnectTwiml(
            urlCaptor.capture(), any(), any(), any(), any(), any());

        assertEquals("wss://my-host.ngrok.io/ws/voice", urlCaptor.getValue());
    }

    @Test
    void generateTwimlThrowsWhenDomainNotConfigured() {
        when(config.getVoicePublicDomain()).thenReturn(null);

        // The exception is thrown eagerly while building the URL, before the Mono.
        assertThrows(IllegalStateException.class,
            () -> voiceChannel.generateTwiml(Map.of("CallSid", "CA1")));
    }

    @Test
    void validateSignatureDelegatesToValidator() {
        when(signatureValidator.validate("sig", "url", Map.of())).thenReturn(true);

        StepVerifier.create(voiceChannel.validateSignature("sig", "url", Map.of()))
            .expectNext(true)
            .verifyComplete();
    }
}
