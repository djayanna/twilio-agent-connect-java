# Twilio Agent Connect SDK for Java/Spring Boot

A Java/Spring Boot implementation of the Twilio Agent Connect SDK, enabling seamless integration of AI agents with Twilio's communication channels.

## Overview

Twilio Agent Connect (TAC) is middleware that connects your LLM-powered agents to Twilio's communication platform. This Java SDK provides:

- **Multi-channel support**: Voice, SMS, WhatsApp, RCS, and Chat
- **Conversation Memory integration**: Persistent customer context and profiles
- **Session management**: Automatic session lifecycle tracking
- **Tool system**: Built-in tools for handoff, memory, and knowledge search
- **Reactive architecture**: Built on Spring WebFlux and Project Reactor

## Features

- ✅ Voice channel with WebSocket support (Conversation Relay)
- ✅ Messaging channels (SMS, WhatsApp, RCS, Chat)
- ✅ Conversation Memory API integration
- ✅ Identity resolution and profile management
- ✅ Annotation-based tool system
- ✅ Idempotency handling
- ✅ Twilio signature validation
- ✅ Circuit breakers and resilience patterns
- ✅ Configurable caching (Caffeine/Redis)

## Requirements

- Java 21 or higher
- Gradle 8.x
- Twilio account with:
  - Conversation Configuration
  - Phone number (for Voice/SMS)
  - Conversation Memory Store (optional)

## Quick Start

### 1. Add Dependencies

```kotlin
dependencies {
    implementation("com.twilio:twilio-agent-connect:0.1.0-SNAPSHOT")
}
```

### 2. Configure

Create `application.yml`:

```yaml
twilio:
  agent-connect:
    account-sid: ${TWILIO_ACCOUNT_SID}
    auth-token: ${TWILIO_AUTH_TOKEN}
    api-key: ${TWILIO_API_KEY}
    api-secret: ${TWILIO_API_SECRET}
    conversation-configuration-id: ${TWILIO_CONVERSATION_CONFIGURATION_ID}
    phone-number: ${TWILIO_PHONE_NUMBER}
    memory:
      store-id: ${TWILIO_MEMORY_STORE_ID}
      mode: once
```

### 3. Implement Your Agent

```java
@SpringBootApplication
public class MyAgentApplication {
    
    @Bean
    public MessageReadyCallback messageHandler(OpenAiService openAi) {
        return (message, context) -> {
            // Build prompt with memory
            String prompt = MemoryPromptBuilder.compose(
                "You are a helpful assistant",
                context.getMemory(),
                context
            );
            
            // Call your LLM
            return openAi.chat(prompt, message.getContent())
                .map(response -> OutboundMessage.builder()
                    .content(response)
                    .conversationId(context.getConversationId())
                    .build());
        };
    }
    
    public static void main(String[] args) {
        SpringApplication.run(MyAgentApplication.class, args);
    }
}
```

### 4. Run

```bash
./gradlew bootRun
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Your LLM Application                      │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│              Twilio Agent Connect SDK (TAC)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Voice      │  │  Messaging   │  │   Session    │      │
│  │   Channel    │  │   Channels   │  │   Manager    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Memory     │  │ Conversation │  │     Tool     │      │
│  │   Client     │  │    Client    │  │   Registry   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    Twilio Platform                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Conversation │  │ Conversation │  │   Twilio     │      │
│  │    Relay     │  │    Memory    │  │ Conversations│      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

## Building from Source

```bash
# Clone the repository
git clone https://github.com/twilio/twilio-agent-connect-java.git
cd twilio-agent-connect-java

# Build
./gradlew build

# Run tests
./gradlew test

# Install to local Maven repository
./gradlew publishToMavenLocal
```

## Documentation

- [Getting Started Guide](docs/getting-started.md)
- [Configuration Reference](docs/configuration.md)
- [Channel Guide](docs/channels.md)
- [Tool System](docs/tools.md)
- [Examples](docs/examples/)

## Project Status

🚧 **Early Development** - This SDK is under active development. APIs may change.

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Related Projects

- [twilio-agent-connect-python](https://github.com/twilio/twilio-agent-connect-python) - Python reference implementation
- [Twilio Java SDK](https://github.com/twilio/twilio-java)

## Contributing

Contributions welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Support

- [Twilio Support](https://support.twilio.com)
- [GitHub Issues](https://github.com/twilio/twilio-agent-connect-java/issues)
