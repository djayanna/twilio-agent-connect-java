package com.twilio.agentconnect.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.agentconnect.channels.voice.TwimlGenerator;
import com.twilio.agentconnect.channels.voice.VoiceChannel;
import com.twilio.agentconnect.core.TacConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TwimlController}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwimlControllerTest {

    @Mock
    private VoiceChannel voiceChannel;

    // Real generator + mapper so handoff TwiML output is asserted end to end.
    private final TwimlGenerator twimlGenerator = new TwimlGenerator();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TacConfiguration config;

    private TwimlController controller;

    @BeforeEach
    void setUp() {
        config = new TacConfiguration();
        config.setPhoneNumber("+15559876543");
        controller = new TwimlController(voiceChannel, twimlGenerator, config, objectMapper);
    }

    @Test
    void generateTwimlReturnsXmlBodyFromVoiceChannel() {
        String twiml = "<Response><Connect/></Response>";
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA123");

        when(voiceChannel.generateTwiml(anyMap())).thenReturn(Mono.just(twiml));

        StepVerifier.create(controller.generateTwiml(params))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(twiml, response.getBody());
                assertEquals(MediaType.APPLICATION_XML, response.getHeaders().getContentType());
            })
            .verifyComplete();

        verify(voiceChannel).generateTwiml(params);
    }

    @Test
    void generateTwimlReturns500OnError() {
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA999");

        when(voiceChannel.generateTwiml(anyMap()))
            .thenReturn(Mono.error(new RuntimeException("generation failed")));

        // onErrorReturn(internalServerError) converts the error into a 500 response.
        StepVerifier.create(controller.generateTwiml(params))
            .assertNext(response -> assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()))
            .verifyComplete();
    }

    @Test
    void generateTwimlHandlesMissingCallSid() {
        Map<String, String> params = new HashMap<>();
        String twiml = "<Response/>";
        when(voiceChannel.generateTwiml(anyMap())).thenReturn(Mono.just(twiml));

        StepVerifier.create(controller.generateTwiml(params))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals(twiml, response.getBody());
            })
            .verifyComplete();
    }

    @Test
    void handoffActionDialsAgentOnLiveAgentHandoff() {
        config.getVoice().setHandoffAgentNumber("+15551234567");
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA1");
        params.put("handoffData", "{\"reasonCode\":\"live-agent-handoff\",\"reason\":\"x\"}");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                String body = response.getBody();
                assertTrue(body.contains("<Dial callerId=\"+15559876543\">"));
                assertTrue(body.contains("<Number>+15551234567</Number>"));
            })
            .verifyComplete();
    }

    @Test
    void handoffActionHangsUpWhenReasonNotHandoff() {
        config.getVoice().setHandoffAgentNumber("+15551234567");
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA1");
        params.put("handoffData", "{\"reasonCode\":\"completed\"}");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> assertTrue(response.getBody().contains("<Hangup/>")))
            .verifyComplete();
    }

    @Test
    void handoffActionHangsUpWhenNoAgentConfigured() {
        // handoffAgentNumber left null
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA1");
        params.put("handoffData", "{\"reasonCode\":\"live-agent-handoff\"}");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> assertTrue(response.getBody().contains("<Hangup/>")))
            .verifyComplete();
    }

    @Test
    void handoffActionHangsUpOnMissingOrMalformedHandoffData() {
        config.getVoice().setHandoffAgentNumber("+15551234567");

        Map<String, String> missing = new HashMap<>();
        missing.put("CallSid", "CA1");
        StepVerifier.create(controller.handoffAction(missing))
            .assertNext(response -> assertTrue(response.getBody().contains("<Hangup/>")))
            .verifyComplete();

        Map<String, String> malformed = new HashMap<>();
        malformed.put("CallSid", "CA1");
        malformed.put("handoffData", "not-json");
        StepVerifier.create(controller.handoffAction(malformed))
            .assertNext(response -> assertTrue(response.getBody().contains("<Hangup/>")))
            .verifyComplete();
    }
}
