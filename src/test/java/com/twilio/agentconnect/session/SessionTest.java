package com.twilio.agentconnect.session;

import com.twilio.agentconnect.core.ChannelType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link Session} data type and its builder.
 */
class SessionTest {

    @Test
    void builderPopulatesAllFields() {
        Instant created = Instant.parse("2026-06-20T10:00:00Z");
        Instant lastActivity = Instant.parse("2026-06-20T10:05:00Z");
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");

        Session session = Session.builder()
            .id("sess-1")
            .channelType(ChannelType.SMS)
            .profileId("profile-1")
            .conversationId("conv-1")
            .createdAt(created)
            .lastActivityAt(lastActivity)
            .data(data)
            .build();

        assertEquals("sess-1", session.getId());
        assertEquals(ChannelType.SMS, session.getChannelType());
        assertEquals("profile-1", session.getProfileId());
        assertEquals("conv-1", session.getConversationId());
        assertEquals(created, session.getCreatedAt());
        assertEquals(lastActivity, session.getLastActivityAt());
        assertSame(data, session.getData());
        assertEquals("value", session.getData().get("key"));
    }

    @Test
    void defaultDataMapIsEmptyAndMutable() {
        Session session = Session.builder().id("sess-2").build();

        assertNotNull(session.getData());
        assertTrue(session.getData().isEmpty());

        session.getData().put("foo", 42);
        assertEquals(42, session.getData().get("foo"));
    }

    @Test
    void settersUpdateAllFields() {
        Session session = new Session();
        Instant created = Instant.now();
        Instant lastActivity = created.plusSeconds(30);
        Map<String, Object> data = new HashMap<>();
        data.put("a", "b");

        session.setId("id-x");
        session.setChannelType(ChannelType.VOICE);
        session.setProfileId("profile-x");
        session.setConversationId("conv-x");
        session.setCreatedAt(created);
        session.setLastActivityAt(lastActivity);
        session.setData(data);

        assertEquals("id-x", session.getId());
        assertEquals(ChannelType.VOICE, session.getChannelType());
        assertEquals("profile-x", session.getProfileId());
        assertEquals("conv-x", session.getConversationId());
        assertEquals(created, session.getCreatedAt());
        assertEquals(lastActivity, session.getLastActivityAt());
        assertSame(data, session.getData());
    }

    @Test
    void touchUpdatesLastActivityTimestamp() {
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        Session session = Session.builder()
            .id("sess-3")
            .lastActivityAt(old)
            .build();

        Instant before = Instant.now();
        session.touch();

        assertNotNull(session.getLastActivityAt());
        assertTrue(session.getLastActivityAt().isAfter(old));
        // touch() uses Instant.now(); the new value must be at/after the call start.
        assertTrue(!session.getLastActivityAt().isBefore(before));
    }
}
