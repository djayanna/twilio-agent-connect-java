# Anthropic Claude Agent Example

An AI agent powered by Anthropic Claude with tool use support.

## What This Example Demonstrates

- Anthropic Claude API integration
- Tool use (function calling)
- Memory-enhanced prompts
- Error handling and fallbacks
- SMS-optimized responses

## Why Claude?

Anthropic's Claude offers:
- **Strong reasoning**: Excellent at complex tasks
- **Long context**: Up to 200k tokens
- **Safety**: Built-in safety features
- **Tool use**: Native function calling support
- **Latest models**: Claude 3.5 Sonnet, Opus, Haiku

## Setup

### 1. Get an Anthropic API Key

Sign up at https://console.anthropic.com/ and create an API key.

### 2. Set Environment Variables

```bash
# Twilio credentials
export TWILIO_ACCOUNT_SID="ACxxx"
export TWILIO_AUTH_TOKEN="your-auth-token"
export TWILIO_API_KEY="SKxxx"
export TWILIO_API_SECRET="your-api-secret"
export TWILIO_CONVERSATION_CONFIGURATION_ID="IGxxx"
export TWILIO_PHONE_NUMBER="+1234567890"

# Optional - for memory
export TWILIO_MEMORY_STORE_ID="MEMxxx"

# Anthropic
export ANTHROPIC_API_KEY="sk-ant-xxx"
```

### 3. Run

```bash
./gradlew bootRun
```

## Configuration

Edit `application.yml` to customize:

```yaml
anthropic:
  api:
    key: ${ANTHROPIC_API_KEY}
  model: claude-3-5-sonnet-20241022  # or claude-opus-4-0, claude-haiku-3-0
```

## Model Comparison

| Model | Speed | Cost | Best For |
|-------|-------|------|----------|
| **Claude 3.5 Sonnet** | Fast | $ | Most use cases (recommended) |
| **Claude 3 Opus** | Slow | $$$ | Complex reasoning, long tasks |
| **Claude 3 Haiku** | Very Fast | $ | Simple tasks, high volume |

## How It Works

### 1. Message Flow

```
Customer → Twilio → TAC → Your Handler
                              ↓
                    Build system prompt with memory
                              ↓
                    Call Claude API with tools
                              ↓
                    Extract text response
                              ↓
                    Return to customer
```

### 2. API Request

```java
Map<String, Object> request = Map.of(
    "model", "claude-3-5-sonnet-20241022",
    "max_tokens", 1024,
    "system", systemPrompt,  // System message (not in messages array)
    "messages", List.of(
        Map.of("role", "user", "content", userMessage)
    ),
    "tools", tools  // Exported from ToolRegistry
);
```

### 3. Tool Use

Claude can use tools registered in your application:

```java
// TAC exports tools in Anthropic format
List<Map<String, Object>> tools = toolRegistry.exportTools(ToolFormat.ANTHROPIC);

// Claude responds with tool_use blocks
{
  "content": [
    {
      "type": "tool_use",
      "id": "toolu_123",
      "name": "handoffToHuman",
      "input": { "reason": "Customer needs manager" }
    }
  ]
}
```

## Example Conversation

```
Customer: Hi, I need help with my order
Agent: I'd be happy to help! Could you provide your order number?

Customer: It's #12345
Agent: Order #12345 is currently shipping. You'll receive tracking tomorrow.

Customer: I want to speak to a manager
Agent: [Uses handoffToHuman tool] I'm connecting you to a manager now.
```

## Claude vs OpenAI

### When to Use Claude

- **Complex reasoning**: Multi-step problems
- **Long documents**: Processing large texts
- **Safety-critical**: Healthcare, legal, finance
- **Conversational**: Natural dialogue
- **Tool use**: Native function calling

### When to Use OpenAI

- **Speed**: Faster responses
- **Cost**: Generally cheaper
- **JSON mode**: Guaranteed JSON output
- **Function calling**: More mature implementation
- **Code generation**: Strong at coding tasks

## Tool Use Example

### Registering Tools

Tools are automatically discovered:

```java
@Component
class MyTools {
    @TacTool(description = "Check order status")
    public Mono<String> checkOrder(
            @TacToolParam(description = "Order number") String orderId) {
        return orderService.getStatus(orderId);
    }
}
```

### Exporting for Claude

```java
List<Map<String, Object>> tools = toolRegistry.exportTools(ToolFormat.ANTHROPIC);

// Results in:
[
  {
    "name": "checkOrder",
    "description": "Check order status",
    "input_schema": {
      "type": "object",
      "properties": {
        "orderId": {
          "type": "string",
          "description": "Order number"
        }
      },
      "required": ["orderId"]
    }
  }
]
```

### Handling Tool Calls

```java
List<Map<String, Object>> content = response.get("content");

for (Map<String, Object> block : content) {
    if ("tool_use".equals(block.get("type"))) {
        String toolName = (String) block.get("name");
        Map<String, Object> input = (Map<String, Object>) block.get("input");
        
        // Execute the tool
        String result = toolRegistry.executeTool(toolName, input);
        
        // Send result back to Claude for final response
    } else if ("text".equals(block.get("type"))) {
        return (String) block.get("text");
    }
}
```

