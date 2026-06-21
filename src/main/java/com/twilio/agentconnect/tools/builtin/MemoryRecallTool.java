package com.twilio.agentconnect.tools.builtin;


import com.twilio.agentconnect.context.client.MemoryClient;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.session.Session;
import com.twilio.agentconnect.tools.InjectContext;
import com.twilio.agentconnect.tools.InjectSession;
import com.twilio.agentconnect.tools.TacTool;
import com.twilio.agentconnect.tools.TacToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Built-in tool for retrieving customer memory.
 */
@Component
public class MemoryRecallTool {

    private static final Logger log = LoggerFactory.getLogger(MemoryRecallTool.class);

    private final MemoryClient memoryClient;

    public MemoryRecallTool(MemoryClient memoryClient) {
        this.memoryClient = memoryClient;
    }

    @TacTool(description = "Retrieve customer memory including traits, observations, and conversation history")
    public Mono<String> recallMemory(
            @TacToolParam(description = "Profile ID to retrieve memory for") String profileId,
            @InjectSession Session session,
            @InjectContext MessageContext context) {

        return memoryClient.retrieveMemory(profileId, session)
            .map(memory -> {
                if (memory.isEmpty()) {
                    return "No memory found for this customer.";
                }

                StringBuilder result = new StringBuilder();
                result.append("Customer Memory:\n\n");

                // Add traits
                if (!memory.getTraits().isEmpty()) {
                    result.append("Traits:\n");
                    memory.getTraits().forEach((key, value) ->
                        result.append("- ").append(key).append(": ").append(value).append("\n")
                    );
                    result.append("\n");
                }

                // Add observations
                if (!memory.getObservations().isEmpty()) {
                    result.append("Recent Observations:\n");
                    memory.getObservations().stream()
                        .limit(10)
                        .forEach(obs ->
                            result.append("- ").append(obs.getContent()).append("\n")
                        );
                    result.append("\n");
                }

                // Add summaries
                if (!memory.getSummaries().isEmpty()) {
                    result.append("Conversation Summaries:\n");
                    memory.getSummaries().stream()
                        .limit(5)
                        .forEach(summary ->
                            result.append("- ").append(summary.getSummary()).append("\n")
                        );
                }

                return result.toString();
            });
    }
}
