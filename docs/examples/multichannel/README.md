# Multichannel Agent Example

A single AI agent that handles Voice, SMS, WhatsApp, and RCS with channel-optimized responses.

## What This Example Demonstrates

- Single agent for all communication channels
- Channel detection and adaptation
- Channel-specific response optimization
- Consistent customer experience across channels
- Memory persists across channels

## Supported Channels

| Channel | Format | Max Length | Features |
|---------|--------|-----------|----------|
| **Voice** | Speech | ~40 words | Real-time, conversational |
| **SMS** | Plain text | 160 chars | Simple, universal |
| **WhatsApp** | Text + markdown | ~300 chars | Emojis, formatting |
| **RCS** | Rich content | ~500 chars | Buttons, media, formatting |
| **Chat** | HTML/markdown | ~1000 chars | Rich formatting, links |

## Architecture

```
Customer contacts you via:
  ├─ Phone Call → Voice Channel → VoiceChannel.java
  ├─ SMS → Messaging Channel → SmsChannel.java
  ├─ WhatsApp → Messaging Channel → WhatsAppChannel.java
  └─ RCS → Messaging Channel → RcsChannel.java
               ↓
         TwilioAgentConnect
               ↓
      MultichannelAgent (your code)
               ↓
         Single LLM + Logic
               ↓
    Channel-optimized response
```

## Setup

### 1. Configure All Channels

