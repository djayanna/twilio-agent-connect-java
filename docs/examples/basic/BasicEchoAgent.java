package com.twilio.agentconnect.examples.basic;

import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

/**
 * Basic Echo Agent - the simplest possible TAC example.
 *
 * This agent simply echoes back whatever message the customer sends.
 * Perfect for testing your TAC setup and Twilio configuration.
 */
@SpringBootApplication
public class BasicEchoAgent {

    public static void main(String[] args) {
        SpringApplication.run(BasicEchoAgent.class, args);
    }

    /**
     * Register the message handler when the application starts.
     */
    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            System.out.println("===========================================");
            System.out.println("Basic Echo Agent Started!");
            System.out.println("Send an SMS to your Twilio number to test");
            System.out.println("===========================================");

            // Register message ready callback
            tac.onMessageReady(this::handleMessage);

            // Optionally register conversation end callback
            tac.onConversationEnded(session -> {
                System.out.println("Conversation ended: " + session.getConversationId());
                return Mono.empty();
            });
        };
    }

    /**
     * Handle incoming messages from customers.
     *
     * @param context Contains the message, session, and memory
     * @return Response to send back to the customer
     */
    private Mono<OutboundMessage> handleMessage(MessageContext context) {
        // Get the customer's message
        String customerMessage = context.getMessage().getContent();
        String from = context.getMessage().getFrom();

        System.out.println("Received message from " + from + ": " + customerMessage);

        // Create echo response
        String response = "Echo: " + customerMessage;

        // Build and return the response
        return Mono.just(OutboundMessage.builder()
            .content(response)
            .conversationId(context.getConversationId())
            .build());
    }
}
