package com.twilio.agentconnect.server.controller;


import com.twilio.agentconnect.channels.voice.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    private final VoiceChannel voiceChannel;

    public TwimlController(VoiceChannel voiceChannel) {
        this.voiceChannel = voiceChannel;
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
}
