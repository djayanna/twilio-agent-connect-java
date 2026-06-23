# OpenAI Chat Completions Agent Example

This example demonstrates how to build an AI agent using OpenAI's Chat Completions API integrated with Twilio Agent Connect.

## Features

- ✅ **OpenAI Chat Completions API** - Uses GPT-4o-mini for generating responses
- ✅ **TAC Memory Integration** - Automatically injects customer context into system prompts
- ✅ **Conversation History** - Maintains context across multiple turns
- ✅ **Multi-Channel Support** - Works with Voice, SMS, WhatsApp, and Chat
- ✅ **Error Handling** - Graceful degradation on API failures

## What This Agent Does

The agent:
1. Receives customer messages via Twilio channels (Voice/SMS/WhatsApp)
2. Retrieves customer memory (if configured) from Twilio Conversation Memory
3. Injects memory context into the OpenAI system prompt
4. Maintains conversation history for contextual responses
5. Returns short, conversational responses suitable for voice or text

## Prerequisites

1. **Twilio Account** with credentials configured (see main [SETUP_CREDENTIALS.md](../../../../../../../SETUP_CREDENTIALS.md))
2. **OpenAI API Key** - Get one at [platform.openai.com/api-keys](https://platform.openai.com/api-keys)

## Quick Start

### 1. Add OpenAI API Key to `.env`

```bash
# Open your .env file
nano .env

# Add this line:
OPENAI_API_KEY=sk-proj-xxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### 2. Build the Project

```bash
./gradlew clean build -x test
```

### 3. Run the Agent

```bash
# Load environment variables
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)

# Run the OpenAI example
./gradlew bootRun -PmainClass=com.twilio.agentconnect.examples.openai.OpenAIResponsesAgent
```

You should see:
```
🤖 OpenAI Agent is ready!
📞 Voice endpoint: http://localhost:8080/twiml
💬 Messaging webhook: http://localhost:8080/webhook
🔌 WebSocket endpoint: ws://localhost:8080/ws/voice
```

### 4. Test with SMS (Quickest)

**Option A: Using Twilio CLI**

```bash
# Send a test SMS to your Twilio number
twilio api:core:messages:create \
  --from "+15551234567" \
  --to "+15559876543" \
  --body "Hello! Can you help me?"
```

**Option B: Send SMS from your phone**

Text your Twilio phone number from a verified number (if using trial account).

### 5. Test with Voice (Requires ngrok)

**Terminal 1: Start ngrok**
```bash
ngrok http 8080
```

**Terminal 2: Update Twilio number webhook**
```bash
# Copy the ngrok HTTPS URL (e.g., https://abc123.ngrok.io)
export TWILIO_VOICE_PUBLIC_DOMAIN=https://abc123.ngrok.io
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)

# Configure your Twilio number
twilio phone-numbers:update "+15551234567" \
  --voice-url="https://abc123.ngrok.io/twiml"

# Restart the agent
./gradlew bootRun -PmainClass=com.twilio.agentconnect.examples.openai.OpenAIResponsesAgent
```

**Call your Twilio number** - you'll speak with the AI agent!

## How It Works

### 1. Message Flow

```
Customer Message → Twilio → TAC SDK → Memory Retrieval → OpenAI API → Response
```

### 2. Memory Injection

When a message arrives, the agent:

```java
// 1. Retrieves customer memory from TAC
MemoryResponse memory = context.getMemory();

// 2. Builds system message with memory context
String systemMessage = buildSystemMessage(memory, context);
// Result: "You are a customer service agent...
//          Customer Context:
//          - Customer name: John Doe
//          - Previous observations: Called about billing last week
//          - Communication history: 3 prior conversations"

// 3. Sends to OpenAI with conversation history
ChatCompletionRequest request = ChatCompletionRequest.builder()
    .model("gpt-4o-mini")
    .messages(history) // Includes system message + conversation
    .build();
```

### 3. Conversation History Management

The agent maintains a rolling history of the last 20 messages per conversation:

```java
// Add user message
history.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));

// Get AI response
String response = openAiService.createChatCompletion(request)...

// Add assistant response
history.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), response));

// Trim if needed (keep system message + last 18 messages)
if (history.size() > 20) {
    // Trim logic...
}
```

## Configuration

### OpenAI Model

Change the model in `OpenAIResponsesAgent.java`:

```java
ChatCompletionRequest.builder()
    .model("gpt-4o")  // or "gpt-4", "gpt-3.5-turbo"
    .temperature(0.7)
    .maxTokens(150)
    .build();
