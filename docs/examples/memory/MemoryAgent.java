package com.twilio.agentconnect.examples.memory;

import com.twilio.agentconnect.context.model.ConversationMemory;
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
 * Example demonstrating Conversation Memory features.
 *
 * Shows how to:
 * - Use customer memory for personalized responses
 * - Build memory-enhanced prompts
 * - Display memory information
 * - Handle memory retrieval modes
 */
@SpringBootApplication
public class MemoryAgent {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private final WebClient openAiClient;

    public MemoryAgent() {
        this.openAiClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(MemoryAgent.class, args);
    }

    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            System.out.println("===========================================");
            System.out.println("Memory-Enhanced Agent Started!");
            System.out.println("Memory Store: " + System.getenv("TWILIO_MEMORY_STORE_ID"));
            System.out.println("===========================================");

            tac.onMessageReady(this::handleMessage);
        };
    }

    private Mono<OutboundMessage> handleMessage(MessageContext context) {
        String customerMessage = context.getMessage().getContent();
        ConversationMemory memory = context.getMemory();

        // Log memory information
        logMemoryInfo(memory);

        // Build system prompt with memory
        String systemPrompt = MemoryPromptBuilder.compose(
            "You are a personalized customer service assistant. " +
            "Use the customer's memory to provide tailored responses. " +
            "Reference their name, preferences, and history when relevant. " +
            "Keep responses under 160 characters for SMS.",
            memory,
            context
        );

        System.out.println("\nCustomer: " + customerMessage);
        System.out.println("Using memory: " + (memory != null));

        return callOpenAi(systemPrompt, customerMessage)
            .doOnNext(response -> System.out.println("Agent: " + response))
            .map(response -> OutboundMessage.builder()
                .content(response)
                .conversationId(context.getConversationId())
                .build());
    }

    private void logMemoryInfo(ConversationMemory memory) {
        if (memory == null) {
            System.out.println("\n[No memory available]");
            return;
        }

        System.out.println("\n=== Customer Memory ===");

        // Traits
        if (memory.getTraits() != null && !memory.getTraits().isEmpty()) {
            System.out.println("Traits:");
            memory.getTraits().forEach((key, value) ->
                System.out.println("  - " + key + ": " + value)
            );
        }

        // Observations
        if (memory.getObservations() != null && !memory.getObservations().isEmpty()) {
            System.out.println("Recent observations:");
            memory.getObservations().stream()
                .limit(3)
                .forEach(obs -> System.out.println("  - " + obs));
        }

        // Summaries
        if (memory.getSummaries() != null && !memory.getSummaries().isEmpty()) {
            System.out.println("Summaries:");
            memory.getSummaries().stream()
                .limit(2)
                .forEach(summary -> System.out.println("  - " + summary));
        }

        System.out.println("======================\n");
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
            .onErrorReturn("I'm having trouble right now. Please try again.");
    }
}
