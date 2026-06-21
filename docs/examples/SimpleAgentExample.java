package com.twilio.agentconnect.examples;

import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

/**
 * Simple example of using Twilio Agent Connect SDK.
 */
@SpringBootApplication
public class SimpleAgentExample {

    public static void main(String[] args) {
        SpringApplication.run(SimpleAgentExample.class, args);
    }

    /**
     * Register message handler on startup.
     */
    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            // Register message ready callback
            tac.onMessageReady(this::handleMessage);

            // Register conversation ended callback
            tac.onConversationEnded(session -> {
                System.out.println("Conversation ended: " + session.getConversationId());
                return Mono.empty();
            });

            System.out.println("Twilio Agent Connect SDK initialized and ready!");
        };
    }

    /**
     * Handle incoming messages.
     * In production, this would call your LLM.
     */
    private Mono<OutboundMessage> handleMessage(MessageContext context) {
        String customerMessage = context.getMessage().getContent();
        System.out.println("Received message: " + customerMessage);

        // Build prompt with memory if available
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Customer message: ").append(customerMessage);

        if (!context.getMemory().isEmpty()) {
            promptBuilder.append("\n\nCustomer traits: ")
                        .append(context.getMemory().getTraits());

            if (!context.getMemory().getObservations().isEmpty()) {
                promptBuilder.append("\n\nRecent observations:");
                context.getMemory().getObservations().forEach(obs ->
                    promptBuilder.append("\n- ").append(obs.getContent())
                );
            }
        }

        // TODO: Call your LLM here with the prompt
        // For now, return a simple echo response
        String response = "I received your message: " + customerMessage;

        return Mono.just(OutboundMessage.builder()
            .content(response)
            .conversationId(context.getConversationId())
            .build());
    }
}
