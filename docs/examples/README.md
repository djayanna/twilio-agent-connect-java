# Twilio Agent Connect Java Examples

This directory contains complete, runnable examples demonstrating how to use the Twilio Agent Connect Java SDK.

## Examples Overview

| Example | Description | Complexity |
|---------|-------------|------------|
| [Basic Echo Agent](basic/) | Simple agent that echoes messages back | ⭐ Beginner |
| [OpenAI Integration](openai/) | Agent powered by OpenAI GPT | ⭐⭐ Intermediate |
| [Anthropic Claude Integration](anthropic/) | Agent powered by Anthropic Claude | ⭐⭐ Intermediate |
| [Memory-Enhanced Agent](memory/) | Agent using Conversation Memory | ⭐⭐ Intermediate |
| [Tools Example](tools/) | Custom tools with handoff support | ⭐⭐⭐ Advanced |
| [Voice Agent](voice/) | Voice channel with real-time streaming | ⭐⭐⭐ Advanced |
| [Multichannel Agent](multichannel/) | Single agent handling all channels | ⭐⭐⭐ Advanced |

## Prerequisites

- Java 17 or higher
- Gradle 8.x
- Twilio account with:
  - Account SID and Auth Token
  - API Key and Secret
  - Conversation Configuration
  - Phone number (for Voice/SMS)
  - (Optional) Conversation Memory Store

## Environment Setup

Create a `.env` file or export these variables:

```bash
export TWILIO_ACCOUNT_SID="ACxxx"
export TWILIO_AUTH_TOKEN="your-auth-token"
export TWILIO_API_KEY="SKxxx"
export TWILIO_API_SECRET="your-api-secret"
export TWILIO_CONVERSATION_CONFIGURATION_ID="IGxxx"
export TWILIO_PHONE_NUMBER="+1234567890"
export TWILIO_VOICE_PUBLIC_DOMAIN="https://your-domain.com"

# Optional - for memory examples
export TWILIO_MEMORY_STORE_ID="MEMxxx"

# For LLM examples
export OPENAI_API_KEY="sk-xxx"
export ANTHROPIC_API_KEY="sk-ant-xxx"
```

## Running Examples

Each example is a standalone Spring Boot application:

```bash
cd docs/examples/basic
./gradlew bootRun
```

Or run directly with Java:

```bash
./gradlew build
java -jar build/libs/example-basic-0.1.0.jar
```

## Testing Examples

Use ngrok to expose your local server:

```bash
ngrok http 8080
```

Then configure your Twilio phone number webhooks:
- **Voice**: `https://your-ngrok-url.ngrok.io/twiml`
- **Messaging**: `https://your-ngrok-url.ngrok.io/webhook`

Send an SMS or make a call to your Twilio number to test!

## Example Structure

Each example follows this structure:

```
example-name/
├── build.gradle.kts          # Dependencies
├── src/
│   └── main/
│       ├── java/
│       │   └── Example.java  # Main application
│       └── resources/
│           └── application.yml  # Configuration
└── README.md                  # Example-specific docs
```

## Next Steps

1. Start with the [Basic Echo Agent](basic/)
2. Add LLM integration ([OpenAI](openai/) or [Anthropic](anthropic/))
3. Enhance with [Memory](memory/)
4. Add [Custom Tools](tools/)
5. Enable [Voice](voice/) support
6. Deploy as [Multichannel](multichannel/) agent

## Learning Path

### Beginner (Start Here)

1. **[Basic Echo Agent](basic/)** - Understand the core concepts
   - Message handling
   - Request/response flow
   - TAC integration

### Intermediate

2. **[OpenAI Integration](openai/)** - Add AI intelligence
   - LLM integration
   - Prompt engineering
   - Error handling

3. **[Anthropic Claude](anthropic/)** - Alternative LLM
   - Claude API
   - Tool use
   - Response streaming

4. **[Memory-Enhanced Agent](memory/)** - Personalization
   - Customer memory
   - Context awareness
   - Memory modes

### Advanced

5. **[Tools Example](tools/)** - Actions and integrations
   - Custom tool creation
   - System integration
   - Handoff to humans

6. **[Voice Agent](voice/)** - Real-time conversations
   - WebSocket handling
   - Voice optimization
   - TwiML generation

7. **[Multichannel Agent](multichannel/)** - Production-ready
   - All channels (Voice, SMS, WhatsApp, RCS)
   - Channel detection
   - Unified experience

## Quick Comparison

| Feature | Basic | OpenAI | Memory | Tools | Voice | Multichannel |
|---------|-------|--------|--------|-------|-------|--------------|
| SMS | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Voice | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| WhatsApp | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ |
| AI/LLM | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Memory | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Tools | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Complexity | Simple | Medium | Medium | High | High | High |
| LOC | ~50 | ~150 | ~200 | ~300 | ~200 | ~250 |

## Need Help?

- [Main Documentation](../../README.md)
- [Getting Started Guide](../GETTING_STARTED_GUIDE.md)
- [Python SDK Examples](https://github.com/twilio/twilio-agent-connect-python/tree/main/getting_started)
- [Twilio Support](https://support.twilio.com)
