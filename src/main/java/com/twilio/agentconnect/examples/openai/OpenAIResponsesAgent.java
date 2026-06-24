package com.twilio.agentconnect.examples.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatFunctionDynamic;
import com.theokanning.openai.completion.chat.ChatFunctionProperty;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import com.twilio.agentconnect.callback.MessageReadyCallback;
import com.twilio.agentconnect.callback.MessageStreamCallback;
import com.twilio.agentconnect.channels.voice.ConferenceCoordinator;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example agent: customer service AI on top of OpenAI Chat Completions, with
 * conference-based handoff to a human agent.
 *
 * <p>
 * Two roles run on the same WebSocket endpoint:
 * <ul>
 * <li><b>AI #1 (caller-facing)</b> — handles inbound calls. When escalation
 * is needed it generates a one-paragraph summary and calls
 * {@code transfer_to_human(reason, summary)}; the handoff parks the
 * caller in a conference and triggers an outbound briefing call.</li>
 * <li><b>AI #2 (agent-facing briefing)</b> — runs on the human agent's
 * outbound leg. Its system prompt includes the stashed summary; when the
 * human says they're ready it calls {@code bridge_to_caller}, which ends
 * the relay so the human is dropped into the same conference.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = "com.twilio.agentconnect")
public class OpenAIResponsesAgent {

    private static final Logger log = LoggerFactory.getLogger(OpenAIResponsesAgent.class);

    private static final String SYSTEM_INSTRUCTIONS = "You are Aria, a friendly and professional virtual front desk assistant for Sunnybrook Medical Clinic. "
            +
            "You are speaking with a patient or visitor over voice or SMS. " +
            "Keep responses short and conversational — a sentence or two. " +
            "Do not use markdown, asterisks, bullets, or emojis; your words will be " +
            "spoken aloud or sent as plain text. " +

            "You can answer basic questions about the clinic, including: " +
            "Business hours: Monday to Friday, 8am to 6pm, and Saturday 9am to 1pm. " +
            "Address: 123 Sunnybrook Drive, Suite 100, Austin, TX 78701. " +
            "Parking: Free parking is available in the lot directly in front of the building. " +

            "For anything related to booking, rescheduling, or canceling an appointment, " +
            "or any question you cannot confidently answer, trigger a human handoff immediately. " +
            "Call the transfer_to_human function and populate the escalation reason with a clear, " +
            "one-paragraph plain-text summary that includes: the patient's name (if provided), " +
            "the nature of their request (e.g. booking, cancellation, rescheduling), " +
            "any relevant details they shared, and the reason for escalation — " +
            "so a human agent can pick up where you left off without asking the patient to repeat themselves. " +

            "Also call transfer_to_human if the caller asks to speak to a person, " +
            "expresses frustration, or has an issue you cannot resolve.";

    private static final String BRIEFING_SYSTEM_INSTRUCTIONS = "You are briefing a human support agent who has just been connected. "
            +
            "First, in one or two short sentences, summarize who the caller is and " +
            "what they need. Then ask the human if they're ready to speak with the " +
            "caller. When the human says yes (or anything affirmative), call the " +
            "bridge_to_caller function. Do not use markdown or emojis; your words " +
            "are spoken aloud.";

    private static final String HANDOFF_FUNCTION = "transfer_to_human";
    private static final String BRIDGE_FUNCTION = "bridge_to_caller";

    private static final String HANDOFF_MESSAGE = "Sure, let me connect you with someone who can help. One moment please.";

    private static final String BRIDGE_MESSAGE = "Connecting you now.";

    private final Map<String, List<ChatMessage>> conversationHistory = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Autowired(required = false)
    private VoiceChannel voiceChannel;

    public static void main(String[] args) {
        SpringApplication.run(OpenAIResponsesAgent.class, args);
    }

    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            if (openAiApiKey == null || openAiApiKey.isEmpty()) {
                log.error("❌ OPENAI_API_KEY is not set!");
                log.error("Set it in .env file: OPENAI_API_KEY=sk-...");
                System.exit(1);
            }

