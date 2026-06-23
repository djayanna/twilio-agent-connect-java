package com.twilio.agentconnect.examples.openai;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import com.twilio.agentconnect.callback.MessageReadyCallback;
import com.twilio.agentconnect.callback.MessageStreamCallback;
import com.twilio.agentconnect.channels.voice.VoiceChannel;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.MemoryResponse;
import com.twilio.agentconnect.context.model.OutboundMessage;
import com.twilio.agentconnect.core.TwilioAgentConnect;
import com.twilio.agentconnect.util.MemoryPromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
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

    // The Voice channel is only present when voice is enabled; the streaming
    // (push) handler uses it to forward tokens to the caller as they arrive.
    @Autowired(required = false)
    private VoiceChannel voiceChannel;

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

            OpenAiService openAiService = new OpenAiService(openAiApiKey, Duration.ofSeconds(30));

            // Single-response callback (used by messaging channels: SMS/WhatsApp/Chat).
            tac.onMessageReady(createMessageHandler(openAiService));

            // Streaming push callback (used by the Voice channel): the handler streams
            // OpenAI tokens and pushes them to the caller via voiceChannel.sendResponse,
            // so Twilio starts speaking before the full reply is generated.
            if (voiceChannel != null) {
                tac.onMessageStream(createStreamHandler(openAiService));
            }

            log.info("🤖 OpenAI Agent is ready!");
            log.info("📞 Voice endpoint: http://localhost:8080/twiml");
            log.info("💬 Messaging webhook: http://localhost:8080/webhook");
            log.info("🔌 WebSocket endpoint: ws://localhost:8080/ws/voice");
        };
    }

    /**
     * Streaming (push) handler — mirrors the Python pattern: build the input,
     * stream tokens from OpenAI, and push them to the caller via the Voice
     * channel. Returns a Mono that completes when the push finishes.
     */
    private MessageStreamCallback createStreamHandler(OpenAiService openAiService) {
        return (MessageContext context) -> {
            String userMessage = context.getMessage().getContent();
            String conversationId = context.getConversationId();
            MemoryResponse memory = context.getMemory();

            log.info("📨 Received prompt (stream): {} (conversation: {})",
                     userMessage, conversationId);

            List<ChatMessage> history =
                conversationHistory.computeIfAbsent(conversationId, k -> new ArrayList<>());
            history.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));

            // Fresh system message (with current memory) + conversation so far.
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(
                ChatMessageRole.SYSTEM.value(), buildSystemMessage(memory, context)));
            messages.addAll(history);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .messages(messages)
                .temperature(0.7)
                .maxTokens(150)
                .build();

            // Accumulate the assistant reply as it streams so we can persist it
            // to history once the stream completes.
            StringBuilder assistant = new StringBuilder();
            java.util.concurrent.atomic.AtomicInteger chunkSeq =
                new java.util.concurrent.atomic.AtomicInteger();

            // theokanning's streamChatCompletion returns an RxJava2 Flowable, which
            // is itself a Reactive-Streams Publisher — wrap it directly with Flux.from.
            // We push each token to the voice channel as it arrives, then send the
            // terminal (last=true) frame when the stream completes.
            return Flux.from(openAiService.streamChatCompletion(request))
                .map(chunk -> {
                    var choices = chunk.getChoices();
                    if (choices.isEmpty() || choices.get(0).getMessage() == null) {
                        return "";
                    }
                    String delta = choices.get(0).getMessage().getContent();
                    return delta == null ? "" : delta;
                })
                .filter(token -> !token.isEmpty())
                .doOnNext(token -> {
                    // Log each LLM chunk and forward it to the caller immediately.
                    assistant.append(token);
                    log.debug("🔹 LLM chunk #{} (conv {}): {}",
                              chunkSeq.incrementAndGet(), conversationId, token);
                    voiceChannel.sendResponse(conversationId, token, false);
                })
                .doOnError(e -> {
                    log.error("Error streaming from OpenAI for conversation {}: {}",
                              conversationId, e.getMessage(), e);
                    // Speak a fallback message, then close the turn below.
                    voiceChannel.sendResponse(conversationId,
                        "Sorry, I encountered an error processing your message.", false);
                })
                .onErrorComplete()
                .doOnComplete(() -> {
                    history.add(new ChatMessage(
                        ChatMessageRole.ASSISTANT.value(), assistant.toString()));
                    log.info("✅ Stream complete (conv {}): {} chunks, {} chars",
                             conversationId, chunkSeq.get(), assistant.length());
                })
                // Terminal frame signals the end of this response to ConversationRelay.
                .doFinally(signal -> voiceChannel.sendResponse(conversationId, "", true))
                .then();
        };
    }

    /**
     * Creates the single-response handler used by messaging channels (SMS,
     * WhatsApp, Chat), which expect one complete reply rather than a stream.
     */
    private MessageReadyCallback createMessageHandler(OpenAiService openAiService) {
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
