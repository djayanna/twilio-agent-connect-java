package com.twilio.agentconnect.examples.voice;

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
 * Voice-enabled agent using Conversation Relay.
 *
 * This example demonstrates:
 * - Voice channel configuration
 * - WebSocket-based real-time conversation
 * - Speech-to-text and text-to-speech
 * - Voice-optimized responses (shorter, conversational)
 */
@SpringBootApplication
public class VoiceAgent {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private final WebClient openAiClient;

    public VoiceAgent() {
        this.openAiClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(VoiceAgent.class, args);
    }

    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            System.out.println("===========================================");
            System.out.println("Voice Agent Started!");
            System.out.println("Voice endpoint: /twiml");
            System.out.println("WebSocket endpoint: /ws/voice");
            System.out.println("===========================================");
            System.out.println();
            System.out.println("Setup:");
            System.out.println("1. Start ngrok: ngrok http 8080");
            System.out.println("2. Configure Twilio number voice webhook:");
            System.out.println("   https://your-url.ngrok.io/twiml");
            System.out.println("3. Call your Twilio number!");
            System.out.println("===========================================");

            tac.onMessageReady(this::handleMessage);
        };
    }

    private Mono<OutboundMessage> handleMessage(MessageContext context) {
        String customerMessage = context.getMessage().getContent();
        boolean isVoice = context.getChannelType().name().equals("VOICE");

        System.out.println("[" + context.getChannelType() + "] Customer: " + customerMessage);

        // Voice-specific prompt optimization
        String systemPrompt;
        if (isVoice) {
            systemPrompt = MemoryPromptBuilder.compose(
                "You are a friendly voice assistant. " +
                "Keep responses SHORT and CONVERSATIONAL - under 40 words. " +
                "Speak naturally like a human would on the phone. " +
                "No markdown, bullet points, or formatting.",
                context.getMemory(),
                context
            );
        } else {
            systemPrompt = MemoryPromptBuilder.compose(
                "You are a helpful customer service assistant. " +
                "Keep responses under 160 characters for SMS.",
                context.getMemory(),
                context
            );
        }

        return callOpenAi(systemPrompt, customerMessage)
            .doOnNext(response -> System.out.println("[" + context.getChannelType() + "] Agent: " + response))
            .map(response -> OutboundMessage.builder()
                .content(response)
                .conversationId(context.getConversationId())
                .build());
    }

    private Mono<String> callOpenAi(String systemPrompt, String userMessage) {
        Map<String, Object> request = Map.of(
            "model", "gpt-4",
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ),
            "max_tokens", 100,  // Shorter for voice
            "temperature", 0.8  // More natural/varied
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
            .onErrorReturn("I'm having trouble processing that. Could you repeat?");
    }
}