#### Voice
In [Twilio Console](https://console.twilio.com/) → Phone Numbers:
- Voice webhook: `https://your-server.com/twiml`

#### SMS
In Twilio Console → Phone Numbers:
- Messaging webhook: `https://your-server.com/webhook`

#### WhatsApp
In Twilio Console → Messaging → WhatsApp Senders:
- Webhook: `https://your-server.com/webhook`

#### RCS
In Twilio Console → Messaging → RCS:
- Webhook: `https://your-server.com/webhook`

### 2. Set Environment Variables

```bash
# Twilio credentials
export TWILIO_ACCOUNT_SID="ACxxx"
export TWILIO_AUTH_TOKEN="your-auth-token"
export TWILIO_API_KEY="SKxxx"
export TWILIO_API_SECRET="your-api-secret"
export TWILIO_CONVERSATION_CONFIGURATION_ID="IGxxx"
export TWILIO_PHONE_NUMBER="+1234567890"

# Memory (optional but recommended)
export TWILIO_MEMORY_STORE_ID="MEMxxx"

# LLM
export OPENAI_API_KEY="sk-xxx"
```

### 3. Run

```bash
./gradlew bootRun
```

### 4. Test All Channels

```bash
# Test SMS
curl -X POST "http://localhost:8080/webhook" \
  -d "From=+15551234567" \
  -d "Body=What's my order status?"

# Test WhatsApp (from WhatsApp sandbox)
# Send message from WhatsApp to your sandbox number

# Test Voice (call your Twilio number)
```

## Channel-Specific Optimization

### Voice

**Constraints:**
- Short responses (< 40 words)
- Conversational tone
- No formatting or special characters

**Example:**
```
Customer: "What's my order status?"
Agent: "Your order ships tomorrow. You'll get tracking via text."
```

### SMS

**Constraints:**
- Single message (160 chars)
- Plain text only
- No markdown or emojis

**Example:**
```
Customer: "What's my order status?"
Agent: "Order #12345 ships tomorrow via FedEx. Tracking: 1Z999AA10123456784"
```

### WhatsApp

**Features:**
- Emojis supported 😊
- Markdown formatting (*bold*, _italic_)
- Longer messages (~300 chars)

**Example:**
```
Customer: "What's my order status?"
Agent: "Great news! 📦 Your order *#12345* ships tomorrow via FedEx.

Tracking: 1Z999AA10123456784
Expected: June 15

Need anything else?"
```

### RCS

**Features:**
- Rich formatting
- Action buttons
- Media support
- Even longer messages (~500 chars)

**Example:**
```
Customer: "What's my order status?"
Agent: "Your order #12345 is confirmed! 🎉

Status: Processing
Ships: Tomorrow
Carrier: FedEx
Tracking: 1Z999AA10123456784
Expected: June 15, 2026

[Track Package] [Modify Order] [Contact Support]"
```

## Implementation Details

### Channel Detection

```java
private Mono<OutboundMessage> handleMessage(MessageContext context) {
    ChannelType channel = context.getChannelType();
    
    // Build channel-appropriate prompt
    String systemPrompt = buildChannelPrompt(channel, context);
    
    return callLLM(systemPrompt, userMessage)
        .map(response -> formatResponseForChannel(response, channel));
}
```

### Channel-Specific Prompts

```java
private String buildChannelPrompt(ChannelType channel, MessageContext context) {
    String channelGuidance = switch (channel) {
        case VOICE -> "Keep responses under 40 words. Be conversational.";
        case SMS -> "Keep responses under 160 characters. No formatting.";
        case WHATSAPP -> "You can use emojis and markdown. Up to 300 chars.";
        case RCS -> "Rich formatting available. Up to 500 chars.";
        default -> "Be clear and concise.";
    };
    
    return basePrompt + " " + channelGuidance;
}
```

### Response Formatting

```java
private String formatResponseForChannel(String response, ChannelType channel) {
    return switch (channel) {
        case VOICE -> stripFormatting(truncate(response, 200));
        case SMS -> stripFormatting(truncate(response, 160));
        case WHATSAPP -> keepMarkdown(truncate(response, 300));
        case RCS -> addRichContent(truncate(response, 500));
        default -> response;
    };
}
```

## Cross-Channel Memory

Memory persists across channels for the same customer:

**Scenario:**

1. Customer texts: "Hi, I need help"
   - Agent (SMS): "Hi Sarah! How can I help?"

2. Customer calls 10 minutes later
   - Agent (Voice): "Hi Sarah, you just texted about needing help. What's going on?"

3. Customer messages on WhatsApp next day
   - Agent (WhatsApp): "Hi Sarah! 😊 Following up on your order question from yesterday."

**Why this works:**
- All channels use the same Profile ID (phone number)
- Memory is fetched from Conversation Memory
- Context persists across channel switches

## Testing Multichannel

### Test Script

```bash
#!/bin/bash

SERVER="http://localhost:8080"
PHONE="+15551234567"

echo "Testing SMS..."
curl -X POST "$SERVER/webhook" \
  -d "From=$PHONE" \
  -d "Body=What's my order status?"

echo "\n\nTesting WhatsApp..."
curl -X POST "$SERVER/webhook" \
  -d "From=whatsapp:$PHONE" \
  -d "Body=What's my order status?"

echo "\n\nTesting Voice..."
echo "Call your Twilio number and ask: What's my order status?"
```

### Expected Results

**SMS Response:**
```
Order #12345 ships tomorrow. Tracking: 1Z999AA10123456784
```

**WhatsApp Response:**
```
Great news! 📦 Your order #12345 ships tomorrow.
Tracking: 1Z999AA10123456784
```

**Voice Response:**
```
"Your order ships tomorrow. You'll get tracking via text."
```

## Advanced: Channel Capabilities

### Detect Channel Features

```java
private boolean supportsRichContent(ChannelType channel) {
    return channel == ChannelType.WHATSAPP ||
           channel == ChannelType.RCS ||
           channel == ChannelType.CHAT;
}

private boolean supportsEmojis(ChannelType channel) {
    return channel != ChannelType.SMS &&
           channel != ChannelType.VOICE;
}

private Mono<OutboundMessage> handleMessage(MessageContext context) {
    if (supportsRichContent(context.getChannelType())) {
        // Send rich response with images, buttons
    } else {
        // Send plain text only
    }
}
```

### Media Support

```java
// WhatsApp and RCS support images
if (channel == ChannelType.WHATSAPP || channel == ChannelType.RCS) {
    return OutboundMessage.builder()
        .content("Here's your order:")
        .mediaUrl("https://example.com/receipt.pdf")
        .conversationId(context.getConversationId())
        .build();
}
```

## Common Patterns

### 1. Progressive Disclosure

Short answer + "ask for more" pattern:

```java
// Voice (brief)
"Your order ships tomorrow."

// SMS (with call-to-action)
"Order ships tomorrow. Reply 'details' for full tracking."

// WhatsApp/RCS (full details)
"Order #12345 ships tomorrow via FedEx.
Tracking: 1Z999. Expected: June 15.
[Track] [Modify]"
```

### 2. Channel Switching

```java
if (channel == ChannelType.VOICE && needsDetailedInfo) {
    return "This is complex. I'll text you the details right now.";
    // Trigger SMS send
}
```

### 3. Channel Preferences

```java
// Check memory for preferred channel
String preferredChannel = memory.getTraits().get("preferred_channel");

if (channel != preferredChannel) {
    // "I see you prefer WhatsApp. Want me to message you there?"
}
```

## Troubleshooting

**Responses too long**
- Check `formatResponseForChannel()` truncation
- Adjust LLM `max_tokens`
- Make channel-specific prompts more strict

**Lost context across channels**
- Verify same Profile ID used for all channels
- Check Memory Store is configured
- Ensure retrieval mode is `ONCE` or `ALWAYS`

**Voice sounds robotic**
- Use Neural TTS voices
- Keep responses very short
- More conversational language in prompt

**WhatsApp formatting not working**
- Verify WhatsApp is actually enabled (not sandbox)
- Check markdown syntax (`*bold*`, `_italic_`)
- Some formatting requires approved template

## Best Practices

1. **Default to shortest format** - If unsure, optimize for SMS (160 chars)
2. **Test on real channels** - Emulators don't always match production
3. **Use memory for consistency** - Same customer, same context, any channel
4. **Adapt tone to channel** - Formal for voice, casual for WhatsApp
5. **Provide channel-specific help** - Voice: "Say help", SMS: "Reply HELP"

## Next Steps

- Add [custom tools](../tools/) that work across all channels
- Implement [memory](../memory/) for cross-channel personalization
- Add rich media for WhatsApp/RCS (images, PDFs, buttons)
- Channel-specific analytics and monitoring
- A/B test response styles per channel
