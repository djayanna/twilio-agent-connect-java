package com.twilio.agentconnect.util;

import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.MemoryResponse;

/**
 * Utility for building prompts enriched with customer memory.
 */
public class MemoryPromptBuilder {

    /**
     * Compose a prompt with memory context.
     *
     * @param basePrompt The base system prompt
     * @param memory Customer memory response
     * @param context Message context
     * @return Enhanced prompt with memory
     */
    public static String compose(
            String basePrompt,
            MemoryResponse memory,
            MessageContext context) {

        StringBuilder prompt = new StringBuilder(basePrompt);

        if (memory != null && !memory.isEmpty()) {
            prompt.append("\n\n# Customer Context\n");

            // Add traits
            if (!memory.getTraits().isEmpty()) {
                prompt.append("\n## Customer Profile:\n");
                memory.getTraits().forEach((key, value) ->
                    prompt.append("- ").append(key).append(": ").append(value).append("\n")
                );
            }

            // Add recent observations
            if (!memory.getObservations().isEmpty()) {
                prompt.append("\n## Recent Observations:\n");
                memory.getObservations().stream()
                    .limit(5)
                    .forEach(obs ->
                        prompt.append("- ").append(obs.getContent()).append("\n")
                    );
            }

            // Add conversation summaries
            if (!memory.getSummaries().isEmpty()) {
                prompt.append("\n## Previous Conversations:\n");
                memory.getSummaries().stream()
                    .limit(3)
                    .forEach(summary ->
                        prompt.append("- ").append(summary.getSummary()).append("\n")
                    );
            }
        }

        // Add current message
        if (context != null && context.getMessage() != null) {
            prompt.append("\n\n# Current Message\n");
            prompt.append("Customer: ").append(context.getMessage().getContent());
        }

        return prompt.toString();
    }

    /**
     * Simple compose with just base prompt and memory.
     */
    public static String compose(String basePrompt, MemoryResponse memory) {
        return compose(basePrompt, memory, null);
    }
}
