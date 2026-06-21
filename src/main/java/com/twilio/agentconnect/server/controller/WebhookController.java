package com.twilio.agentconnect.server.controller;


import com.twilio.agentconnect.channels.Channel;
import com.twilio.agentconnect.context.model.InboundMessage;
import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Controller for handling Twilio webhook requests.
 * Receives messages from all messaging channels (SMS, WhatsApp, RCS, Chat).
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final TwilioAgentConnect tac;
    private final Map<ChannelType, Channel> channels;

    public WebhookController(TwilioAgentConnect tac,
                            Map<ChannelType, Channel> channels) {
        this.tac = tac;
        this.channels = channels;
    }

    /**
     * Handle incoming webhook from Twilio Conversations.
     * This is a fire-and-forget endpoint that immediately returns 200 OK.
     */
    @PostMapping
    public Mono<ResponseEntity<Void>> handleWebhook(
            @RequestParam Map<String, String> params,
            @RequestHeader Map<String, String> headers) {

        log.debug("Received webhook with params: {}", params.keySet());

        // Fire-and-forget processing
        processWebhookAsync(params, headers).subscribe();

        // Return 200 OK immediately
        return Mono.just(ResponseEntity.ok().build());
    }

    /**
     * Process webhook asynchronously.
     */
    private Mono<Void> processWebhookAsync(
            Map<String, String> params,
            Map<String, String> headers) {

        return Mono.defer(() -> {
            // Detect channel type from params
            ChannelType channelType = detectChannelType(params);
            Channel channel = channels.get(channelType);

            if (channel == null) {
                log.warn("No channel found for type: {}", channelType);
                return Mono.empty();
            }

            // Build inbound message
            InboundMessage message = buildInboundMessage(params, channelType);

            // Validate signature
            String signature = headers.get("x-twilio-signature");
            String url = buildFullUrl(params);

            return channel.validateSignature(signature, url, params)
                .filter(Boolean::booleanValue)
                .flatMap(valid -> channel.processInbound(message, params))
                .flatMap(tac::handleMessageContext)
                .then()
                .onErrorResume(ex -> {
                    log.error("Error processing webhook", ex);
                    return Mono.empty();
                });
        });
    }

    /**
     * Detect channel type from webhook parameters.
     */
    private ChannelType detectChannelType(Map<String, String> params) {
        String from = params.get("From");
        if (from != null) {
            if (from.startsWith("whatsapp:")) {
                return ChannelType.WHATSAPP;
            } else if (from.startsWith("+")) {
                return ChannelType.SMS;
            }
        }

        String messagingBinding = params.get("MessagingBinding.Type");
        if (messagingBinding != null) {
            return switch (messagingBinding.toLowerCase()) {
                case "whatsapp" -> ChannelType.WHATSAPP;
                case "sms" -> ChannelType.SMS;
                case "chat" -> ChannelType.CHAT;
                default -> ChannelType.SMS;
            };
        }

        return ChannelType.SMS;
    }

    /**
     * Build inbound message from webhook parameters.
     */
    private InboundMessage buildInboundMessage(
            Map<String, String> params,
            ChannelType channelType) {

        return InboundMessage.builder()
            .content(params.get("Body"))
            .channelType(channelType)
            .conversationId(params.get("ConversationSid"))
            .messageSid(params.get("MessageSid"))
            .participantSid(params.get("ParticipantSid"))
            .from(params.get("From"))
            .to(params.get("To"))
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Build full URL for signature validation.
     */
    private String buildFullUrl(Map<String, String> params) {
        // In production, this would be constructed from the actual request URL
        // For now, we'll use a placeholder that should be overridden
        return "https://your-domain.com/webhook";
    }
}