```

### System Instructions

Customize the agent's behavior in `OpenAIResponsesAgent.java`:

```java
private static final String SYSTEM_INSTRUCTIONS =
    "You are a helpful customer service agent for Acme Corp. " +
    "You can help with orders, returns, and account questions. " +
    "Always be polite and professional.";
```

### Memory Mode

Control when memory is retrieved in `application.yml`:

```yaml
twilio:
  agent-connect:
    memory:
      store-id: ${TWILIO_MEMORY_STORE_ID:}   # mem_store_... (TTID format)
      identifier-type: phone                  # how the caller's address is looked up
      trait-groups: ${TWILIO_TRAIT_GROUPS:}   # optional: restrict trait groups
      observations-limit: 20
      summaries-limit: 5
      mode: once  # Options: always, once, never
```

- **`once`**: Retrieve memory once per conversation and cache it (default)
- **`always`**: Retrieve memory on every message / voice turn (fresh context)
- **`never`**: Disable memory retrieval

### What Memory Is Retrieved

When a message arrives, the SDK assembles a `MemoryResponse` from the Twilio
Conversation Memory API (three calls behind the scenes):

1. **Profile lookup** — resolves the caller's address (e.g. phone) to a profile
   via `POST /Stores/{id}/Profiles/Lookup`.
2. **Traits** — structured facts (name, tier, preferences) from
   `GET /Stores/{id}/Profiles/{id}`.
3. **Observations + summaries** — conversational memory from
   `POST /Stores/{id}/Profiles/{id}/Recall`.

The assembled `MemoryResponse` is handed to your message handler on **every**
message/voice turn (subject to `mode` — `once` returns the cached copy,
`always` re-retrieves). It is **available whether or not you use it** — the
handler decides:

```java
MemoryResponse memory = context.getMemory();

memory.getProfileId();          // resolved mem_profile_... ID (or null)
memory.getTraits();             // Map<String,Object> grouped traits
memory.getObservations();       // List<Observation>  -> getContent(), getScore()
memory.getSummaries();          // List<ConversationSummary> -> getSummary()

if (!memory.isEmpty()) {
    // e.g. inject into the system prompt (this example does this via
    // MemoryPromptBuilder.compose), or ignore it entirely.
}
```

This mirrors the Python/TypeScript SDK, where memory is passed to the handler
regardless of use:

```typescript
const voiceChannel = new VoiceChannel(tac, { memoryMode: 'always' });
```

> If `store-id` is unset or invalid, retrieval degrades gracefully to an empty
> `MemoryResponse` (logged as a warning) — the conversation still proceeds.

### Conversation Orchestrator & Memory Retrieval

There are **two independent ways** memory gets retrieved for a voice call:

1. **App-side (this SDK)** — `MemoryClient` calls the Conversation Memory API and
   `MemoryPromptBuilder` injects the result into the OpenAI system prompt. Controlled
   by the `memory.mode` setting above.

2. **Twilio-side (Conversation Orchestrator)** — for voice, the generated TwiML
   includes the `conversationConfiguration` attribute on `<ConversationRelay>`:

   ```xml
   <Connect>
     <ConversationRelay
       url="wss://your-domain/ws/voice"
       conversationConfiguration="conv_configuration_xxxxxxxx" />
   </Connect>
   ```

   This value comes from `TWILIO_CONVERSATION_CONFIGURATION_ID`. When present,
   Conversation Orchestrator captures the call into a conversation and activates the
   linked **Memory Store** for identity resolution and context — without any extra
   app code.

   > ⚠️ **Attribute name matters:** it is `conversationConfiguration`, **not**
   > `conversationConfigurationId`. Twilio silently ignores unrecognized attribute
   > names, so a typo means no conversation is created (and no error is raised).

   > ⚠️ **Avoid double STT billing:** passing `conversationConfiguration` in TwiML is
   > *active* ingestion. Do **not** also add passive `VOICE` `captureRules` to the same
   > Orchestrator Configuration — both speech-to-text engines would run and you would be
   > billed twice. Define `VOICE` channel settings (timeouts) but omit `captureRules`.

### Conversation History Limit

Adjust the history limit in `OpenAIResponsesAgent.java`:

```java
if (history.size() > 20) {  // Change 20 to your preferred limit
    // Trim logic...
}
```

## Code Structure

```
OpenAIResponsesAgent.java
├── main()                      - Spring Boot entry point
├── setupAgent()                - Initialize agent and callbacks
├── createMessageHandler()      - Process incoming messages
└── buildSystemMessage()        - Inject memory context

