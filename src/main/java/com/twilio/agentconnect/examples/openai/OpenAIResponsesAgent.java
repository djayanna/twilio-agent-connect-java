package com.twilio.agentconnect.examples.openai;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import com.twilio.agentconnect.callback.MessageReadyCallback;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.MemoryResponse;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.util.MemoryPromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example: Using OpenAI Chat Completions API with TAC Memory Injection
 *
 * Demonstrates how to build an AI agent that:
 * - Uses OpenAI's Chat Completions API for generating responses
 * - Integrates TAC Memory for customer context and conversation history
 * - Maintains conversation history across multiple turns
 * - Handles both Voice and SMS channels
 *
 * Based on: https://github.com/twilio/twilio-agent-connect-python/blob/main/getting_started/examples/partners/openai_responses_api.py
 */
@SpringBootApplication(scanBasePackages = "com.twilio.agentconnect")
public class OpenAIResponsesAgent {

    private static final Logger log = LoggerFactory.getLogger(OpenAIResponsesAgent.class);

    // System instructions for the AI agent
    private static final String SYSTEM_INSTRUCTIONS =
        "You are a customer service agent speaking with a user over voice or SMS. " +
        "Keep responses short and conversational — a sentence or two. " +
        "Do not use markdown, asterisks, bullets, or emojis; your words will be " +
        "spoken aloud or sent as plain text.";

    // Store conversation history per conversation ID
    // This maintains context across multiple turns in a conversation
    private final Map<String, List<ChatMessage>> conversationHistory = new ConcurrentHashMap<>();

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    public static void main(String[] args) {
        SpringApplication.run(OpenAIResponsesAgent.class, args);
    }

    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            // Validate OpenAI API key
            if (openAiApiKey == null || openAiApiKey.isEmpty()) {
                log.error("❌ OPENAI_API_KEY is not set!");
                log.error("Set it in .env file: OPENAI_API_KEY=sk-...");
                System.exit(1);
            }

            // Register message handler callback
            // TAC will invoke this function whenever a message needs processing
            tac.onMessageReady(createMessageHandler());

            log.info("🤖 OpenAI Agent is ready!");
            log.info("📞 Voice endpoint: http://localhost:8080/twiml");
            log.info("💬 Messaging webhook: http://localhost:8080/webhook");
            log.info("🔌 WebSocket endpoint: ws://localhost:8080/voice/ws");
        };
    }

    /**
     * Creates the message handler callback that processes customer messages.
     */
    private MessageReadyCallback createMessageHandler() {
        // Initialize OpenAI client
        final OpenAiService openAiService = new OpenAiService(openAiApiKey, Duration.ofSeconds(30));

        return (MessageContext context) -> {
            String userMessage = context.getMessage().getContent();
            String conversationId = context.getConversationId();
            MemoryResponse memory = context.getMemory();

            log.info("📨 Received message: {} (conversation: {})", userMessage, conversationId);

            return Mono.fromCallable(() -> {
                try {
                    // Conversation history holds ONLY the user/assistant turns.
                    // The system message (with memory) is built fresh on each
                    // call below, so memory is re-injected every turn.
                    List<ChatMessage> history =
                        conversationHistory.computeIfAbsent(conversationId, k -> new ArrayList<>());

                    // Add user message to conversation history
                    history.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));

                    // Build the request messages: a fresh system message carrying
                    // the current memory context, followed by the conversation so
                    // far. This mirrors the Python with_tac_memory adapter, which
                    // injects memory on every call and respects memory.mode
                    // (once = cached memory, always = freshly retrieved memory).
                    List<ChatMessage> messages = new ArrayList<>();
                    messages.add(new ChatMessage(
                        ChatMessageRole.SYSTEM.value(), buildSystemMessage(memory, context)));
                    messages.addAll(history);

                    // Call OpenAI Chat Completions API
                    ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                        .model("gpt-4o-mini")
                        .messages(messages)
                        .temperature(0.7)
                        .maxTokens(150)
                        .build();

                    String llmResponse = openAiService.createChatCompletion(completionRequest)
                        .getChoices()
                        .get(0)
                        .getMessage()
                        .getContent();

                    log.info("🤖 AI response: {}", llmResponse);

                    // Save assistant response to conversation history
                    history.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), llmResponse));

                    // Limit history size to prevent token overflow. The system
                    // message isn't stored here, so we keep the last N turns only.
                    if (history.size() > 20) {
                        conversationHistory.put(conversationId,
                            new ArrayList<>(history.subList(history.size() - 20, history.size())));
                    }

                    return OutboundMessage.builder()
                        .content(llmResponse)
                        .build();

                } catch (Exception e) {
                    log.error("❌ Error processing message for conversation {}: {}",
                        conversationId, e.getMessage(), e);

                    return OutboundMessage.builder()
                        .content("Sorry, I encountered an error processing your message. Please try again.")
                        .build();
                }
            });
        };
    }

    /**
     * Builds the system message with TAC Memory context.
     *
     * This method injects customer memory (traits, observations, summaries)
     * into the system prompt, allowing the AI to personalize responses.
     */
    private String buildSystemMessage(MemoryResponse memory, MessageContext context) {
        // Use MemoryPromptBuilder to compose system message with memory context
        String systemMessage = MemoryPromptBuilder.compose(SYSTEM_INSTRUCTIONS, memory, context);

        if (memory != null && !memory.isEmpty()) {
            log.debug("💾 Injected memory context for profile: {}", memory.getProfileId());
        }

        return systemMessage;
    }
}
