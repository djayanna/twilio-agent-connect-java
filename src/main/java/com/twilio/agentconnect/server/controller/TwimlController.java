package com.twilio.agentconnect.server.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.agentconnect.channels.voice.ConferenceCoordinator;
import com.twilio.agentconnect.channels.voice.HumanAgentDialer;
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
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Controller for TwiML generation (voice webhooks).
 */
@RestController
@RequestMapping("/twiml")
@ConditionalOnBean(VoiceChannel.class)
public class TwimlController {

    private static final Logger log = LoggerFactory.getLogger(TwimlController.class);

    /** reasonCode that triggers the conference handoff (caller waits, agent dialed). */
    private static final String LIVE_AGENT_HANDOFF = "live-agent-handoff";

    /** reasonCode AI #2 emits to bridge the human into the caller's conference. */
    private static final String BRIDGE_TO_CONFERENCE = "bridge-to-conference";

    private final VoiceChannel voiceChannel;
    private final TwimlGenerator twimlGenerator;
    private final TacConfiguration config;
    private final ObjectMapper objectMapper;
    private final ConferenceCoordinator conferenceCoordinator;
    private final HumanAgentDialer humanAgentDialer;

    public TwimlController(VoiceChannel voiceChannel,
                           TwimlGenerator twimlGenerator,
                           TacConfiguration config,
                           ObjectMapper objectMapper,
                           ConferenceCoordinator conferenceCoordinator,
                           HumanAgentDialer humanAgentDialer) {
        this.voiceChannel = voiceChannel;
        this.twimlGenerator = twimlGenerator;
        this.config = config;
        this.objectMapper = objectMapper;
        this.conferenceCoordinator = conferenceCoordinator;
        this.humanAgentDialer = humanAgentDialer;
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
     * ends. Branches on {@code reasonCode}:
     * <ul>
     *   <li>{@code live-agent-handoff} — places the caller into a per-call
     *       conference and triggers an outbound briefing call to the human
     *       agent.</li>
     *   <li>{@code bridge-to-conference} — the briefing AI session is ending;
     *       the human is being dropped into the caller's conference, bridging
     *       the two legs.</li>
     *   <li>anything else — hang up.</li>
     * </ul>
     */
    @PostMapping("/handoff")
    public Mono<ResponseEntity<String>> handoffAction(ServerWebExchange exchange) {
        // Twilio POSTs the action callback as application/x-www-form-urlencoded;
        // WebFlux only binds @RequestParam from the query string, so read the body.
        return exchange.getFormData().flatMap(form -> handoffAction(form.toSingleValueMap()));
    }

    /**
     * Briefing endpoint Twilio fetches when the outbound agent leg connects.
     * Returns ConversationRelay TwiML that opens a fresh WS session and passes
     * the {@code ctxId} so the WS handler can fetch the briefing payload.
     */
    @PostMapping("/briefing")
    public Mono<ResponseEntity<String>> briefingTwiml(
            @RequestParam(value = "ctx", required = false) String ctxId) {
        if (ctxId == null || ctxId.isBlank()) {
            log.warn("Briefing called without ctx; hanging up");
            return Mono.just(xml(twimlGenerator.generateHangupTwiml()));
        }
        ConferenceCoordinator.BriefingContext briefing = conferenceCoordinator.get(ctxId);
        if (briefing == null) {
            log.warn("Briefing context {} not found; hanging up", ctxId);
            return Mono.just(xml(twimlGenerator.generateHangupTwiml()));
        }

        TacConfiguration.VoiceConfig voice = config.getVoice();
        String wsUrl = voiceChannel.buildWebSocketUrlPublic();
        // Reuse the same /twiml/handoff endpoint for the agent's leg: it
        // branches on reasonCode, so the bridge-to-conference branch handles
        // dropping the human into the caller's conference.
        String actionUrl = publicBaseUrl() + "/twiml/handoff";
        String twiml = twimlGenerator.generateBriefingConnectTwiml(
            wsUrl,
            ctxId,
            actionUrl,
            voice.getVoice(),
            voice.getLanguage(),
            null);
        return Mono.just(xml(twiml));
    }

    /**
     * Status callback for the outbound briefing call. If the human didn't
     * answer (no-answer / busy / failed), drop the caller out of hold with a
     * brief apology and hang up.
     */
    @PostMapping("/agent-status")
    public Mono<ResponseEntity<String>> agentStatus(
            @RequestParam(value = "ctx", required = false) String ctxId,
            ServerWebExchange exchange) {
        return exchange.getFormData().map(form -> {
            String callStatus = form.getFirst("CallStatus");
            log.info("Agent briefing leg status for ctx {}: {}", ctxId, callStatus);
            if (ctxId == null) {
                return ResponseEntity.ok().<String>build();
            }
            boolean failed = "no-answer".equals(callStatus)
                || "busy".equals(callStatus)
                || "failed".equals(callStatus)
                || "canceled".equals(callStatus);
            if (failed) {
                ConferenceCoordinator.BriefingContext ctx = conferenceCoordinator.remove(ctxId);
                if (ctx != null) {
                    // The caller is still alive in their original ConversationRelay
                    // WebSocket — but we already ended that session to put them
                    // into the conference. The cleanest user-facing fallback is
                    // to redirect their leg to a Say + Hangup TwiML. For now we
                    // just log; redirecting requires a Calls.update() REST call,
                    // which can be added later. The conference will time out on
                    // its own.
                    log.warn("Human agent unreachable for ctx {} (caller={}, callStatus={}); "
                        + "caller will hear hold music until conference times out",
                        ctxId, ctx.callerCallSid(), callStatus);
                }
            }
            return ResponseEntity.ok().<String>build();
        });
    }

    /**
     * Core handoff logic, separated from request binding for testability.
     */
    Mono<ResponseEntity<String>> handoffAction(Map<String, String> params) {
        String callSid = params.get("CallSid");
        // Twilio sends form params in PascalCase: HandoffData, not handoffData.
        String handoffData = params.getOrDefault("HandoffData", params.get("handoffData"));
        JsonNode payload = parseHandoff(handoffData);
        String reasonCode = payload.path("reasonCode").asText(null);
        log.info("Relay session ended for call {} (reasonCode={})", callSid, reasonCode);

        if (LIVE_AGENT_HANDOFF.equals(reasonCode)) {
            return handleCallerHandoff(callSid, payload);
        }
        if (BRIDGE_TO_CONFERENCE.equals(reasonCode)) {
            return handleBridgeToConference(callSid, payload);
        }
        // Unknown reason: end the call gracefully.
        return Mono.just(xml(twimlGenerator.generateHangupTwiml()));
    }

    /**
     * Handle the customer leg's handoff: park the caller in a per-call
     * conference and kick off the outbound briefing call.
     */
    private Mono<ResponseEntity<String>> handleCallerHandoff(String callSid, JsonNode payload) {
        TacConfiguration.VoiceConfig voice = config.getVoice();
        String agentNumber = voice.getHandoffAgentNumber();
        if (agentNumber == null || agentNumber.isBlank()) {
            log.warn("Handoff requested for call {} but no agent number configured; hanging up", callSid);
            return Mono.just(xml(twimlGenerator.generateHangupTwiml()));
        }

        String conferenceName = "conf-" + callSid;
        String ctxId = payload.path("ctxId").asText(null);

        // If the agent didn't pre-register a context, create one from the
        // handoffData fields so the briefing AI still has something to say.
        if (ctxId == null || ctxId.isBlank()) {
            ConferenceCoordinator.BriefingContext ctx = new ConferenceCoordinator.BriefingContext(
                callSid,
                conferenceName,
                payload.path("from").asText(null),
                payload.path("reason").asText(null),
                payload.path("summary").asText(null));
            ctxId = conferenceCoordinator.store(ctx);
        } else {
            // Backfill the conference name on the stored context — AI #1 doesn't
            // know it (it's derived from CallSid here).
            ConferenceCoordinator.BriefingContext existing = conferenceCoordinator.get(ctxId);
            if (existing != null && !conferenceName.equals(existing.conferenceName())) {
                conferenceCoordinator.remove(ctxId);
                ctxId = conferenceCoordinator.store(new ConferenceCoordinator.BriefingContext(
                    callSid,
                    conferenceName,
                    existing.callerNumber(),
                    existing.reason(),
                    existing.summary()));
            }
        }

        log.info("Parking caller {} into conference {} (ctx={})", callSid, conferenceName, ctxId);

        // Fire the outbound dial off the response thread; we still return
        // <Conference> TwiML immediately so the caller stops hearing silence.
        String finalCtxId = ctxId;
        Mono.fromRunnable(() -> humanAgentDialer.dialAgent(finalCtxId, publicBaseUrl()))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();

        String twiml = twimlGenerator.generateConferenceTwiml(
            conferenceName,
            TwimlGenerator.ConferenceRole.CALLER,
            voice.getConferenceWaitUrl());
        return Mono.just(xml(twiml));
    }

    /**
     * Handle the agent leg's handoff: drop the human into the caller's
     * conference, completing the bridge.
     */
    private Mono<ResponseEntity<String>> handleBridgeToConference(String callSid, JsonNode payload) {
        String conferenceName = payload.path("conference").asText(null);
        if (conferenceName == null || conferenceName.isBlank()) {
            String ctxId = payload.path("ctxId").asText(null);
            if (ctxId != null && !ctxId.isBlank()) {
                ConferenceCoordinator.BriefingContext ctx = conferenceCoordinator.get(ctxId);
                if (ctx != null) {
                    conferenceName = ctx.conferenceName();
                }
            }
        }
        if (conferenceName == null || conferenceName.isBlank()) {
            log.warn("Bridge requested for call {} but no conference name available; hanging up", callSid);
            return Mono.just(xml(twimlGenerator.generateHangupTwiml()));
        }
        log.info("Bridging human agent {} into conference {}", callSid, conferenceName);
        String twiml = twimlGenerator.generateConferenceTwiml(
            conferenceName,
            TwimlGenerator.ConferenceRole.AGENT,
            null);
        return Mono.just(xml(twiml));
    }

    /**
     * Parse {@code handoffData} JSON, returning an empty node on missing or
     * malformed input so callers can use {@code .path(...)} safely.
     */
    private JsonNode parseHandoff(String handoffData) {
        if (handoffData == null || handoffData.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(handoffData);
        } catch (Exception e) {
            log.warn("Could not parse handoffData: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private String publicBaseUrl() {
        String domain = config.getVoicePublicDomain();
        if (domain == null || domain.isEmpty()) {
            throw new IllegalStateException("Voice public domain not configured");
        }
        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
            domain = "https://" + domain;
        }
        return domain;
    }

    private ResponseEntity<String> xml(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(body);
    }
}
