package com.twilio.agentconnect.tools.builtin;

import com.twilio.agentconnect.context.client.MemoryClient;
import com.twilio.agentconnect.context.model.ConversationSummary;
import com.twilio.agentconnect.context.model.MemoryResponse;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.Observation;
import com.twilio.agentconnect.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MemoryRecallTool}.
 */
@ExtendWith(MockitoExtension.class)
class MemoryRecallToolTest {

    @Mock
    private MemoryClient memoryClient;

    private MemoryRecallTool tool;
    private Session session;
    private MessageContext context;

    @BeforeEach
    void setUp() {
        tool = new MemoryRecallTool(memoryClient);
        session = Session.builder().id("conv-1").build();
        context = MessageContext.builder().conversationId("conv-1").build();
    }

    @Test
    void buildsTextualSummaryFromTraitsObservationsAndSummaries() {
        Map<String, Object> traits = new LinkedHashMap<>();
        traits.put("name", "Ada");
        traits.put("tier", "gold");

        Observation observation = new Observation();
        observation.setContent("Prefers email contact");

        ConversationSummary summary = new ConversationSummary();
        summary.setContent("Discussed billing last week");

        MemoryResponse memory = MemoryResponse.builder()
            .profileId("profile-1")
            .traits(traits)
            .observations(List.of(observation))
            .summaries(List.of(summary))
            .build();

        when(memoryClient.retrieveMemory(eq("profile-1"), any(Session.class)))
            .thenReturn(Mono.just(memory));

        StepVerifier.create(tool.recallMemory("profile-1", session, context))
            .assertNext(out -> {
                assertTrue(out.contains("Customer Memory:"));
                assertTrue(out.contains("Traits:"));
                assertTrue(out.contains("- name: Ada"));
                assertTrue(out.contains("- tier: gold"));
                assertTrue(out.contains("Recent Observations:"));
                assertTrue(out.contains("- Prefers email contact"));
                assertTrue(out.contains("Conversation Summaries:"));
                // Summary text rendered via getSummary().
                assertTrue(out.contains("- Discussed billing last week"));
            })
            .verifyComplete();
    }

    @Test
    void returnsNoMemoryMessageWhenMemoryEmpty() {
        when(memoryClient.retrieveMemory(eq("profile-2"), any(Session.class)))
            .thenReturn(Mono.just(MemoryResponse.empty()));

        StepVerifier.create(tool.recallMemory("profile-2", session, context))
            .expectNext("No memory found for this customer.")
            .verifyComplete();
    }

    @Test
    void omitsEmptySectionsWhenOnlyTraitsPresent() {
        Map<String, Object> traits = new LinkedHashMap<>();
        traits.put("status", "active");

        MemoryResponse memory = MemoryResponse.builder()
            .profileId("profile-3")
            .traits(traits)
            .build();

        when(memoryClient.retrieveMemory(eq("profile-3"), any(Session.class)))
            .thenReturn(Mono.just(memory));

        StepVerifier.create(tool.recallMemory("profile-3", session, context))
            .assertNext(out -> {
                assertTrue(out.contains("Traits:"));
                assertTrue(out.contains("- status: active"));
                // No observation/summary sections when those lists are empty.
                org.junit.jupiter.api.Assertions.assertFalse(out.contains("Recent Observations:"));
                org.junit.jupiter.api.Assertions.assertFalse(out.contains("Conversation Summaries:"));
            })
            .verifyComplete();
    }
}
