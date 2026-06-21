# Getting Started with Twilio Agent Connect Java SDK

This guide will help you get started with the Twilio Agent Connect Java SDK.

## Prerequisites

- Java 21 or higher
- Gradle 8.x (included via wrapper)
- A Twilio account
- Twilio Conversation Configuration
- Twilio phone number (for Voice/SMS)
- (Optional) Twilio Conversation Memory Store

## Step 1: Set Up Twilio Services

### 1.1 Create a Conversation Configuration

```bash
# Using Twilio CLI
twilio api:conversations:v1:configuration:create \
  --auto-create-conversations
```

Save the Configuration SID - you'll need it for `TWILIO_CONVERSATION_CONFIGURATION_ID`.

### 1.2 (Optional) Create a Memory Store

```bash
# Using curl or Twilio Console
curl -X POST https://memory.twilio.com/v1/MemoryStores \
  -u "$TWILIO_API_KEY:$TWILIO_API_SECRET" \
  -d "FriendlyName=MyAgentMemory"
```

Save the Memory Store SID for `TWILIO_MEMORY_STORE_ID`.

## Step 2: Clone and Build

```bash
# Clone the repository
git clone https://github.com/twilio/twilio-agent-connect-java.git
cd twilio-agent-connect-java

# Build the project
./gradlew build

# Run tests
./gradlew test
```

## Step 3: Configure Environment

Create a `.env` file or set environment variables:

```bash
# Required
export TWILIO_ACCOUNT_SID="ACxxx"
export TWILIO_AUTH_TOKEN="your-auth-token"
export TWILIO_API_KEY="SKxxx"
export TWILIO_API_SECRET="your-api-secret"
export TWILIO_CONVERSATION_CONFIGURATION_ID="IGxxx"
export TWILIO_PHONE_NUMBER="+1234567890"

# Optional - for voice webhooks
export TWILIO_VOICE_PUBLIC_DOMAIN="https://your-domain.com"

# Optional - for memory integration
export TWILIO_MEMORY_STORE_ID="MEMxxx"
```

## Step 4: Create Your First Agent

### 4.1 Create a Spring Boot Application

```java
@SpringBootApplication
public class MyAgentApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MyAgentApplication.class, args);
    }
    
    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            // Register message handler
            tac.onMessageReady(this::handleMessage);
            
            System.out.println("Agent is ready!");
        };
    }
    
    private Mono<OutboundMessage> handleMessage(MessageContext context) {
        String customerMessage = context.getMessage().getContent();
        
        // Call your LLM here
        String response = callYourLLM(customerMessage, context.getMemory());
        
        return Mono.just(OutboundMessage.builder()
            .content(response)
            .conversationId(context.getConversationId())
            .build());
    }
    
    private String callYourLLM(String message, MemoryResponse memory) {
        // TODO: Integrate with OpenAI, Anthropic, or your LLM
        return "Echo: " + message;
    }
}
```

### 4.2 Create application.yml

```yaml
server:
  port: 8080

twilio:
  agent-connect:
    account-sid: ${TWILIO_ACCOUNT_SID}
    auth-token: ${TWILIO_AUTH_TOKEN}
    api-key: ${TWILIO_API_KEY}
    api-secret: ${TWILIO_API_SECRET}
    conversation-configuration-id: ${TWILIO_CONVERSATION_CONFIGURATION_ID}
    phone-number: ${TWILIO_PHONE_NUMBER}
    
    memory:
      store-id: ${TWILIO_MEMORY_STORE_ID:}
      mode: once # always, once, never
    
    channels:
      sms: true
      whatsapp: true
      chat: true
```

## Step 5: Run Your Agent

```bash
./gradlew bootRun
```

Your agent is now running on `http://localhost:8080`!

## Step 6: Expose Your Webhook

For local development, use ngrok:

```bash
ngrok http 8080
```

Copy the HTTPS URL (e.g., `https://abc123.ngrok.io`).

## Step 7: Configure Twilio Webhooks

### For SMS/WhatsApp

Configure your Twilio phone number to send webhooks to:
```
https://your-domain.com/webhook
```

Using Twilio Console:
1. Go to Phone Numbers → Manage → Active numbers
2. Select your number
3. Under "Messaging", set:
   - Webhook: `https://your-domain.com/webhook`
   - HTTP POST

### For Voice (when implemented)

Set the Voice webhook to:
```
https://your-domain.com/twiml
```

## Step 8: Test Your Agent

