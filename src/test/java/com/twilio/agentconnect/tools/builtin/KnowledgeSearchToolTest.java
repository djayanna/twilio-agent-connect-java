package com.twilio.agentconnect.tools.builtin;

import com.twilio.agentconnect.context.client.KnowledgeClient;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KnowledgeSearchTool}.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSearchToolTest {

    @Mock
    private KnowledgeClient knowledgeClient;

    private KnowledgeSearchTool tool;
    private Session session;
    private MessageContext context;

    @BeforeEach
    void setUp() {
        tool = new KnowledgeSearchTool(knowledgeClient);
        session = Session.builder().id("conv-1").build();
        context = MessageContext.builder().conversationId("conv-1").build();
    }

    @Test
    void formatsResultsWithTitleAndContent() {
        List<Map<String, Object>> results = List.of(
            Map.of("title", "Refund Policy", "content", "Refunds within 30 days."),
            Map.of("title", "Shipping", "content", "Ships in 2 days.")
        );
        when(knowledgeClient.search("refund", 3)).thenReturn(Mono.just(results));

        StepVerifier.create(tool.searchKnowledge("refund", 3, session, context))
            .assertNext(out -> {
                org.junit.jupiter.api.Assertions.assertTrue(out.contains("Knowledge Search Results:"));
                org.junit.jupiter.api.Assertions.assertTrue(out.contains("1. Refund Policy"));
                org.junit.jupiter.api.Assertions.assertTrue(out.contains("Refunds within 30 days."));
                org.junit.jupiter.api.Assertions.assertTrue(out.contains("2. Shipping"));
                org.junit.jupiter.api.Assertions.assertTrue(out.contains("Ships in 2 days."));
            })
            .verifyComplete();
    }

    @Test
    void usesDefaultLimitOfFiveWhenLimitIsNull() {
        when(knowledgeClient.search("query", 5)).thenReturn(Mono.just(List.of()));

        StepVerifier.create(tool.searchKnowledge("query", null, session, context))
            .expectNext("No knowledge articles found for query: query")
            .verifyComplete();
    }

    @Test
    void emptyResultsProduceNoArticlesMessage() {
        when(knowledgeClient.search("nothing", 5)).thenReturn(Mono.just(List.of()));

        StepVerifier.create(tool.searchKnowledge("nothing", 5, session, context))
            .expectNext("No knowledge articles found for query: nothing")
            .verifyComplete();
    }

    @Test
    void missingTitleAndContentFallBackToDefaults() {
        List<Map<String, Object>> results = List.of(Map.of("irrelevant", "x"));
        when(knowledgeClient.search("q", 5)).thenReturn(Mono.just(results));

        StepVerifier.create(tool.searchKnowledge("q", 5, session, context))
            .assertNext(out -> {
                // getOrDefault("title", "Untitled") path.
                org.junit.jupiter.api.Assertions.assertTrue(out.contains("1. Untitled"));
            })
            .verifyComplete();
    }
}
