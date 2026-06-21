# Voice Agent Example

Real-time voice conversations using Twilio Conversation Relay.

## What This Example Demonstrates

- Voice channel with WebSocket integration
- Real-time bidirectional audio streaming
- Speech-to-text and text-to-speech
- Voice-optimized response length (< 40 words)
- Natural conversational style for phone calls

## Architecture

```
Customer → Phone Call → Twilio
                        ↓
                     TwiML (/twiml)
                        ↓
                  ConversationRelay
                        ↓
                  WebSocket (/voice/ws)
                        ↓
                   VoiceChannel
                        ↓
                  Your LLM + Logic
```

## Setup

### 1. Set Environment Variables

```bash
# Twilio credentials
export TWILIO_ACCOUNT_SID="ACxxx"
export TWILIO_AUTH_TOKEN="your-auth-token"
export TWILIO_API_KEY="SKxxx"
export TWILIO_API_SECRET="your-api-secret"
export TWILIO_CONVERSATION_CONFIGURATION_ID="IGxxx"
export TWILIO_PHONE_NUMBER="+1234567890"

# LLM (OpenAI or Anthropic)
export OPENAI_API_KEY="sk-xxx"
```

### 2. Start ngrok

Twilio needs a public URL to reach your local server:

```bash
ngrok http 8080
```

You'll get a URL like `https://abc123.ngrok.io`

### 3. Configure Twilio Phone Number

In the [Twilio Console](https://console.twilio.com/):
1. Go to Phone Numbers → Manage → Active Numbers
2. Click your phone number
3. Under "Voice Configuration":
   - A CALL COMES IN: Webhook
   - URL: `https://your-url.ngrok.io/twiml`
   - HTTP POST
4. Save

### 4. Run the Application

```bash
./gradlew bootRun
```

### 5. Make a Test Call

Call your Twilio phone number from any phone. You'll hear:
- Welcome message
- Speech recognition of what you say
- AI-generated voice responses

## Voice-Specific Optimizations

### 1. Response Length

Voice responses should be SHORT (< 40 words):

```java
// Good for voice
"Sure, I can help! What's your order number?"

// Bad - too long
"I'd be happy to help you check the status of your order. " +
"In order to look that up for you, I'll need you to provide " +
"me with your order number which you can find in your confirmation email."
```

### 2. Conversational Tone

```java
// Voice - casual and natural
"Got it! Your order ships tomorrow."

// SMS - can be more formal
"Order #12345 will ship tomorrow via FedEx."
```

### 3. No Formatting

Voice reads everything aloud, so skip markdown:

```java
// Good
"Your order has three items: t-shirt, socks, and hat."

// Bad - reads as "asterisk t hyphen shirt asterisk"
"Your order has three items: *t-shirt*, *socks*, and *hat*."
```

### 4. System Prompt

```java
String systemPrompt = 
    "You are a friendly voice assistant. " +
    "Keep responses SHORT - under 40 words. " +
    "Speak naturally like on a phone call. " +
    "No markdown, bullets, or formatting.";
```

## TwiML Response

The `/twiml` endpoint returns:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Response>
    <ConversationRelay
        url="wss://your-server/voice/ws"
        method="POST"
        voice="Polly.Joanna-Neural"
        track="both"
        transcriptionProvider="twilio"
        transcriptionModel="default"
    />
</Response>
```

**Key Parameters:**
- `url`: WebSocket endpoint for real-time conversation
- `voice`: Text-to-speech voice (see [Twilio Voices](https://www.twilio.com/docs/voice/twiml/say/text-speech#available-voices-and-languages))
- `track`: Audio track to send (`inbound`, `outbound`, or `both`)
- `transcriptionProvider`: STT provider (`twilio`, `google`, or `deepgram`)

## WebSocket Message Flow

### From Twilio → Agent

```json
{
  "type": "setup",
  "callSid": "CAxxxx",
  "from": "+15551234567",
  "to": "+15559876543"
}
```

```json
{
  "type": "prompt",
  "voicePrompt": "What's my order status?",
  "callSid": "CAxxxx"
}
```

```json
{
  "type": "interrupt",
  "callSid": "CAxxxx"
}
```

### From Agent → Twilio

```json
{
  "type": "response",
  "token": "response_1234",
  "response": {
    "responses": [
      {
        "content": "Your order ships tomorrow!",
        "contentType": "text/plain"
      }
    ]
  }
}
```

```json
{
  "type": "clear",
  "token": "response_1234"
}
```

## Testing Voice

### Test Script

```bash
# 1. Terminal 1: Start server
./gradlew bootRun

# 2. Terminal 2: Start ngrok
ngrok http 8080

# 3. Configure Twilio number (see Setup step 3)

# 4. Call your Twilio number from your phone

# 5. Say: "What's my order status?"

# 6. Expected: AI responds with order info
```

### Local Testing (Without Phone)

You can test the message handling logic with SMS first:

```bash
# Send SMS to your Twilio number
# It will use the same agent logic
# Once that works, voice should work too
```

## Voice vs SMS: Channel Detection

```java
private Mono<OutboundMessage> handleMessage(MessageContext context) {
    boolean isVoice = context.getChannelType() == ChannelType.VOICE;
    
    if (isVoice) {
        // Short, conversational
        return Mono.just("Your order ships tomorrow!");
    } else {
        // Detailed, formatted
        return Mono.just("Order #12345: Ships tomorrow via FedEx. " +
                         "Tracking: 1Z999AA10123456784");
    }
}
```

## Advanced: Custom Voices

Change the voice in `TwimlGenerator`:

```java
@Override
public String generateTwiml(String callSid) {
    return twimlGenerator.generateTwiml(callSid, TwimlVoice.builder()
        .name("Polly.Matthew-Neural")    // Male voice
        .language("en-GB")                // British English
        .build());
}
```

**Popular voices:**
- `Polly.Joanna-Neural` - US Female (default)
- `Polly.Matthew-Neural` - US Male
- `Polly.Amy-Neural` - British Female
- `Google.en-US-Neural2-A` - Google Neural
- See [Twilio Voice List](https://www.twilio.com/docs/voice/twiml/say/text-speech#available-voices-and-languages)

## Debugging

### Check WebSocket Connection

```bash
# Watch ngrok traffic
ngrok http 8080 --log=stdout

# Look for:
# - POST /twiml → Returns TwiML
# - WebSocket Upgrade → Connection established
# - WS frames → Messages flowing
```

### Common Issues

**"Call fails immediately"**
- Check TwiML endpoint returns valid XML
- Verify ngrok URL is correct
- Check Twilio Console error logs

**"No transcription"**
- Verify `transcriptionProvider` is set
- Check audio track is `inbound` or `both`
- Try different transcription model

**"Robot voice responses"**
- Use Neural voices (e.g., `Polly.Joanna-Neural`)
- Standard voices sound robotic

**"Delayed responses"**
- Reduce LLM max_tokens
- Use faster model (GPT-3.5 instead of GPT-4)
- Check network latency

## Next Steps

- Add [custom tools](../tools/) for voice (order lookup, transfers)
- Try [multichannel](../multichannel/) - same agent for voice + SMS
- Implement interruption handling
- Add sentiment analysis
- Voice authentication
