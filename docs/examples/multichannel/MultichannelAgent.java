package com.twilio.agentconnect.examples.multichannel;

import com.twilio.agentconnect.channels.ChannelType;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.util.MemoryPromptBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Multichannel agent that handles Voice, SMS, WhatsApp, and RCS.
 *
 * This example demonstrates:
 * - Single agent for all channels
 * - Channel-specific response optimization
 * - Consistent experience across channels
 * - Channel detection and adaptation
 */
@SpringBootApplication
public class MultichannelAgent {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private final WebClient openAiClient;

    public MultichannelAgent() {
        this.openAiClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(MultichannelAgent.class, args);
    }

    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            System.out.println("===========================================");
            System.out.println("Multichannel Agent Started!");
            System.out.println("Supports: Voice, SMS, WhatsApp, RCS");
            System.out.println("===========================================");
            System.out.println();
            System.out.println("Endpoints:");
            System.out.println("  Voice TwiML: /twiml");
            System.out.println("  Voice WS: /ws/voice");
            System.out.println("  Messaging: /webhook");
            System.out.println("===========================================");

            tac.onMessageReady(this::handleMessage);
        };
    }

    private Mono<OutboundMessage> handleMessage(MessageContext context) {
        ChannelType channel = context.getChannelType();
        String customerMessage = context.getMessage().getContent();

        System.out.println("\n[" + channel + "] Customer: " + customerMessage);

        // Build channel-appropriate prompt
        String systemPrompt = buildChannelPrompt(channel, context);

        return callOpenAi(systemPrompt, customerMessage)
            .map(response -> formatResponseForChannel(response, channel))
            .doOnNext(response ->
                System.out.println("[" + channel + "] Agent: " + response)
            )
            .map(response -> OutboundMessage.builder()
                .content(response)
                .conversationId(context.getConversationId())
                .build());
    }

    private String buildChannelPrompt(ChannelType channel, MessageContext context) {
        String basePrompt = "You are a helpful customer service assistant for TechCorp. " +
            "You can check orders, answer questions, and escalate to humans if needed.";

        // Channel-specific optimization
        String channelGuidance = switch (channel) {
            case VOICE -> "Keep responses VERY SHORT (under 40 words). " +
                "Be conversational and natural. No formatting or special characters.";
            case SMS -> "Keep responses under 160 characters for single SMS. " +
                "Be concise but friendly. No markdown.";
            case WHATSAPP -> "You can use emojis 😊 and be slightly more casual. " +
                "Responses can be longer (up to 300 chars). Markdown formatting is supported.";
            case RCS -> "Rich formatting available. You can suggest actions with buttons. " +
                "Use emojis appropriately. Up to 500 chars.";
            case CHAT -> "Web chat format. Can be longer and more detailed. " +
                "Use formatting for clarity.";
            default -> "Keep responses clear and concise.";
        };

        return MemoryPromptBuilder.compose(
            basePrompt + " " + channelGuidance,
            context.getMemory(),
            context
        );
    }

    private String formatResponseForChannel(String response, ChannelType channel) {
        return switch (channel) {
            case VOICE -> {
                // Remove any markdown that might have slipped through
                String cleaned = response.replaceAll("[*_`]", "");
                // Truncate if too long (safety check)
                yield cleaned.length() > 200 ? cleaned.substring(0, 197) + "..." : cleaned;
            }
            case SMS -> {
                // Ensure single SMS (160 chars)
                String cleaned = response.replaceAll("[*_`]", "");
                yield cleaned.length() > 160 ? cleaned.substring(0, 157) + "..." : cleaned;
            }
            case WHATSAPP -> {
                // WhatsApp supports markdown, keep it
                // Limit to ~300 chars
                yield response.length() > 300 ? response.substring(0, 297) + "..." : response;
            }
            case RCS -> {
                // RCS supports rich content
                // Could add button suggestions here
                yield response.length() > 500 ? response.substring(0, 497) + "..." : response;
            }
            case CHAT -> response;  // No special formatting needed
            default -> response;
        };
    }

    private Mono<String> callOpenAi(String systemPrompt, String userMessage) {
        Map<String, Object> request = Map.of(
            "model", "gpt-4",
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ),
            "max_tokens", 150,
            "temperature", 0.7
        );

        return openAiClient.post()
            .uri("/chat/completions")
            .header("Authorization", "Bearer " + openAiApiKey)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.get("choices");
                @SuppressWarnings("unchecked")
                Map<String, Object> message =
                    (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            })
            .onErrorResume(error -> {
                System.err.println("OpenAI Error: " + error.getMessage());
                return Mono.just("I'm having trouble right now. Please try again shortly.");
            });
    }
}
