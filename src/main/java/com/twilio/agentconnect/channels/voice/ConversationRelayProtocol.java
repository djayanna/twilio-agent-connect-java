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
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                "type", "text",
                "token", text,
                "last", true
            ));
        } catch (Exception e) {
            log.error("Error building response message", e);
            throw new RuntimeException("Failed to build response message", e);
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
}