### Test SMS

Send an SMS to your Twilio number. You should see:
1. Log output in your console showing the received message
2. A response sent back to your phone

### Test WhatsApp

Send a WhatsApp message to your Twilio WhatsApp number (format: `whatsapp:+1234567890`).

## Integrating with LLMs

### OpenAI Example

```java
@Service
public class OpenAiService {
    
    private final OpenAiClient openAiClient;
    
    public String chat(String systemPrompt, String userMessage) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .model("gpt-4")
            .messages(List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userMessage)
            ))
            .build();
            
        return openAiClient.createChatCompletion(request)
            .getChoices().get(0)
            .getMessage()
            .getContent();
    }
}
```

### Anthropic Claude Example

```java
@Service
public class ClaudeService {
    
    private final ClaudeClient claudeClient;
    
    public String chat(String systemPrompt, String userMessage) {
        MessageRequest request = MessageRequest.builder()
            .model("claude-3-opus-20240229")
            .maxTokens(1024)
            .system(systemPrompt)
            .messages(List.of(
                Message.builder()
                    .role("user")
                    .content(userMessage)
                    .build()
            ))
            .build();
            
        return claudeClient.createMessage(request)
            .getContent().get(0)
            .getText();
    }
}
```

## Working with Memory

The Memory API provides customer context:

```java
private Mono<OutboundMessage> handleMessage(MessageContext context) {
    MemoryResponse memory = context.getMemory();
    
    // Build enriched prompt with customer traits
    StringBuilder prompt = new StringBuilder();
    prompt.append("Customer message: ")
          .append(context.getMessage().getContent());
    
    if (!memory.isEmpty()) {
        // Add customer traits
        prompt.append("\n\nCustomer profile:");
        memory.getTraits().forEach((key, value) ->
            prompt.append("\n- ").append(key).append(": ").append(value)
        );
        
        // Add recent observations
        if (!memory.getObservations().isEmpty()) {
            prompt.append("\n\nRecent interactions:");
            memory.getObservations().stream()
                .limit(5)
                .forEach(obs ->
                    prompt.append("\n- ").append(obs.getContent())
                );
        }
    }
    
    String response = callLLM(prompt.toString());
    
    return Mono.just(OutboundMessage.builder()
        .content(response)
        .conversationId(context.getConversationId())
        .build());
}
```

## Handling Conversation End

```java
@Bean
public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
    return args -> {
        tac.onMessageReady(this::handleMessage);
        
        tac.onConversationEnded(session -> {
            log.info("Conversation ended: {}", session.getConversationId());
            
            // Clean up resources, save final summary, etc.
            return Mono.empty();
        });
    };
}
```

## Configuration Options

### Memory Modes

```yaml
twilio:
  agent-connect:
    memory:
      mode: once  # Options: always, once, never
```

- **always**: Retrieve memory on every message (fresh data, more API calls)
- **once**: Retrieve once per conversation and cache (efficient, may miss updates)
- **never**: Don't retrieve memory (fastest, no context)

### Cache Provider

```yaml
twilio:
  agent-connect:
    cache:
      provider: caffeine  # Options: caffeine, redis
```

- **caffeine**: In-memory cache (single instance)
- **redis**: Distributed cache (multi-instance)

### Resilience Settings

```yaml
twilio:
  agent-connect:
    resilience:
      circuit-breaker:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
      retry:
        max-attempts: 3
```

## Troubleshooting

### Issue: No messages received

**Check:**
1. Webhook URL is correct and publicly accessible
2. Twilio signature validation is passing
3. Application is running on the correct port
4. ngrok tunnel is active (for local dev)

**Logs to check:**
```
Received webhook with params: ...
```

### Issue: Memory not loading

**Check:**
1. `TWILIO_MEMORY_STORE_ID` is set
2. Memory mode is not `never`
3. API key/secret have permissions for Memory API

**Logs to check:**
```
Retrieving memory for profile: ...
```

### Issue: Duplicate messages

**Check:**
1. Idempotency cache is working
2. Webhook isn't configured multiple times

**Logs to check:**
```
Duplicate message detected, skipping processing
```

## Next Steps

- Add voice support (coming soon)
- Implement custom tools for your domain
- Deploy to production
- Set up monitoring and logging
- Configure Redis for multi-instance deployments

## Support

- [GitHub Issues](https://github.com/twilio/twilio-agent-connect-java/issues)
- [Twilio Support](https://support.twilio.com)
- [Documentation](docs/)