Flow:
1. Customer sends message
2. TAC invokes createMessageHandler()
3. buildSystemMessage() adds memory context
4. OpenAI API generates response
5. Response sent back to customer
```

## Comparison with Python Example

This Java example is functionally equivalent to the Python version:

| Feature | Python | Java |
|---------|--------|------|
| OpenAI Integration | `AsyncOpenAI` | `OpenAiService` |
| Memory Injection | `with_tac_memory()` adapter | `buildSystemMessage()` |
| Conversation History | `dict[str, list[dict]]` | `Map<String, List<ChatMessage>>` |
| Error Handling | `try/except` | `try/catch` |
| Server | FastAPI | Spring Boot (Netty) |

## Troubleshooting

### "OPENAI_API_KEY is not set"

Make sure you added it to `.env` and loaded it:
```bash
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
```

### "Unauthorized" or "Invalid API Key"

- Check your OpenAI API key at [platform.openai.com/api-keys](https://platform.openai.com/api-keys)
- Ensure it starts with `sk-proj-` or `sk-`
- Verify you have credits in your OpenAI account

### "Rate limit exceeded"

OpenAI has rate limits based on your plan:
- Free tier: ~3 requests/minute
- Paid tier: Much higher limits

Wait a moment and try again, or upgrade your OpenAI plan.

### Voice calls not connecting

1. Verify ngrok is running: `curl https://your-ngrok-url.ngrok.io/twiml`
2. Check Twilio number webhook is set correctly
3. Check logs for errors: `./gradlew bootRun` output

### No response from agent

1. Check logs for errors in the terminal
2. Verify OpenAI API key is valid
3. Check Twilio webhook logs at [console.twilio.com/monitor/logs/debugger](https://console.twilio.com/monitor/logs/debugger)

## Customization Ideas

### 1. Add Custom Tools

```java
@TacTool(name = "lookupOrder", description = "Look up order status by order number")
public String lookupOrder(@TacToolParam(name = "orderNumber") String orderNumber) {
    // Call your order system API
    return "Order " + orderNumber + " was shipped on 2024-01-15";
}
```

### 2. Add Function Calling

Use OpenAI's function calling feature:

```java
ChatCompletionRequest.builder()
    .model("gpt-4o")
    .functions(List.of(
        ChatFunction.builder()
            .name("lookupOrder")
            .description("Look up order status")
            .parameters(...)
            .build()
    ))
    .build();
```

### 3. Sentiment Analysis

Track customer sentiment:

```java
String sentiment = analyzesentiment(userMessage);
if (sentiment.equals("negative")) {
    // Escalate to human agent
    tac.handoffToHuman(conversationId);
}
```

### 4. Language Detection

Handle multiple languages:

```java
String language = detectLanguage(userMessage);
if (!language.equals("en")) {
    systemMessage += "\nRespond in " + language + " language.";
}
```

## Environment Variables

```bash
# Required - Twilio Credentials
TWILIO_ACCOUNT_SID=ACxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxx
TWILIO_API_KEY=SKxxxxxxxx
TWILIO_API_SECRET=xxxxxxxx
TWILIO_CONVERSATION_CONFIGURATION_ID=IGxxxxxxxx
TWILIO_PHONE_NUMBER=+15551234567

# Required - OpenAI
OPENAI_API_KEY=sk-proj-xxxxxxxx

# Optional - Memory
TWILIO_MEMORY_STORE_ID=MEMxxxxxxxx

# Optional - Voice
TWILIO_VOICE_PUBLIC_DOMAIN=https://abc123.ngrok.io
```

## Next Steps

1. **Deploy to production** - See deployment guides
2. **Add custom tools** - Extend agent capabilities
3. **Implement handoff** - Transfer to human agents when needed
4. **Add analytics** - Track conversation metrics
5. **Multi-language support** - Handle international customers

## Resources

- [OpenAI API Documentation](https://platform.openai.com/docs)
- [Twilio Agent Connect Docs](https://www.twilio.com/docs)
- [Main TAC SDK README](../../../../../../../README.md)
- [Python Example](https://github.com/twilio/twilio-agent-connect-python/blob/main/getting_started/examples/partners/openai_responses_api.py)

## License

MIT License - see [LICENSE](../../../../../../../LICENSE)