            OpenAiService openAiService = new OpenAiService(openAiApiKey, Duration.ofSeconds(30));
            tac.onMessageReady(createMessageHandler(openAiService));
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
     * Streaming handler. Branches on whether this conversation is a briefing
     * (AI #2) or a normal caller-facing turn (AI #1).
     */
    private MessageStreamCallback createStreamHandler(OpenAiService openAiService) {
        return (MessageContext context) -> {
            String userMessage = context.getMessage().getContent();
            String conversationId = context.getConversationId();

            ConferenceCoordinator.BriefingContext briefing = voiceChannel.getBriefingContext(conversationId);
            boolean isBriefing = briefing != null;

            log.info("📨 Prompt (stream, briefing={}): {} (conv: {})",
                    isBriefing, userMessage, conversationId);

            List<ChatMessage> history = conversationHistory.computeIfAbsent(conversationId, k -> new ArrayList<>());
            history.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));

            String systemPrompt = isBriefing
                    ? buildBriefingSystemMessage(briefing)
                    : buildSystemMessage(context.getMemory(), context);

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
            messages.addAll(history);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model("gpt-4o-mini")
                    .messages(messages)
                    .functions(List.of(isBriefing ? bridgeFunction() : handoffFunction()))
                    .temperature(0.7)
                    .maxTokens(200)
                    .build();

            StringBuilder assistant = new StringBuilder();
            StringBuilder fnArgs = new StringBuilder();
            AtomicInteger chunkSeq = new AtomicInteger();
            AtomicBoolean handoffRequested = new AtomicBoolean();
            AtomicBoolean bridgeRequested = new AtomicBoolean();

            return Flux.from(openAiService.streamChatCompletion(request))
                    .map(chunk -> {
                        var choices = chunk.getChoices();
                        if (choices.isEmpty() || choices.get(0).getMessage() == null) {
                            return "";
                        }
                        ChatMessage delta = choices.get(0).getMessage();
                        if (delta.getFunctionCall() != null) {
                            String name = delta.getFunctionCall().getName();
                            if (HANDOFF_FUNCTION.equals(name))
                                handoffRequested.set(true);
                            if (BRIDGE_FUNCTION.equals(name))
                                bridgeRequested.set(true);
                            // Function-call argument fragments arrive incrementally.
                            // theokanning models the field as JsonNode; in practice each
                            // delta carries a TextNode wrapping a fragment string. Concat
                            // every fragment (textual via asText(), structural via
                            // toString() as a fallback) so we end up with the full args
                            // JSON when the stream closes.
                            JsonNode argsNode = delta.getFunctionCall().getArguments();
                            if (argsNode != null && !argsNode.isNull()) {
                                String argFragment = argsNode.isTextual() ? argsNode.asText() : argsNode.toString();
                                fnArgs.append(argFragment);
                                log.info("🔧 fn-call delta (conv {}): name={} fragment=[{}]",
                                         conversationId, name, argFragment);
                            } else if (name != null) {
                                log.debug("🔧 fn-call start (conv {}): name={}", conversationId, name);
                            }
                        }
                        String content = delta.getContent();
                        return content == null ? "" : content;
                    })
                    .filter(token -> !token.isEmpty())
                    .doOnNext(token -> {
                        assistant.append(token);
                        log.debug("🔹 LLM chunk #{} (conv {}): {}",
                                chunkSeq.incrementAndGet(), conversationId, token);
                        voiceChannel.sendResponse(conversationId, token, false);
                    })
                    .doOnError(e -> {
                        log.error("Error streaming from OpenAI for conversation {}: {}",
                                conversationId, e.getMessage(), e);
                        voiceChannel.sendResponse(conversationId,
                                "Sorry, I encountered an error processing your message.", false);
                    })
                    .onErrorComplete()
                    .doOnComplete(() -> {
                        if (assistant.length() > 0) {
                            history.add(new ChatMessage(
                                    ChatMessageRole.ASSISTANT.value(), assistant.toString()));
                        }
                        log.info("✅ Stream complete (conv {}, briefing={}): {} chunks, {} chars, "
                                + "handoff={}, bridge={}",
                                conversationId, isBriefing, chunkSeq.get(), assistant.length(),
                                handoffRequested.get(), bridgeRequested.get());
                    })
                    .doFinally(signal -> {
                        if (handoffRequested.get() && !isBriefing) {
                            String rawArgs = fnArgs.toString();
                            log.info("🔧 transfer_to_human streamed args (conv {}): [{}]",
                                     conversationId, rawArgs);
                            String summary = extractStringArg(rawArgs, "summary");
                            String reason = extractStringArg(rawArgs, "reason");
                            // theokanning's streaming deserializer often drops
                            // function-call argument fragments (the field is a JsonNode
                            // and partial JSON parses to null). When that happens, fall
                            // back to a single non-streaming call that asks the model to
                            // call the function — this returns args as a complete JSON
                            // string we can parse reliably. We pay one extra small request
                            // only on the handoff path and only when streaming failed.
                            if (summary == null || summary.isBlank()) {
                                String fallbackArgs = fetchHandoffArgs(openAiService, messages);
                                log.info("🔧 transfer_to_human fallback args (conv {}): [{}]",
                                         conversationId, fallbackArgs);
                                if (fallbackArgs != null) {
                                    String fbSummary = extractStringArg(fallbackArgs, "summary");
                                    String fbReason = extractStringArg(fallbackArgs, "reason");
                                    if (fbSummary != null && !fbSummary.isBlank()) summary = fbSummary;
                                    if (fbReason != null && !fbReason.isBlank()) reason = fbReason;
                                }
                            }
                            voiceChannel.sendResponse(conversationId, HANDOFF_MESSAGE, true);
                            voiceChannel.endSession(conversationId,
                                    buildCallerHandoffData(conversationId, reason, summary));
                        } else if (bridgeRequested.get() && isBriefing) {
                            voiceChannel.sendResponse(conversationId, BRIDGE_MESSAGE, true);
                            voiceChannel.endSession(conversationId,
                                    buildBridgeHandoffData(briefing.conferenceName()));
                        } else {
                            voiceChannel.sendResponse(conversationId, "", true);
                        }
                    })
                    .then();
        };
    }

    /**
     * Non-streaming fallback: ask the model to produce the {@code transfer_to_human}
     * call again, this time forcing the function and reading the args as a
     * complete JSON string. Used when streaming-mode arg fragments came back
     * malformed (theokanning's streaming deserializer drops {@code ":"}
     * separators between key fragments and value fragments, which corrupts
     * the partial JSON).
     *
     * @return the full args JSON, or {@code null} on failure
     */
    private String fetchHandoffArgs(OpenAiService openAiService, List<ChatMessage> messages) {
        try {
            ChatCompletionRequest req = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .messages(messages)
                .functions(List.of(handoffFunction()))
                .functionCall(ChatCompletionRequest.ChatCompletionRequestFunctionCall.of(HANDOFF_FUNCTION))
                .temperature(0.3)
                .maxTokens(400)
                .build();
            ChatMessage msg = openAiService.createChatCompletion(req).getChoices().get(0).getMessage();
            if (msg.getFunctionCall() == null) return null;
            JsonNode argsNode = msg.getFunctionCall().getArguments();
            if (argsNode == null || argsNode.isNull()) return null;
            // In non-streaming mode the arguments JsonNode is the parsed object,
            // so toString() gives well-formed JSON.
            return argsNode.isTextual() ? argsNode.asText() : argsNode.toString();
        } catch (Exception e) {
            log.warn("Non-streaming fallback for handoff args failed: {}", e.getMessage());
            return null;
        }
    }

    /** Tool the caller-facing AI calls to escalate. */
    private static ChatFunctionDynamic handoffFunction() {
        return ChatFunctionDynamic.builder()
                .name(HANDOFF_FUNCTION)
                .description("Transfer the caller to a human agent")
                .addProperty(ChatFunctionProperty.builder()
                        .name("reason")
                        .type("string")
                        .description("Brief reason the caller needs a human")
                        .build())
                .addProperty(ChatFunctionProperty.builder()
                        .name("summary")
                        .type("string")
                        .description("One-paragraph plain-text summary of the conversation so far")
                        .build())
                .build();
    }

    /**
     * Tool the briefing AI calls to bridge the human into the caller's conference.
     */
    private static ChatFunctionDynamic bridgeFunction() {
        return ChatFunctionDynamic.builder()
                .name(BRIDGE_FUNCTION)
                .description("Bridge the human agent to the waiting caller now")
                .build();
    }

    /**
     * Build the handoffData JSON sent on the caller's leg. The TwiML controller
     * uses {@code summary} (and other fields) to seed the briefing context.
     */
    private String buildCallerHandoffData(String conversationId, String reason, String summary) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "reasonCode", "live-agent-handoff",
                    "conversationId", conversationId,
                    "reason", reason == null ? "Caller requested a human agent" : reason,
                    "summary", summary == null ? "" : summary));
        } catch (Exception e) {
            return "{\"reasonCode\":\"live-agent-handoff\"}";
        }
    }

    /** Build the handoffData JSON sent on the agent's briefing leg. */
    private String buildBridgeHandoffData(String conferenceName) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "reasonCode", "bridge-to-conference",
                    "conference", conferenceName));
        } catch (Exception e) {
            return "{\"reasonCode\":\"bridge-to-conference\"}";
        }
    }

    /**
     * Extract a top-level string argument from accumulated streamed function-call
     * args. Falls back to null if parsing fails or the field is missing.
     */
    private String extractStringArg(String json, String field) {
        if (json == null || json.isBlank())
            return null;
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode v = node.path(field);
            return v.isTextual() ? v.asText() : null;
        } catch (Exception e) {
            log.debug("Could not parse function-call args: {}", e.getMessage());
            return null;
        }
    }

    /** System prompt for the briefing AI: includes the stored caller summary. */
    private String buildBriefingSystemMessage(ConferenceCoordinator.BriefingContext briefing) {
        StringBuilder sb = new StringBuilder(BRIEFING_SYSTEM_INSTRUCTIONS);
        sb.append("\n\nCaller context:");
        if (briefing.callerNumber() != null) {
            sb.append("\n- Caller number: ").append(briefing.callerNumber());
        }
        if (briefing.reason() != null && !briefing.reason().isBlank()) {
            sb.append("\n- Reason for escalation: ").append(briefing.reason());
        }
        if (briefing.summary() != null && !briefing.summary().isBlank()) {
            sb.append("\n- Conversation summary: ").append(briefing.summary());
        } else {
            sb.append("\n- (No summary available — keep your briefing brief.)");
        }
        return sb.toString();
    }

    /**
     * Single-response handler for messaging channels. Briefing flow is
     * voice-only, so this stays unchanged from the simple inbound case.
     */
    private MessageReadyCallback createMessageHandler(OpenAiService openAiService) {
        return (MessageContext context) -> {
            String userMessage = context.getMessage().getContent();
            String conversationId = context.getConversationId();
            MemoryResponse memory = context.getMemory();

            log.info("📨 Received message: {} (conversation: {})", userMessage, conversationId);

            return Mono.fromCallable(() -> {
                try {
                    List<ChatMessage> history = conversationHistory.computeIfAbsent(conversationId,
                            k -> new ArrayList<>());
                    history.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));

                    List<ChatMessage> messages = new ArrayList<>();
                    messages.add(new ChatMessage(
                            ChatMessageRole.SYSTEM.value(), buildSystemMessage(memory, context)));
                    messages.addAll(history);

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

                    history.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), llmResponse));

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

    private String buildSystemMessage(MemoryResponse memory, MessageContext context) {
        String systemMessage = MemoryPromptBuilder.compose(SYSTEM_INSTRUCTIONS, memory, context);
        if (memory != null && !memory.isEmpty()) {
            log.debug("💾 Injected memory context for profile: {}", memory.getProfileId());
        }
        return systemMessage;
    }
}
