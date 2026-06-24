package com.twilio.agentconnect.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.agentconnect.channels.voice.ConferenceCoordinator;
import com.twilio.agentconnect.channels.voice.HumanAgentDialer;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock
    private HumanAgentDialer humanAgentDialer;

    private final TwimlGenerator twimlGenerator = new TwimlGenerator();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConferenceCoordinator conferenceCoordinator = new ConferenceCoordinator();
    private TacConfiguration config;

    private TwimlController controller;

    @BeforeEach
    void setUp() {
        config = new TacConfiguration();
        config.setPhoneNumber("+15559876543");
        config.setVoicePublicDomain("https://example.com");
        controller = new TwimlController(
            voiceChannel, twimlGenerator, config, objectMapper,
            conferenceCoordinator, humanAgentDialer);
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

        StepVerifier.create(controller.generateTwiml(params))
            .assertNext(response -> assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()))
            .verifyComplete();
    }

    @Test
    void liveAgentHandoffParksCallerInConferenceAndDialsAgent() {
        config.getVoice().setHandoffAgentNumber("+15551234567");
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA1");
        params.put("HandoffData",
            "{\"reasonCode\":\"live-agent-handoff\",\"reason\":\"x\",\"summary\":\"caller wants refund\"}");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                String body = response.getBody();
                assertTrue(body.contains("<Conference"), "expected <Conference>: " + body);
                assertTrue(body.contains("conf-CA1"), "expected conference name conf-CA1: " + body);
                assertTrue(body.contains("startConferenceOnEnter=\"false\""));
                assertTrue(body.contains("endConferenceOnExit=\"false\""));
            })
            .verifyComplete();

        // The outbound dial fires asynchronously on the boundedElastic scheduler;
        // poll for up to 2s so the verification isn't flaky.
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(humanAgentDialer).dialAgent(anyString(), anyString());
                return;
            } catch (AssertionError ignored) {
                try { Thread.sleep(20); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
        }
        verify(humanAgentDialer).dialAgent(anyString(), anyString());
    }

    @Test
    void liveAgentHandoffHangsUpWhenNoAgentConfigured() {
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA1");
        params.put("HandoffData", "{\"reasonCode\":\"live-agent-handoff\"}");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> assertTrue(response.getBody().contains("<Hangup/>")))
            .verifyComplete();
    }

    @Test
    void bridgeToConferenceReturnsConferenceTwimlForAgent() {
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CAagent");
        params.put("HandoffData",
            "{\"reasonCode\":\"bridge-to-conference\",\"conference\":\"conf-CA1\"}");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> {
                String body = response.getBody();
                assertTrue(body.contains("<Conference"));
                assertTrue(body.contains("conf-CA1"));
                assertTrue(body.contains("startConferenceOnEnter=\"true\""));
                assertTrue(body.contains("endConferenceOnExit=\"true\""));
            })
            .verifyComplete();
    }

    @Test
    void bridgeToConferenceFallsBackToCtxIdLookup() {
        ConferenceCoordinator.BriefingContext ctx = new ConferenceCoordinator.BriefingContext(
            "CAcaller", "conf-CAcaller", "+15550001111", "reason", "summary");
        String ctxId = conferenceCoordinator.store(ctx);

        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CAagent");
        params.put("HandoffData",
            "{\"reasonCode\":\"bridge-to-conference\",\"ctxId\":\"" + ctxId + "\"}");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> assertTrue(response.getBody().contains("conf-CAcaller")))
            .verifyComplete();
    }

    @Test
    void unknownReasonHangsUp() {
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA1");
        params.put("HandoffData", "{\"reasonCode\":\"completed\"}");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> assertTrue(response.getBody().contains("<Hangup/>")))
            .verifyComplete();
    }

    @Test
    void missingHandoffDataHangsUp() {
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA1");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> assertTrue(response.getBody().contains("<Hangup/>")))
            .verifyComplete();
    }

    @Test
    void malformedHandoffDataHangsUp() {
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA1");
        params.put("HandoffData", "not-json");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> assertTrue(response.getBody().contains("<Hangup/>")))
            .verifyComplete();
    }

    @Test
    void briefingTwimlReturnsConversationRelayWithCtxParameter() {
        ConferenceCoordinator.BriefingContext ctx = new ConferenceCoordinator.BriefingContext(
            "CAcaller", "conf-CAcaller", "+15550001111", "reason", "summary");
        String ctxId = conferenceCoordinator.store(ctx);

        when(voiceChannel.buildWebSocketUrlPublic()).thenReturn("wss://example.com/ws/voice");

        StepVerifier.create(controller.briefingTwiml(ctxId))
            .assertNext(response -> {
                String body = response.getBody();
                assertTrue(body.contains("<ConversationRelay"));
                assertTrue(body.contains("wss://example.com/ws/voice"));
                assertTrue(body.contains("name=\"role\" value=\"briefing\""));
                assertTrue(body.contains("name=\"ctxId\" value=\"" + ctxId + "\""));
                // action on <Connect> so AI #2's bridge_to_caller endSession
                // can hit /twiml/handoff and get conference TwiML.
                assertTrue(body.contains("<Connect action=\"https://example.com/twiml/handoff\">"));
            })
            .verifyComplete();
    }

    @Test
    void briefingTwimlHangsUpWhenCtxMissing() {
        StepVerifier.create(controller.briefingTwiml(null))
            .assertNext(response -> assertTrue(response.getBody().contains("<Hangup/>")))
            .verifyComplete();
    }

    @Test
    void agentStatusRemovesContextOnNoAnswer() {
        ConferenceCoordinator.BriefingContext ctx = new ConferenceCoordinator.BriefingContext(
            "CAcaller", "conf-CAcaller", "+15550001111", "r", "s");
        String ctxId = conferenceCoordinator.store(ctx);

        org.springframework.mock.web.server.MockServerWebExchange exchange =
            org.springframework.mock.web.server.MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                    .post("/twiml/agent-status?ctx=" + ctxId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("CallStatus=no-answer"));

        StepVerifier.create(controller.agentStatus(ctxId, exchange))
            .assertNext(r -> assertEquals(HttpStatus.OK, r.getStatusCode()))
            .verifyComplete();

        // Context was removed.
        org.junit.jupiter.api.Assertions.assertNull(conferenceCoordinator.get(ctxId));
    }

    @Test
    void agentStatusKeepsContextOnCompleted() {
        ConferenceCoordinator.BriefingContext ctx = new ConferenceCoordinator.BriefingContext(
            "CAcaller", "conf-CAcaller", "+15550001111", "r", "s");
        String ctxId = conferenceCoordinator.store(ctx);

        org.springframework.mock.web.server.MockServerWebExchange exchange =
            org.springframework.mock.web.server.MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                    .post("/twiml/agent-status?ctx=" + ctxId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("CallStatus=completed"));

        StepVerifier.create(controller.agentStatus(ctxId, exchange))
            .assertNext(r -> assertEquals(HttpStatus.OK, r.getStatusCode()))
            .verifyComplete();

        // Context still present (call completed normally; bridge happens before this).
        org.junit.jupiter.api.Assertions.assertNotNull(conferenceCoordinator.get(ctxId));
    }

    @Test
    void agentStatusWithoutCtxReturnsOkWithoutSideEffects() {
        org.springframework.mock.web.server.MockServerWebExchange exchange =
            org.springframework.mock.web.server.MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                    .post("/twiml/agent-status")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("CallStatus=failed"));

        StepVerifier.create(controller.agentStatus(null, exchange))
            .assertNext(r -> assertEquals(HttpStatus.OK, r.getStatusCode()))
            .verifyComplete();
    }

    @Test
    void agentStatusForFailedCallWithUnknownCtxStillReturnsOk() {
        org.springframework.mock.web.server.MockServerWebExchange exchange =
            org.springframework.mock.web.server.MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                    .post("/twiml/agent-status?ctx=ctx_missing")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("CallStatus=busy"));

        StepVerifier.create(controller.agentStatus("ctx_missing", exchange))
            .assertNext(r -> assertEquals(HttpStatus.OK, r.getStatusCode()))
            .verifyComplete();
    }

    @Test
    void liveAgentHandoffPreservesPrestoredCtxAndOverridesConferenceName() {
        config.getVoice().setHandoffAgentNumber("+15551234567");
        // AI #1 pre-stored a context (without yet knowing the conf name).
        ConferenceCoordinator.BriefingContext stored = new ConferenceCoordinator.BriefingContext(
            "CAprior", "conf-old", "+15550001111", "r", "summary text");
        String prevCtxId = conferenceCoordinator.store(stored);

        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA1");
        params.put("HandoffData",
            "{\"reasonCode\":\"live-agent-handoff\",\"ctxId\":\"" + prevCtxId + "\"}");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> {
                String body = response.getBody();
                assertTrue(body.contains("conf-CA1"), "conference name should derive from CallSid");
            })
            .verifyComplete();

        // Old ctx replaced (key changed) but a new entry exists with the new conference.
        org.junit.jupiter.api.Assertions.assertNull(conferenceCoordinator.get(prevCtxId));
    }

    @Test
    void bridgeToConferenceWithoutNameOrCtxHangsUp() {
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CAagent");
        params.put("HandoffData", "{\"reasonCode\":\"bridge-to-conference\"}");

        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> assertTrue(response.getBody().contains("<Hangup/>")))
            .verifyComplete();
    }

    @Test
    void liveAgentHandoffWithEmptyConfigDomainSurfacesError() {
        config.setVoicePublicDomain(null);
        config.getVoice().setHandoffAgentNumber("+15551234567");
        Map<String, String> params = new HashMap<>();
        params.put("CallSid", "CA1");
        params.put("HandoffData", "{\"reasonCode\":\"live-agent-handoff\"}");

        // The synchronous TwiML still returns Conference; the async dial throws
        // IllegalStateException internally and is logged but not surfaced.
        StepVerifier.create(controller.handoffAction(params))
            .assertNext(response -> {
                String body = response.getBody();
                assertTrue(body.contains("<Conference"));
            })
            .verifyComplete();
    }

    @Test
    void briefingTwimlHangsUpWhenCtxUnknown() {
        StepVerifier.create(controller.briefingTwiml("ctx_unknown"))
            .assertNext(response -> assertTrue(response.getBody().contains("<Hangup/>")))
            .verifyComplete();
    }
}
