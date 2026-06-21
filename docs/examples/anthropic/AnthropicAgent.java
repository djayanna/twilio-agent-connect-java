package com.twilio.agentconnect.examples.anthropic;

import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.tools.ToolFormat;
import com.twilio.agentconnect.tools.ToolRegistry;
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
 * Anthropic Claude-powered Agent with tool use support.
 *
 * This example shows how to integrate Anthropic Claude with TAC,
 * including support for Claude's tool use feature.
 */
@SpringBootApplication
public class AnthropicAgent {

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    @Value("${anthropic.model:claude-3-5-sonnet-20241022}")
    private String model;

    private final WebClient claudeClient;
    private final ToolRegistry toolRegistry;

    public AnthropicAgent(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.claudeClient = WebClient.builder()
            .baseUrl("https://api.anthropic.com/v1")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("anthropic-version", "2023-06-01")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(AnthropicAgent.class, args);
    }

    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            System.out.println("===========================================");
            System.out.println("Anthropic Claude Agent Started!");
            System.out.println("Model: " + model);
            System.out.println("Tools: " + toolRegistry.getAllTools().size());
            System.out.println("===========================================");

            tac.onMessageReady(this::handleMessage);
        };
    }

    private Mono<OutboundMessage> handleMessage(MessageContext context) {
        String customerMessage = context.getMessage().getContent();
        System.out.println("Customer: " + customerMessage);

        // Build system prompt with memory
        String systemPrompt = MemoryPromptBuilder.compose(
            "You are a helpful and friendly customer service assistant. " +
            "Provide clear, concise responses. " +
            "If you need to escalate to a human, use the handoffToHuman tool. " +
            "Keep responses under 160 characters for SMS compatibility.",
            context.getMemory(),
            context
        );

        // Call Claude with tools
        return callClaude(systemPrompt, customerMessage)
            .doOnNext(response -> System.out.println("Agent: " + response))
            .map(response -> OutboundMessage.builder()
                .content(response)
                .conversationId(context.getConversationId())
                .build());
    }

    private Mono<String> callClaude(String systemPrompt, String userMessage) {
        // Export tools in Anthropic format
        List<Map<String, Object>> tools = toolRegistry.exportTools(ToolFormat.ANTHROPIC);

        Map<String, Object> request = Map.of(
            "model", model,
            "max_tokens", 1024,
            "system", systemPrompt,
            "messages", List.of(
                Map.of("role", "user", "content", userMessage)
            ),
            "tools", tools
        );

        return claudeClient.post()
            .uri("/messages")
            .header("x-api-key", anthropicApiKey)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> content =
                    (List<Map<String, Object>>) response.get("content");

                // Find text response (handling tool use if present)
                for (Map<String, Object> block : content) {
                    if ("text".equals(block.get("type"))) {
                        return (String) block.get("text");
                    }
                }

                return "I've processed your request.";
            })
            .onErrorResume(error -> {
                System.err.println("Claude Error: " + error.getMessage());
                return Mono.just("I'm having trouble processing your request. Please try again.");
            });
    }
}
