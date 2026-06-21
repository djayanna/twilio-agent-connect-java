# Getting Started with Twilio Agent Connect Java

Complete guide to building AI agents with Twilio Agent Connect (TAC) in Java.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start (5 minutes)](#quick-start-5-minutes)
3. [Understanding the Basics](#understanding-the-basics)
4. [Complete Examples](#complete-examples)
5. [Next Steps](#next-steps)

## Prerequisites

### Required

- **Java 17+** (tested with Java 17)
- **Gradle 8.5+** (wrapper included)
- **Twilio Account** ([sign up free](https://www.twilio.com/try-twilio))
- **OpenAI or Anthropic API Key** (for LLM)

### Twilio Setup

1. **Get Twilio Credentials**
   - Account SID: [Console Dashboard](https://console.twilio.com/)
   - Auth Token: Same page
   - API Key/Secret: [API Keys](https://console.twilio.com/project/api-keys)

2. **Buy a Phone Number**
   - [Buy a Number](https://console.twilio.com/develop/phone-numbers/manage/search)
   - Select one with SMS and Voice capabilities
   - Note the number (e.g., +1234567890)

3. **Create Conversation Configuration**
   - Go to [Conversations](https://console.twilio.com/us1/develop/conversations/configuration)
   - Create new configuration
   - Note the ID (starts with `IG`)

4. **Optional: Create Memory Store**
   - Go to [Memory](https://console.twilio.com/us1/develop/conversations/memory)
   - Create new Memory Store
   - Note the ID (starts with `MEM`)

## Quick Start (5 minutes)

### 1. Clone or Create Project

```bash
# Clone this repo
git clone https://github.com/twilio/twilio-agent-connect-java
cd twilio-agent-connect-java

# Or use the examples
cd docs/examples/basic
```

### 2. Set Environment Variables

```bash
# Twilio (required)
export TWILIO_ACCOUNT_SID="ACxxx"
export TWILIO_AUTH_TOKEN="your-auth-token"
export TWILIO_API_KEY="SKxxx"
export TWILIO_API_SECRET="your-api-secret"
export TWILIO_CONVERSATION_CONFIGURATION_ID="IGxxx"
export TWILIO_PHONE_NUMBER="+1234567890"

# LLM (pick one)
export OPENAI_API_KEY="sk-xxx"
# OR
export ANTHROPIC_API_KEY="sk-ant-xxx"

# Optional
export TWILIO_MEMORY_STORE_ID="MEMxxx"
```

### 3. Run the Example

```bash
./gradlew bootRun
```

### 4. Test It

**Option A: Test with SMS**

```bash
# Send SMS to your Twilio number from your phone
# Or use curl:
curl -X POST "http://localhost:8080/webhook" \
  -d "From=+15551234567" \
  -d "Body=Hello!"
```

**Option B: Test with Voice**

1. Start ngrok: `ngrok http 8080`
2. Configure Twilio number voice webhook: `https://abc123.ngrok.io/twiml`
3. Call your Twilio number

### 5. See It Work

```
Customer: Hello!
Agent: Hi! How can I help you today?
```

🎉 **Success!** You have a working AI agent.

## Understanding the Basics

### Architecture

```
Customer Message
      ↓
Twilio (SMS/Voice/WhatsApp)
      ↓
Your Server (/webhook or /twiml)
      ↓
TwilioAgentConnect (TAC)
      ↓
Your Handler (onMessageReady)
      ↓
Your LLM (OpenAI/Anthropic)
      ↓
Response
      ↓
TAC sends to Twilio
      ↓
Customer receives response
```

### Basic Agent Structure

```java
@SpringBootApplication
public class MyAgent {
    
    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            // Register your message handler
            tac.onMessageReady(this::handleMessage);
        };
    }
    
    private Mono<OutboundMessage> handleMessage(MessageContext context) {
        // 1. Get customer message
        String customerMessage = context.getMessage().getContent();
        
        // 2. Call your LLM
        String response = callLLM(customerMessage);
        
        // 3. Return response
        return Mono.just(OutboundMessage.builder()
            .content(response)
            .conversationId(context.getConversationId())
            .build());
    }
}
```

### Key Concepts

#### 1. MessageContext

Everything you need about the current message:

```java
MessageContext context = ...;

// Who sent it
String from = context.getMessage().getFrom();

// What they said
String content = context.getMessage().getContent();

// Which channel (SMS, Voice, WhatsApp, etc.)
ChannelType channel = context.getChannelType();

// Customer memory (if configured)
ConversationMemory memory = context.getMemory();

// Session info
String conversationId = context.getConversationId();
String profileId = context.getProfileId();
```

#### 2. OutboundMessage

Your response to the customer:

```java
OutboundMessage message = OutboundMessage.builder()
    .content("Your order ships tomorrow!")
    .conversationId(context.getConversationId())
    .mediaUrl("https://example.com/tracking.pdf")  // optional
    .build();
```

#### 3. Reactive Programming

TAC uses Project Reactor (Mono/Flux):

```java
// Simple response
return Mono.just(outboundMessage);

// Async operation
return callLLM(message)
    .map(response -> buildMessage(response))
    .onErrorReturn(fallbackMessage);
```

## Complete Examples

### Example 1: Basic Echo Agent

Simplest possible agent - echoes messages back:

```java
tac.onMessageReady(context ->
    Mono.just(OutboundMessage.builder()
        .content("Echo: " + context.getMessage().getContent())
        .conversationId(context.getConversationId())
        .build())
);
```

**See:** [`docs/examples/basic/`](examples/basic/)

### Example 2: OpenAI Integration

Real AI agent with GPT-4:

```java
private Mono<String> callOpenAI(String message) {
    return webClient.post()
        .uri("https://api.openai.com/v1/chat/completions")
        .header("Authorization", "Bearer " + apiKey)
        .bodyValue(Map.of(
            "model", "gpt-4",
            "messages", List.of(
                Map.of("role", "user", "content", message)
            )
        ))
        .retrieve()
        .bodyToMono(Map.class)
        .map(this::extractResponse);
}
```

**See:** [`docs/examples/openai/`](examples/openai/)

### Example 3: Anthropic Claude

Using Claude instead of GPT:

```java
private Mono<String> callClaude(String message) {
    return webClient.post()
        .uri("https://api.anthropic.com/v1/messages")
        .header("x-api-key", apiKey)
        .header("anthropic-version", "2023-06-01")
        .bodyValue(Map.of(
            "model", "claude-3-5-sonnet-20241022",
            "max_tokens", 1024,
            "messages", List.of(
                Map.of("role", "user", "content", message)
            )
        ))
        .retrieve()
        .bodyToMono(Map.class)
        .map(this::extractResponse);
}
```

**See:** [`docs/examples/anthropic/`](examples/anthropic/)

### Example 4: Memory-Enhanced Agent

Uses customer memory for personalization:

```java
private Mono<OutboundMessage> handleMessage(MessageContext context) {
    // Memory is automatically fetched
    ConversationMemory memory = context.getMemory();
    
    // Build personalized prompt
    String systemPrompt = MemoryPromptBuilder.compose(
        "You are a helpful assistant.",
        memory,
        context
    );
    
    // Call LLM with memory context
    return callLLM(systemPrompt, context.getMessage().getContent())
        .map(response -> buildResponse(response, context));
}
```

**Memory includes:**
- Customer traits (name, preferences, VIP status)
- Recent observations (recent interactions)
- Summaries (conversation history)

**See:** [`docs/examples/memory/`](examples/memory/)

### Example 5: Tools & Actions

Agent that can take actions (check orders, handoff to human):

```java
@Component
class OrderTool {
    @TacTool(description = "Check order status by order number")
    public Mono<String> checkOrderStatus(
            @TacToolParam(description = "Order number") String orderNumber) {
        
        // Query your database/API
        return orderService.getStatus(orderNumber)
            .map(status -> "Order " + orderNumber + ": " + status);
    }
}
```

Tools are:
- Automatically discovered by Spring
- Exported to LLM-compatible format
- Injected with session/context when needed

**See:** [`docs/examples/tools/`](examples/tools/)

### Example 6: Voice Agent

Real-time phone conversations:

```java
private Mono<OutboundMessage> handleMessage(MessageContext context) {
    boolean isVoice = context.getChannelType() == ChannelType.VOICE;
    
    if (isVoice) {
        // Voice: SHORT and conversational (< 40 words)
        return callLLM("Be very brief and conversational", message)
            .map(response -> truncate(response, 40));
    } else {
        // SMS: Concise (160 chars)
        return callLLM("Be concise", message)
            .map(response -> truncate(response, 160));
    }
}
```

**Voice Setup:**
1. Start ngrok: `ngrok http 8080`
2. Configure voice webhook: `https://xxx.ngrok.io/twiml`
3. Call your number

**See:** [`docs/examples/voice/`](examples/voice/)

### Example 7: Multichannel Agent

Single agent for Voice, SMS, WhatsApp, RCS:

```java
private String buildChannelPrompt(ChannelType channel) {
    return switch (channel) {
        case VOICE -> "Keep under 40 words. Be conversational.";
        case SMS -> "Keep under 160 chars. Plain text only.";
        case WHATSAPP -> "You can use emojis. Up to 300 chars.";
        case RCS -> "Rich formatting. Up to 500 chars.";
        default -> "Be clear and concise.";
    };
}
```

**Benefits:**
- One codebase for all channels
- Memory persists across channels
- Channel-optimized responses

**See:** [`docs/examples/multichannel/`](examples/multichannel/)

## Configuration

### application.yml

```yaml
twilio:
  agent-connect:
    # Twilio credentials
    account-sid: ${TWILIO_ACCOUNT_SID}
    auth-token: ${TWILIO_AUTH_TOKEN}
    api-key: ${TWILIO_API_KEY}
    api-secret: ${TWILIO_API_SECRET}
    phone-number: ${TWILIO_PHONE_NUMBER}
    conversation-configuration-id: ${TWILIO_CONVERSATION_CONFIGURATION_ID}
    
    # Memory
    memory:
      store-id: ${TWILIO_MEMORY_STORE_ID:}
      retrieval-mode: ONCE  # ONCE, ALWAYS, or NEVER
    
    # Voice
    voice:
      websocket-path: /voice/ws
      twiml-path: /twiml
    
    # Resilience
    resilience:
      circuit-breaker:
        enabled: true
        failure-rate-threshold: 50
        wait-duration: 60000

server:
  port: 8080
```

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `TWILIO_ACCOUNT_SID` | Yes | Twilio Account SID |
| `TWILIO_AUTH_TOKEN` | Yes | Twilio Auth Token |
| `TWILIO_API_KEY` | Yes | Twilio API Key (for signatures) |
| `TWILIO_API_SECRET` | Yes | Twilio API Secret |
| `TWILIO_PHONE_NUMBER` | Yes | Your Twilio phone number |
| `TWILIO_CONVERSATION_CONFIGURATION_ID` | Yes | Conversation Config ID |
| `TWILIO_MEMORY_STORE_ID` | No | Memory Store ID |
| `OPENAI_API_KEY` | No* | OpenAI API key |
| `ANTHROPIC_API_KEY` | No* | Anthropic API key |

\* One LLM API key required

## Next Steps

### Learning Path

1. **Start with Basic** → [`docs/examples/basic/`](examples/basic/)
   - Understand TAC flow
   - Test with SMS

2. **Add LLM** → [`docs/examples/openai/`](examples/openai/)
   - Integrate GPT-4 or Claude
   - Handle LLM errors

3. **Enable Memory** → [`docs/examples/memory/`](examples/memory/)
   - Personalize responses
   - Use customer history

4. **Add Tools** → [`docs/examples/tools/`](examples/tools/)
   - Create custom actions
   - Connect to your systems

5. **Add Voice** → [`docs/examples/voice/`](examples/voice/)
   - Real-time conversations
   - Voice optimization

6. **Go Multichannel** → [`docs/examples/multichannel/`](examples/multichannel/)
   - Handle all channels
   - Channel-specific optimization

### Production Checklist

- [ ] Use Redis for session storage (not in-memory)
- [ ] Configure circuit breakers and timeouts
- [ ] Add proper error handling and logging
- [ ] Implement rate limiting
- [ ] Add monitoring and alerting
- [ ] Set up staging environment
- [ ] Test failover scenarios
- [ ] Document escalation procedures
- [ ] Configure Twilio signature validation
- [ ] Review security (API keys, webhooks)

### Resources

- **Main README**: [`README.md`](../README.md)
- **API Documentation**: See JavaDoc in source code
- **Twilio Docs**: [https://www.twilio.com/docs/agent-connect](https://www.twilio.com/docs/conversations)
- **Examples**: [`docs/examples/`](examples/)
- **Python Reference**: [https://github.com/twilio/twilio-agent-connect-python](https://github.com/twilio/twilio-agent-connect-python)

### Getting Help

- **Issues**: [GitHub Issues](https://github.com/twilio/twilio-agent-connect-java/issues)
- **Twilio Support**: [https://support.twilio.com](https://support.twilio.com)
- **Community**: [Twilio Community](https://community.twilio.com)

## Common Issues

### "Build fails with Lombok errors"

```bash
# Ensure Java 17 (not 21)
java -version

# Clean build
./gradlew clean build
```

### "No response from agent"

Check:
1. Environment variables set correctly
2. Server running (`./gradlew bootRun`)
3. Twilio webhook configured
4. Check logs for errors

### "Memory not working"

Check:
1. `TWILIO_MEMORY_STORE_ID` set
2. Memory exists for this Profile ID
3. Retrieval mode not `NEVER`
4. Check Twilio Console for memory data

### "Voice not connecting"

Check:
1. ngrok running
2. Voice webhook points to ngrok URL + `/twiml`
3. WebSocket endpoint `/voice/ws` accessible
4. Check ngrok dashboard for requests

## License

MIT License - see [LICENSE](../LICENSE)

## Contributing

Contributions welcome! See [CONTRIBUTING.md](../CONTRIBUTING.md)
