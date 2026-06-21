# OpenAI Agent Example

An intelligent customer service agent powered by OpenAI GPT-4.

## What This Example Demonstrates

- OpenAI GPT integration with TAC
- Using Conversation Memory for personalized responses
- Reactive HTTP calls with WebClient
- Error handling for LLM failures
- SMS-optimized responses (160 char limit)

## Setup

### 1. Get an OpenAI API Key

Sign up at https://platform.openai.com/ and create an API key.

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

# OpenAI
export OPENAI_API_KEY="sk-xxx"
```

### 3. Run

```bash
./gradlew bootRun
```

## Configuration

Edit `application.yml` to customize:

```yaml
openai:
  api:
    key: ${OPENAI_API_KEY}
  model: gpt-4  # or gpt-3.5-turbo for faster/cheaper responses
```

## How It Works

1. **Customer Message**: Customer sends SMS to your Twilio number
2. **Memory Retrieval**: TAC automatically fetches customer memory (traits, history)
3. **Prompt Building**: System prompt is enriched with customer context
4. **OpenAI Call**: Message sent to GPT-4 for intelligent response
5. **Response**: GPT-4's response sent back to customer

## Example Conversation

```
Customer: Hi, I need help with my order
Agent: I'd be happy to help! Can you provide your order number?

Customer: It's #12345
Agent: Order #12345 is currently being processed and will ship tomorrow. 
       You'll receive tracking info via email.

Customer: Thanks!
Agent: You're welcome! Let me know if you need anything else.
```

## Memory Integration

With Conversation Memory enabled, the agent remembers:
- Customer name and preferences
- Previous conversations
- Order history
- Support interactions

This enables more personalized, context-aware responses.

## Cost Optimization

- **Use GPT-3.5-Turbo**: 10x cheaper than GPT-4
- **Limit tokens**: Set `max_tokens` to keep responses concise
- **Cache prompts**: Reuse system prompts across messages
- **Memory mode ONCE**: Retrieve memory once per conversation

## Error Handling

The example includes:
- Network error handling
- OpenAI API error handling
- Fallback responses when LLM fails
- Timeout protection

## Next Steps

- Add [custom tools](../tools/) for handoff to humans
- Enable [voice support](../voice/) for phone calls
- Try [Anthropic Claude](../anthropic/) as an alternative LLM
- Implement [tool calling](../tools/) for actions (database lookups, etc.)
