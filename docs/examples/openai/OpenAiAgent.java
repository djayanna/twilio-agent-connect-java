package com.twilio.agentconnect.examples.openai;

import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.util.MemoryPromptBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * OpenAI-powered Agent using GPT-4.
 *
 * This example shows how to integrate OpenAI's GPT models with TAC.
 * The agent uses Conversation Memory to provide personalized responses.
 */
@SpringBootApplication
public class OpenAiAgent {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4}")
    private String model;

    private final WebClient openAiClient;

    public OpenAiAgent() {
        this.openAiClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(OpenAiAgent.class, args);
    }

    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            System.out.println("===========================================");
            System.out.println("OpenAI Agent Started!");
            System.out.println("Model: " + model);
            System.out.println("===========================================");

            tac.onMessageReady(this::handleMessage);
        };
    }

    private Mono<OutboundMessage> handleMessage(MessageContext context) {
        String customerMessage = context.getMessage().getContent();
        System.out.println("Customer: " + customerMessage);

        // Build prompt with memory context
        String systemPrompt = MemoryPromptBuilder.compose(
            "You are a helpful customer service assistant. " +
            "Be friendly, concise, and helpful. " +
            "Keep responses under 160 characters for SMS compatibility.",
            context.getMemory(),
            context
        );

        // Call OpenAI
        return callOpenAi(systemPrompt, customerMessage)
            .doOnNext(response -> System.out.println("Agent: " + response))
            .map(response -> OutboundMessage.builder()
                .content(response)
                .conversationId(context.getConversationId())
                .build());
    }

    private Mono<String> callOpenAi(String systemPrompt, String userMessage) {
        Map<String, Object> request = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ),
            "max_tokens", 150,
            "temperature", 0.7
        );

        return openAiClient.post()
            .uri("/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
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
                return Mono.just("I'm having trouble processing your request. Please try again.");
            });
    }
}
