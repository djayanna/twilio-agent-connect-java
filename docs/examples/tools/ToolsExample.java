package com.twilio.agentconnect.examples.tools;

import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.session.Session;
import com.twilio.agentconnect.tools.*;
import com.twilio.agentconnect.util.MemoryPromptBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Example demonstrating custom tools with TAC.
 *
 * Shows how to:
 * - Create custom tools with @TacTool
 * - Use tool parameters with @TacToolParam
 * - Inject session/context with @InjectSession/@InjectContext
 * - Export tools to OpenAI/Anthropic format
 * - Handle tool execution
 */
@SpringBootApplication
public class ToolsExample {

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    private final WebClient claudeClient;
    private final ToolRegistry toolRegistry;

    public ToolsExample(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.claudeClient = WebClient.builder()
            .baseUrl("https://api.anthropic.com/v1")
            .defaultHeader("anthropic-version", "2023-06-01")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(ToolsExample.class, args);
    }

    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            System.out.println("===========================================");
            System.out.println("Tools Example Started!");
            System.out.println("Available tools:");
            toolRegistry.getAllTools().forEach(tool ->
                System.out.println("  - " + tool.getName() + ": " + tool.getDescription())
            );
            System.out.println("===========================================");

            tac.onMessageReady(this::handleMessage);
        };
    }

    private Mono<OutboundMessage> handleMessage(MessageContext context) {
        String customerMessage = context.getMessage().getContent();

        String systemPrompt = MemoryPromptBuilder.compose(
            "You are a customer service assistant with access to tools. " +
            "Use checkOrderStatus to look up orders. " +
            "Use handoffToHuman when you can't help or customer requests human agent.",
            context.getMemory(),
            context
        );

        // In a real implementation, this would:
        // 1. Call LLM with tools
        // 2. If LLM wants to use tool, execute it
        // 3. Send tool result back to LLM
        // 4. Get final response

        return Mono.just(OutboundMessage.builder()
            .content("I can help you check order status or connect you to a human agent. What would you like?")
            .conversationId(context.getConversationId())
            .build());
    }
}

/**
 * Custom tool for checking order status.
 * This demonstrates how to create tools that interact with your systems.
 */
@Component
class OrderLookupTool {

    @TacTool(description = "Check the status of a customer order by order number")
    public Mono<String> checkOrderStatus(
            @TacToolParam(description = "Order number to look up") String orderNumber,
            @InjectSession Session session,
            @InjectContext MessageContext context) {

        System.out.println("Looking up order: " + orderNumber);

        // In real implementation, this would query your database/API
        // For demo, return mock data
        return Mono.just(String.format(
            "Order %s: Status = Shipped, " +
            "Tracking = 1Z999AA10123456784, " +
            "Expected delivery: Tomorrow",
            orderNumber
        ));
    }

    @TacTool(description = "Search for orders by customer email address")
    public Mono<String> searchOrdersByEmail(
            @TacToolParam(description = "Customer email address") String email,
            @TacToolParam(description = "Maximum number of orders to return", required = false)
            Integer limit,
            @InjectSession Session session) {

        int maxResults = limit != null ? limit : 5;
        System.out.println("Searching orders for: " + email + " (limit: " + maxResults + ")");

        // Mock response
        return Mono.just(String.format(
            "Found 2 orders for %s:\n" +
            "1. Order #12345 - Delivered\n" +
            "2. Order #12346 - In Transit",
            email
        ));
    }
}

/**
 * Custom tool for updating customer preferences.
 */
@Component
class CustomerPreferencesTool {

    @TacTool(description = "Update customer notification preferences")
    public Mono<String> updatePreferences(
            @TacToolParam(description = "Preference type: email, sms, or push")
            String preferenceType,
            @TacToolParam(description = "Enable or disable: true or false")
            boolean enabled,
            @InjectSession Session session,
            @InjectContext MessageContext context) {

        String profileId = context.getProfileId();
        System.out.println("Updating " + preferenceType + " preference for " + profileId + " to " + enabled);

        // In real implementation, update database
        return Mono.just(String.format(
            "%s notifications have been %s for your account.",
            preferenceType.toUpperCase(),
            enabled ? "enabled" : "disabled"
        ));
    }
}
