# Basic Echo Agent

The simplest possible Twilio Agent Connect example - an agent that echoes back whatever the customer says.

## What This Example Demonstrates

- Basic TAC setup and configuration
- Registering a message callback
- Handling inbound messages
- Sending responses

## Running

```bash
# Set environment variables
export TWILIO_ACCOUNT_SID="ACxxx"
export TWILIO_AUTH_TOKEN="your-auth-token"
export TWILIO_API_KEY="SKxxx"
export TWILIO_API_SECRET="your-api-secret"
export TWILIO_CONVERSATION_CONFIGURATION_ID="IGxxx"
export TWILIO_PHONE_NUMBER="+1234567890"

# Run
../../../gradlew :examples:basic:bootRun

# Or run from this directory
./gradlew bootRun
```

## Testing

1. Start ngrok: `ngrok http 8080`
2. Configure Twilio webhook: `https://your-url.ngrok.io/webhook`
3. Send an SMS to your Twilio number
4. You should receive: "Echo: [your message]"

## Code Walkthrough

The example consists of a single class:

```java
@SpringBootApplication
public class BasicEchoAgent {
    
    @Bean
    public CommandLineRunner setupAgent(TwilioAgentConnect tac) {
        return args -> {
            // Register message handler
            tac.onMessageReady(this::handleMessage);
        };
    }
    
    private Mono<OutboundMessage> handleMessage(MessageContext context) {
        String customerMessage = context.getMessage().getContent();
        String response = "Echo: " + customerMessage;
        
        return Mono.just(OutboundMessage.builder()
            .content(response)
            .conversationId(context.getConversationId())
            .build());
    }
}
```

## What's Happening

1. **Setup**: The `setupAgent` bean registers your message handler when the app starts
2. **Receive**: TAC receives webhooks from Twilio and parses them
3. **Process**: Your `handleMessage` method receives a `MessageContext`
4. **Respond**: You return an `OutboundMessage` which TAC sends back

## Next Steps

- Add [OpenAI integration](../openai/) for intelligent responses
- Use [Conversation Memory](../memory/) to remember customer context
- Enable [Voice support](../voice/) for phone calls
