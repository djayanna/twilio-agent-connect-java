package com.twilio.agentconnect.channels.voice;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Protocol handler for Conversation Relay WebSocket messages.
 */
@Component
public class ConversationRelayProtocol {

    private static final Logger log = LoggerFactory.getLogger(ConversationRelayProtocol.class);

    private final ObjectMapper objectMapper;

    public ConversationRelayProtocol(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse incoming WebSocket message.
     */
    public Mono<ConversationRelayMessage> parseMessage(String json) {
        return Mono.fromCallable(() -> {
            JsonNode root = objectMapper.readTree(json);
            // Conversation Relay sends the discriminator in the "type" field.
            String eventType = root.path("type").asText();

            ConversationRelayMessage.MessageType type = switch (eventType) {
                case "setup" -> ConversationRelayMessage.MessageType.SETUP;
                case "prompt" -> ConversationRelayMessage.MessageType.PROMPT;
                case "interrupt" -> ConversationRelayMessage.MessageType.INTERRUPT;
                case "mark" -> ConversationRelayMessage.MessageType.MARK;
                default -> null;
            };

            // Unsupported types (dtmf, error, etc.) are ignored rather than
            // failing the WebSocket; returning null yields an empty Mono.
            if (type == null) {
                log.debug("Ignoring unsupported Conversation Relay message type: '{}'", eventType);
                return null;
            }

            ConversationRelayMessage.Builder builder =
                ConversationRelayMessage.builder().type(type);

            // Extract common fields
            if (root.has("callSid")) {
                builder.callSid(root.path("callSid").asText());
            }

            // Extract type-specific fields
            switch (type) {
                case SETUP:
                    builder.from(root.path("from").asText())
                           .to(root.path("to").asText());
                    JsonNode custom = root.path("customParameters");
                    if (custom.isObject()) {
                        java.util.Map<String, String> params = new java.util.HashMap<>();
                        custom.fields().forEachRemaining(e -> params.put(e.getKey(), e.getValue().asText()));
                        builder.customParameters(params);
                    }
                    break;
                case PROMPT:
                    // "voicePrompt" is the transcribed speech as a plain string.
                    builder.text(root.path("voicePrompt").asText());
                    break;
                case MARK:
                    builder.markName(root.path("mark").asText());
                    break;
                default:
                    break;
            }

            return builder.build();
        });
    }

    /**
     * Build a text token message to send TTS back to Conversation Relay.
     *
     * <p>Non-streaming mode: the full response is sent as a single token with
     * {@code last=true}, which signals Conversation Relay to play the audio.
     */
    public String buildResponseMessage(String text) {
        return buildTokenMessage(text, true);
    }

    /**
     * Build a streaming text token message.
     *
     * <p>Send each token with {@code last=false} as it arrives, then a final
     * message with {@code last=true} to signal the response is complete. Twilio
     * begins TTS playback as tokens stream in.
     *
     * @param token the text token (may be empty for the terminal {@code last=true} frame)
     * @param last  whether this is the final token of the response
     */
    public String buildTokenMessage(String token, boolean last) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                "type", "text",
                "token", token == null ? "" : token,
                "last", last
            ));
        } catch (Exception e) {
            log.error("Error building token message", e);
            throw new RuntimeException("Failed to build token message", e);
        }
    }

    /**
     * Build an interrupt message to cancel ongoing TTS playback.
     */
    public String buildClearMessage() {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                "type", "interrupt"
            ));
        } catch (Exception e) {
            log.error("Error building clear message", e);
            throw new RuntimeException("Failed to build clear message", e);
        }
    }

    /**
     * Build an "end" message that terminates the Conversation Relay session.
     *
     * <p>When sent, Twilio ends the {@code <ConversationRelay>} verb and POSTs to
     * its {@code action} URL with the call details and {@code handoffData}. That
     * endpoint returns follow-on TwiML (e.g. {@code <Dial>} to a human agent).
     *
     * @param handoffData a JSON string passed through to the action URL, e.g.
     *                    {@code {"reasonCode":"live-agent-handoff","reason":"..."}}.
     *                    When null, the end message is sent without handoffData.
     */
    public String buildEndMessage(String handoffData) {
        try {
            java.util.Map<String, Object> message = new java.util.HashMap<>();
            message.put("type", "end");
            if (handoffData != null) {
                // handoffData is a JSON *string* per the ConversationRelay spec.
                message.put("handoffData", handoffData);
            }
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Error building end message", e);
            throw new RuntimeException("Failed to build end message", e);
        }
    }
}
