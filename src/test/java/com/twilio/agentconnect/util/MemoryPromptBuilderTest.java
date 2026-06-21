package com.twilio.agentconnect.util;

import com.twilio.agentconnect.context.model.ConversationSummary;
import com.twilio.agentconnect.context.model.InboundMessage;
import com.twilio.agentconnect.context.model.MemoryResponse;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.Observation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MemoryPromptBuilder}.
 */
class MemoryPromptBuilderTest {

    private static final String BASE_PROMPT = "You are a helpful assistant.";

    @Test
    void nullMemoryReturnsBasePromptUnchanged() {
        String result = MemoryPromptBuilder.compose(BASE_PROMPT, null, null);

        assertEquals(BASE_PROMPT, result);
    }

    @Test
    void emptyMemoryReturnsBasePrompt() {
        MemoryResponse empty = MemoryResponse.empty();

        String result = MemoryPromptBuilder.compose(BASE_PROMPT, empty);

        assertEquals(BASE_PROMPT, result);
        assertFalse(result.contains("# Customer Context"));
    }

    @Test
    void populatedMemoryIncludesTraitKeysObservationsAndSummaries() {
        Map<String, Object> traits = new LinkedHashMap<>();
        traits.put("name", "Ada");
        traits.put("tier", "gold");

        Observation observation = new Observation();
        observation.setContent("Customer prefers email contact");

        ConversationSummary summary = new ConversationSummary();
        summary.setContent("Discussed a billing dispute last week");

        MemoryResponse memory = MemoryResponse.builder()
            .profileId("mem_profile_123")
            .traits(traits)
            .observations(List.of(observation))
            .summaries(List.of(summary))
            .build();

        String result = MemoryPromptBuilder.compose(BASE_PROMPT, memory);

        // Base prompt is always preserved.
        assertTrue(result.startsWith(BASE_PROMPT));
        // Section headers rendered.
        assertTrue(result.contains("# Customer Context"));
        assertTrue(result.contains("## Customer Profile:"));
        assertTrue(result.contains("## Recent Observations:"));
        assertTrue(result.contains("## Previous Conversations:"));
        // Trait keys and values.
        assertTrue(result.contains("- name: Ada"));
        assertTrue(result.contains("- tier: gold"));
        // Observation content.
        assertTrue(result.contains("- Customer prefers email contact"));
        // Summary text rendered via getSummary() (alias for content).
        assertTrue(result.contains("- Discussed a billing dispute last week"));
    }

    @Test
    void summariesRenderViaGetSummaryAlias() {
        ConversationSummary summary = new ConversationSummary();
        // getSummary() returns content; verify the builder uses it.
        summary.setContent("Summary body text");

        MemoryResponse memory = MemoryResponse.builder()
            .summaries(List.of(summary))
            .build();

        String result = MemoryPromptBuilder.compose(BASE_PROMPT, memory);

        assertEquals(summary.getSummary(), summary.getContent());
        assertTrue(result.contains("- Summary body text"));
    }

    @Test
    void observationsAreLimitedToFive() {
        var builder = MemoryResponse.builder();
        java.util.List<Observation> observations = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Observation obs = new Observation();
            obs.setContent("obs-" + i);
            observations.add(obs);
        }

        MemoryResponse memory = builder.observations(observations).build();

        String result = MemoryPromptBuilder.compose(BASE_PROMPT, memory);

        // First five rendered.
        assertTrue(result.contains("- obs-0"));
        assertTrue(result.contains("- obs-4"));
        // Sixth onward dropped (limit 5).
        assertFalse(result.contains("- obs-5"));
        assertFalse(result.contains("- obs-7"));
    }

    @Test
    void summariesAreLimitedToThree() {
        java.util.List<ConversationSummary> summaries = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ConversationSummary summary = new ConversationSummary();
            summary.setContent("sum-" + i);
            summaries.add(summary);
        }

        MemoryResponse memory = MemoryResponse.builder().summaries(summaries).build();

        String result = MemoryPromptBuilder.compose(BASE_PROMPT, memory);

        assertTrue(result.contains("- sum-0"));
        assertTrue(result.contains("- sum-2"));
        assertFalse(result.contains("- sum-3"));
    }

    @Test
    void currentMessageAppendedWhenContextHasMessage() {
        InboundMessage message = InboundMessage.builder()
            .content("I need help with my order")
            .build();

        MessageContext context = MessageContext.builder()
            .message(message)
            .build();

        String result = MemoryPromptBuilder.compose(BASE_PROMPT, MemoryResponse.empty(), context);

        assertTrue(result.contains("# Current Message"));
        assertTrue(result.contains("Customer: I need help with my order"));
    }

    @Test
    void currentMessageOmittedWhenContextMessageIsNull() {
        MessageContext context = MessageContext.builder().build();

        String result = MemoryPromptBuilder.compose(BASE_PROMPT, MemoryResponse.empty(), context);

        assertFalse(result.contains("# Current Message"));
        assertEquals(BASE_PROMPT, result);
    }
}