## Advanced: Streaming Responses

For real-time responses, use streaming:

```java
private Flux<String> callClaudeStream(String systemPrompt, String userMessage) {
    return claudeClient.post()
        .uri("/messages")
        .header("x-api-key", anthropicApiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of(
            "model", model,
            "max_tokens", 1024,
            "system", systemPrompt,
            "messages", List.of(
                Map.of("role", "user", "content", userMessage)
            ),
            "stream", true  // Enable streaming
        ))
        .retrieve()
        .bodyToFlux(String.class)
        .map(this::parseStreamEvent);
}
```

## Cost Optimization

### 1. Use Haiku for Simple Tasks

```yaml
anthropic:
  model: claude-haiku-3-0  # 10x cheaper than Sonnet
```

### 2. Limit max_tokens

```java
Map.of(
    "max_tokens", 150  // Cheaper and faster
)
```

### 3. Cache System Prompts

Claude supports prompt caching:

```java
Map.of(
    "system", List.of(
        Map.of(
            "type", "text",
            "text", longSystemPrompt,
            "cache_control", Map.of("type", "ephemeral")  // Cache this
        )
    )
)
```

### 4. Use Memory Mode ONCE

```yaml
twilio:
  agent-connect:
    memory:
      retrieval-mode: ONCE  # Not ALWAYS
```

## Error Handling

### API Errors

```java
.onErrorResume(WebClientResponseException.class, error -> {
    if (error.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
        // Rate limited - retry with backoff
        return Mono.delay(Duration.ofSeconds(1))
            .then(Mono.defer(() -> callClaude(prompt, message)));
    } else if (error.getStatusCode() == HttpStatus.UNAUTHORIZED) {
        // Bad API key
        System.err.println("Invalid Anthropic API key");
        return Mono.just("Configuration error. Please try again later.");
    } else {
        // Other error
        return Mono.just("I'm having trouble right now. Please try again.");
    }
})
```

### Timeout Protection

```java
return callClaude(prompt, message)
    .timeout(Duration.ofSeconds(30))
    .onErrorResume(TimeoutException.class, e ->
        Mono.just("Request timed out. Please try again.")
    );
```

## Testing

### Unit Test

```java
@Test
void testClaudeResponse() {
    // Mock Claude response
    when(claudeClient.post()...)
        .thenReturn(Mono.just(mockResponse));
    
    // Test agent
    String response = agent.handleMessage(context).block();
    
    assertThat(response).contains("expected text");
}
```

### Integration Test

```bash
# Send real message
curl -X POST "http://localhost:8080/webhook" \
  -d "From=+15551234567" \
  -d "Body=Hello"

# Check logs for Claude API call and response
```

## Troubleshooting

**"Invalid API key"**
- Verify `ANTHROPIC_API_KEY` is set correctly
- Check key hasn't expired
- Ensure key has correct permissions

**"Rate limit exceeded"**
- Slow down requests
- Implement exponential backoff
- Upgrade Anthropic tier

**"Request too large"**
- Reduce `max_tokens`
- Limit memory in prompt
- Trim conversation history

**"Empty response"**
- Check Claude returned text block (not just tool_use)
- Add fallback for missing content
- Log full response for debugging

## Best Practices

### 1. Clear System Prompts

```java
// Good - specific and actionable
String prompt = "You are a customer service assistant. " +
    "Answer questions about orders and products. " +
    "Use checkOrder tool for order status. " +
    "Use handoffToHuman if you can't help. " +
    "Keep responses under 160 characters.";

// Bad - vague and rambling
String prompt = "You are an AI assistant that helps customers " +
    "with various things they might need help with...";
```

### 2. Handle Tool Use

```java
// Check for both text and tool_use blocks
for (Map<String, Object> block : content) {
    String type = (String) block.get("type");
    if ("tool_use".equals(type)) {
        // Execute tool and send result back
    } else if ("text".equals(type)) {
        return (String) block.get("text");
    }
}
```

### 3. Test with Real API

Don't just mock - test with real Claude API:
```bash
export ANTHROPIC_API_KEY="sk-ant-test-xxx"
./gradlew test
```

## Next Steps

- Add [custom tools](../tools/) for your business logic
- Enable [voice support](../voice/) with Claude
- Implement [streaming responses](#advanced-streaming-responses)
- Try [prompt caching](#3-cache-system-prompts) for cost savings
- Explore [Claude's extended thinking](https://docs.anthropic.com/en/docs/build-with-claude/thinking) for complex tasks

## Resources

- [Anthropic Documentation](https://docs.anthropic.com/)
- [Claude Models](https://docs.anthropic.com/en/docs/about-claude/models)
- [Tool Use Guide](https://docs.anthropic.com/en/docs/build-with-claude/tool-use)
- [Prompt Engineering](https://docs.anthropic.com/en/docs/prompt-engineering)
