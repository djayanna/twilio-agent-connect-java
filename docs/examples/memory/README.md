# Memory Agent Example

Demonstrates Twilio Conversation Memory for personalized customer interactions.

## What This Example Demonstrates

- Retrieving customer memory (traits, observations, summaries)
- Building memory-enhanced prompts
- Personalizing responses based on customer history
- Memory retrieval modes (ONCE, ALWAYS, NEVER)

## What is Conversation Memory?

Twilio Conversation Memory automatically tracks and stores information about your customers across all interactions:

- **Traits**: Stable customer attributes (name, preferences, VIP status)
- **Observations**: Recent interaction notes (complained about shipping, asked about returns)
- **Summaries**: Condensed conversation history (purchased 3 times, prefers email)

Memory is scoped per customer (Profile ID), so it persists across:
- Different phone numbers
- Different channels (SMS, Voice, WhatsApp)
- Different conversations

## Setup

### 1. Create a Memory Store

In [Twilio Console](https://console.twilio.com/):
1. Go to Conversational AI → Memory → Create Memory Store
2. Name it (e.g., "Customer Memory")
3. Copy the Memory Store ID (MEMxxxx)

### 2. Set Environment Variables

```bash
# Twilio credentials
export TWILIO_ACCOUNT_SID="ACxxx"
export TWILIO_AUTH_TOKEN="your-auth-token"
export TWILIO_API_KEY="SKxxx"
export TWILIO_API_SECRET="your-api-secret"
export TWILIO_CONVERSATION_CONFIGURATION_ID="IGxxx"
export TWILIO_PHONE_NUMBER="+1234567890"

# Memory Store
export TWILIO_MEMORY_STORE_ID="MEMxxx"

# LLM
export OPENAI_API_KEY="sk-xxx"
```

### 3. Configure Memory Mode

In `application.yml`:

```yaml
twilio:
  agent-connect:
    memory:
      retrieval-mode: ONCE  # ONCE, ALWAYS, or NEVER
```

**Modes:**
- `ONCE`: Fetch memory at conversation start (default, most efficient)
- `ALWAYS`: Fetch memory for every message (real-time updates, higher cost)
- `NEVER`: Don't fetch memory

### 4. Run

```bash
./gradlew bootRun
```

## How It Works

### 1. Customer Sends Message

```
Customer: Hi, I need help with my order
```

### 2. TAC Fetches Memory

```json
{
  "traits": {
    "name": "Sarah Johnson",
    "customer_type": "VIP",
    "preferred_contact": "SMS"
  },
  "observations": [
    "Prefers expedited shipping",
    "Has premium subscription",
    "Complained about late delivery last month"
  ],
  "summaries": [
    "Frequent customer, 15 orders in past year",
    "Usually orders tech accessories"
  ]
}
```

### 3. Agent Uses Memory

```java
String systemPrompt = MemoryPromptBuilder.compose(
    "You are a customer service assistant.",
    memory,  // Injected automatically
    context
);
// Results in:
// "You are a customer service assistant.
//  Customer Name: Sarah Johnson
//  Customer Type: VIP
//  Recent Context: Prefers expedited shipping, Has premium subscription"
```

### 4. Personalized Response

```
Agent: Hi Sarah! I see you're a premium member. Let me look up your order right away.
```

## Memory in Action

### Example 1: Returning Customer

```
Customer: What's my order status?
Agent: Hi Mike! Your order #12345 is being expedited per your preference. 
       Tracking: 1Z999. Arrives tomorrow.
```

Memory used:
- Name: "Mike"
- Preference: "expedited shipping"

### Example 2: VIP Customer

```
Customer: I need to return this
Agent: Of course, Sarah. As a VIP member, I'm processing your return with 
       free pickup. Check your email for the label.
```

Memory used:
- Name: "Sarah"
- Status: "VIP customer"
- Preference: "email contact"

### Example 3: Problem Customer

```
Customer: This is taking too long!
Agent: I apologize for the frustration. I see you had a late delivery 
       last month. I'm escalating this to my manager right now.
```

Memory used:
- Observation: "complained about late delivery last month"

## MemoryPromptBuilder

The `MemoryPromptBuilder` utility formats memory for LLM prompts:

```java
String prompt = MemoryPromptBuilder.compose(
    "You are a helpful assistant.",  // Base prompt
    memory,                           // ConversationMemory object
    context                           // MessageContext
);
```

**Output format:**

```
You are a helpful assistant.

Customer Profile:
- Name: Sarah Johnson
- Customer Type: VIP
- Preferred Contact: SMS

Recent Context:
- Prefers expedited shipping
- Has premium subscription
- Complained about late delivery last month

Conversation History:
- Frequent customer, 15 orders in past year
- Usually orders tech accessories
```

## Custom Memory Formatting

If you need custom formatting:

```java
private String buildCustomPrompt(ConversationMemory memory) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("You are a customer service assistant.\n\n");
    
    if (memory != null) {
        // Add name if available
        String name = memory.getTraits().get("name");
        if (name != null) {
            prompt.append("Customer Name: ").append(name).append("\n");
        }
        
        // Add VIP status
        if ("VIP".equals(memory.getTraits().get("customer_type"))) {
            prompt.append("⚠️ VIP Customer - Prioritize!\n");
        }
        
        // Add recent problems
        if (memory.getObservations().stream()
                .anyMatch(obs -> obs.toLowerCase().contains("complaint"))) {
            prompt.append("⚠️ Previous Issue - Handle with care\n");
        }
    }
    
    return prompt.toString();
}
```

## Memory Configuration

### Retrieval Modes

**ONCE (Default - Recommended)**
- Fetches memory at conversation start
- Cached for entire conversation
- Most efficient (1 API call per conversation)
- Use when: Memory doesn't change mid-conversation

**ALWAYS**
- Fetches memory for every message
- Real-time updates
- Higher API costs
- Use when: Memory updates from other systems mid-conversation

**NEVER**
- Don't fetch memory
- No personalization
- Use when: Testing, or memory not needed

### Cache Configuration

```yaml
twilio:
  agent-connect:
    memory:
      retrieval-mode: ONCE
      cache:
        type: CAFFEINE  # or REDIS
        ttl: 3600       # seconds (for ALWAYS mode)
```

## Testing Memory

### 1. Add Test Memory

Use Twilio Console or API to add memory:

```bash
curl -X POST "https://memory.twilio.com/v1/MemoryStores/MEMxxx/Profiles" \
  -u "SKxxx:your-secret" \
  -d "ProfileId=+15551234567" \
  -d "Traits={\"name\":\"Sarah\",\"customer_type\":\"VIP\"}"
```

### 2. Send Test Message

```bash
curl -X POST "http://localhost:8080/webhook" \
  -d "From=+15551234567" \
  -d "Body=Hi I need help"
```

### 3. Check Logs

```
=== Customer Memory ===
Traits:
  - name: Sarah
  - customer_type: VIP
======================

Customer: Hi I need help
Using memory: true
Agent: Hi Sarah! As a VIP customer, I'm here to help...
```

## Best Practices

### 1. Check for Null

```java
ConversationMemory memory = context.getMemory();
if (memory != null && memory.getTraits() != null) {
    String name = memory.getTraits().get("name");
    if (name != null) {
        // Use name
    }
}
```

### 2. Don't Assume Fields Exist

```java
// Bad
String name = memory.getTraits().get("name");  // NPE if no traits

// Good
String name = Optional.ofNullable(memory)
    .map(ConversationMemory::getTraits)
    .map(t -> t.get("name"))
    .orElse("there");
```

### 3. Limit Memory in Prompt

Too much memory wastes tokens:

```java
// Good - top 3 observations
memory.getObservations().stream().limit(3)

// Bad - all 50 observations
memory.getObservations()  // Could be huge!
```

### 4. Use ONCE Mode

Unless you need real-time updates:

```yaml
memory:
  retrieval-mode: ONCE  # Not ALWAYS
```

## Troubleshooting

**No memory retrieved**
- Check `TWILIO_MEMORY_STORE_ID` is set
- Verify memory exists for this Profile ID
- Check retrieval mode isn't `NEVER`

**Outdated memory**
- Use `ALWAYS` mode for real-time
- Or clear cache and retry

**Too much context**
- Limit observations/summaries
- Use custom prompt builder
- Consider memory relevance

## Next Steps

- Add [custom tools](../tools/) that update memory
- Combine with [voice](../voice/) for personalized calls
- Use memory for [multichannel](../multichannel/) consistency
- Implement memory-based routing (VIP gets priority)
