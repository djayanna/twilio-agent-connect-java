# Custom Tools Example

Demonstrates how to create custom tools that your AI agent can use to take actions and access data from your systems.

## What This Example Demonstrates

- Creating custom tools with `@TacTool` annotation
- Tool parameters with `@TacToolParam`
- Dependency injection with `@InjectSession` and `@InjectContext`
- Exporting tools to OpenAI/Anthropic format
- Built-in tools (handoff, memory, knowledge)
- Tool execution flow

## Custom Tools in This Example

### 1. Order Lookup Tool

```java
@TacTool(description = "Check the status of a customer order")
public Mono<String> checkOrderStatus(
    @TacToolParam(description = "Order number") String orderNumber,
    @InjectSession Session session) {
    // Query your database/API
    return Mono.just("Order status: Shipped");
}
```

### 2. Search Orders Tool

```java
@TacTool(description = "Search for orders by email")
public Mono<String> searchOrdersByEmail(
    @TacToolParam(description = "Email address") String email,
    @TacToolParam(description = "Max results", required = false) Integer limit) {
    // Search your system
}
```

### 3. Update Preferences Tool

```java
@TacTool(description = "Update notification preferences")
public Mono<String> updatePreferences(
    @TacToolParam(description = "Preference type") String preferenceType,
    @TacToolParam(description = "Enable/disable") boolean enabled,
    @InjectContext MessageContext context) {
    // Update customer preferences
}
```

## How Tools Work

### 1. Definition

Tools are discovered automatically by Spring:

```java
@Component
class MyTool {
    @TacTool(description = "Does something useful")
    public Mono<String> myTool(
        @TacToolParam(description = "Input") String input) {
        // Tool implementation
        return Mono.just("Result");
    }
}
```

### 2. Registration

Tools are automatically registered by `ToolRegistry` on startup.

### 3. Export

Tools are exported to LLM-specific formats:

```java
// OpenAI format
List<Map<String, Object>> openAiTools = 
    toolRegistry.exportTools(ToolFormat.OPENAI);

// Anthropic format  
List<Map<String, Object>> anthropicTools =
    toolRegistry.exportTools(ToolFormat.ANTHROPIC);
```

### 4. Execution Flow

```
1. Customer sends message
2. LLM receives message + available tools
3. LLM decides to use a tool
4. Tool is executed with parameters
5. Tool result sent back to LLM
6. LLM generates final response
7. Response sent to customer
```

## Built-in Tools

TAC includes three built-in tools:

### 1. Memory Recall

```java
// Retrieve customer memory and history
recallMemory(profileId: String) -> String
```

### 2. Knowledge Search

```java
// Search knowledge base
searchKnowledge(query: String, limit: Integer) -> String
```

### 3. Handoff to Human

```java
// Escalate to human agent
handoffToHuman(reason: String) -> String
```

## Tool Best Practices

### 1. Keep Tools Focused

Each tool should do ONE thing well:
```java
// Good
@TacTool(description = "Get order status")
public Mono<String> getOrderStatus(String orderId)

// Bad - does too much
@TacTool(description = "Do order operations")
public Mono<String> orderOperations(String action, String orderId)
```

### 2. Clear Descriptions

```java
// Good - clear and specific
@TacToolParam(description = "Order number in format ORD-12345")
String orderNumber

// Bad - vague
@TacToolParam(description = "The order")
String order
```

### 3. Return Structured Data

Return data the LLM can easily use:

```java
// Good
return Mono.just("Order #12345: Status=Shipped, Tracking=1Z999, ETA=Tomorrow");

// Bad - too verbose
return Mono.just("The order you asked about, which is order number 12345, " +
    "has a status that is currently set to shipped and the tracking number...");
```

### 4. Error Handling

```java
@TacTool(description = "Check order status")
public Mono<String> checkOrderStatus(String orderNumber) {
    return orderService.getOrder(orderNumber)
        .map(order -> formatOrderStatus(order))
        .onErrorReturn("Order not found: " + orderNumber);
}
```

### 5. Use Injected Parameters

Don't pass session/context as regular parameters:

```java
// Good - injected
public Mono<String> myTool(
    @TacToolParam(description = "Input") String input,
    @InjectSession Session session)

// Bad - exposed to LLM
public Mono<String> myTool(
    @TacToolParam(description = "Input") String input,
    @TacToolParam(description = "Session") Session session)  // ❌
```

## Connecting to Your Systems

### Database

```java
@Component
class DatabaseTool {
    private final JdbcTemplate jdbc;
    
    @TacTool(description = "Query customer data")
    public Mono<String> queryCustomer(String customerId) {
        return Mono.fromCallable(() ->
            jdbc.queryForObject(
                "SELECT * FROM customers WHERE id = ?",
                CustomerMapper,
                customerId
            )
        ).map(customer -> formatCustomer(customer));
    }
}
```

### REST API

```java
@Component
class ApiTool {
    private final WebClient webClient;
    
    @TacTool(description = "Get product info")
    public Mono<String> getProduct(String productId) {
        return webClient.get()
            .uri("/products/{id}", productId)
            .retrieve()
            .bodyToMono(Product.class)
            .map(product -> formatProduct(product));
    }
}
```

## Testing Tools

```java
@SpringBootTest
class OrderLookupToolTest {
    
    @Autowired
    private OrderLookupTool tool;
    
    @Test
    void testOrderLookup() {
        Session session = Session.builder().build();
        MessageContext context = MessageContext.builder().build();
        
        String result = tool.checkOrderStatus("12345", session, context)
            .block();
            
        assertThat(result).contains("Order 12345");
    }
}
```

## Next Steps

- See [OpenAI example](../openai/) for tool use with GPT
- See [Anthropic example](../anthropic/) for tool use with Claude
- Enable [voice support](../voice/) with tools
- Add authentication/authorization to tools
