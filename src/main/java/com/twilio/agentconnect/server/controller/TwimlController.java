package com.twilio.agentconnect.server.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.agentconnect.channels.voice.TwimlGenerator;
import com.twilio.agentconnect.channels.voice.VoiceChannel;
import com.twilio.agentconnect.core.TacConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controller for TwiML generation (voice webhooks).
 */
@RestController
@RequestMapping("/twiml")
@ConditionalOnBean(VoiceChannel.class)
public class TwimlController {

    private static final Logger log = LoggerFactory.getLogger(TwimlController.class);

    /** reasonCode in handoffData that triggers a transfer to a human agent. */
    private static final String LIVE_AGENT_HANDOFF = "live-agent-handoff";

    private final VoiceChannel voiceChannel;
    private final TwimlGenerator twimlGenerator;
    private final TacConfiguration config;
    private final ObjectMapper objectMapper;

    public TwimlController(VoiceChannel voiceChannel,
                           TwimlGenerator twimlGenerator,
                           TacConfiguration config,
                           ObjectMapper objectMapper) {
        this.voiceChannel = voiceChannel;
        this.twimlGenerator = twimlGenerator;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate TwiML for incoming voice call.
     */
    @PostMapping
    public Mono<ResponseEntity<String>> generateTwiml(
            @RequestParam Map<String, String> params) {

        String callSid = params.get("CallSid");
        log.info("Received voice webhook for call: {}", callSid);

        return voiceChannel.generateTwiml(params)
            .map(twiml -> ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(twiml))
            .doOnError(error -> log.error("Error generating TwiML", error))
            .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    /**
     * Conversation Relay {@code action} callback, invoked when the relay session
     * ends. On a {@code live-agent-handoff} reason it returns TwiML that dials a
     * human agent; otherwise it returns an empty response (Twilio hangs up).
     */
    @PostMapping("/handoff")
    public Mono<ResponseEntity<String>> handoffAction(ServerWebExchange exchange) {
        // Twilio POSTs the action callback as application/x-www-form-urlencoded;
        // WebFlux only binds @RequestParam from the query string, so read the body.
        return exchange.getFormData().flatMap(form -> handoffAction(form.toSingleValueMap()));
    }

    /**
     * Core handoff logic, separated from request binding for testability.
     */
    Mono<ResponseEntity<String>> handoffAction(Map<String, String> params) {
        String callSid = params.get("CallSid");
        // Twilio's <Connect> action callback sends PascalCase params, so the
        // field is "HandoffData" (not "handoffData"). Accept either for safety.
        String handoffData = params.getOrDefault("HandoffData", params.get("handoffData"));
        String reasonCode = extractReasonCode(handoffData);
        log.info("Relay session ended for call {} (reasonCode={})", callSid, reasonCode);

        String agentNumber = config.getVoice().getHandoffAgentNumber();

        if (LIVE_AGENT_HANDOFF.equals(reasonCode)
                && agentNumber != null && !agentNumber.isBlank()) {
            log.info("Transferring call {} to human agent {}", callSid, agentNumber);
            String twiml = twimlGenerator.generateDialTwiml(agentNumber, config.getPhoneNumber());
            return Mono.just(xml(twiml));
        }

        // Not a handoff (or no agent configured): end the call gracefully.
        return Mono.just(xml(twimlGenerator.generateHangupTwiml()));
    }

    /**
     * Extract {@code reasonCode} from the handoffData JSON string, tolerating
     * absent/blank/malformed input (returns null).
     */
    private String extractReasonCode(String handoffData) {
        if (handoffData == null || handoffData.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(handoffData).path("reasonCode").asText(null);
        } catch (Exception e) {
            log.warn("Could not parse handoffData: {}", e.getMessage());
            return null;
        }
    }

    private ResponseEntity<String> xml(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(body);
    }
}
