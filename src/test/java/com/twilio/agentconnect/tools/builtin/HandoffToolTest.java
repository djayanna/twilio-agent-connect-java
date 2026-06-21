package com.twilio.agentconnect.tools.builtin;

import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.context.model.MemoryResponse;
import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.core.TacConfiguration;
import com.twilio.agentconnect.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Unit tests for {@link HandoffTool}.
 */
class HandoffToolTest {

    private HandoffTool handoffTool;

    @BeforeEach
    void setUp() {
        // HandoffTool keeps a reference to config but does not use it in this method.
        handoffTool = new HandoffTool(new TacConfiguration());
    }

    @SuppressWarnings("unchecked")
    @Test
    void handoffStoresPayloadInSessionWithMemoryTraits() {
        Map<String, Object> traits = new LinkedHashMap<>();
        traits.put("tier", "gold");
        MemoryResponse memory = MemoryResponse.builder().traits(traits).build();

        Session session = Session.builder().id("conv-1").build();

        MessageContext context = MessageContext.builder()
            .conversationId("conv-1")
            .profileId("profile-1")
            .channelType(ChannelType.VOICE)
            .memory(memory)
            .session(session)
            .build();

        StepVerifier.create(handoffTool.handoffToHuman("angry customer", session, context))
            .expectNext("Conversation will be escalated to a human agent. Please hold.")
            .verifyComplete();

        Object stored = session.getData().get("handoffPayload");
        assertInstanceOf(Map.class, stored);

        Map<String, Object> payload = (Map<String, Object>) stored;
        assertEquals("conv-1", payload.get("conversationId"));
        assertEquals("profile-1", payload.get("profileId"));
        assertEquals("angry customer", payload.get("reason"));
        assertEquals("VOICE", payload.get("channel"));
        // Memory non-empty -> customerTraits attached.
        assertTrue(payload.containsKey("customerTraits"));
        assertEquals(traits, payload.get("customerTraits"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void handoffOmitsCustomerTraitsWhenMemoryEmpty() {
        Session session = Session.builder().id("conv-2").build();

        MessageContext context = MessageContext.builder()
            .conversationId("conv-2")
            .profileId("profile-2")
            .channelType(ChannelType.SMS)
            .memory(MemoryResponse.empty())
            .session(session)
            .build();

        StepVerifier.create(handoffTool.handoffToHuman("need supervisor", session, context))
            .expectNextCount(1)
            .verifyComplete();

        Map<String, Object> payload = (Map<String, Object>) session.getData().get("handoffPayload");
        assertEquals("need supervisor", payload.get("reason"));
        assertEquals("SMS", payload.get("channel"));
        // Empty memory -> no traits key.
        assertFalse(payload.containsKey("customerTraits"));
        assertNull(payload.get("customerTraits"));
    }
}
