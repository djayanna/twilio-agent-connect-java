package com.twilio.agentconnect.session;

import com.twilio.agentconnect.core.ChannelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link InMemorySessionManager}.
 */
class InMemorySessionManagerTest {

    private InMemorySessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new InMemorySessionManager();
    }

    @Test
    void getOrCreateBuildsSessionWithExpectedFields() {
        StepVerifier.create(manager.getOrCreate("conv-1", ChannelType.SMS))
            .assertNext(session -> {
                org.junit.jupiter.api.Assertions.assertEquals("conv-1", session.getId());
                org.junit.jupiter.api.Assertions.assertEquals("conv-1", session.getConversationId());
                org.junit.jupiter.api.Assertions.assertEquals(ChannelType.SMS, session.getChannelType());
                org.junit.jupiter.api.Assertions.assertNotNull(session.getCreatedAt());
                org.junit.jupiter.api.Assertions.assertNotNull(session.getLastActivityAt());
            })
            .verifyComplete();
    }

    @Test
    void getOrCreateReturnsSameCachedInstanceOnRepeatCall() {
        Session first = manager.getOrCreate("conv-2", ChannelType.VOICE).block();
        Session second = manager.getOrCreate("conv-2", ChannelType.VOICE).block();

        org.junit.jupiter.api.Assertions.assertNotNull(first);
        org.junit.jupiter.api.Assertions.assertSame(first, second);
    }

    @Test
    void getReturnsEmptyWhenMissing() {
        StepVerifier.create(manager.get("does-not-exist"))
            .verifyComplete();
    }

    @Test
    void getReturnsExistingSession() {
        Session created = manager.getOrCreate("conv-3", ChannelType.CHAT).block();

        StepVerifier.create(manager.get("conv-3"))
            .assertNext(found -> org.junit.jupiter.api.Assertions.assertSame(created, found))
            .verifyComplete();
    }

    @Test
    void existsReflectsPresence() {
        StepVerifier.create(manager.exists("conv-4"))
            .expectNext(false)
            .verifyComplete();

        manager.getOrCreate("conv-4", ChannelType.SMS).block();

        StepVerifier.create(manager.exists("conv-4"))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void updateTouchesAndStoresSession() {
        Session session = manager.getOrCreate("conv-5", ChannelType.SMS).block();
        org.junit.jupiter.api.Assertions.assertNotNull(session);
        java.time.Instant before = session.getLastActivityAt();

        StepVerifier.create(manager.update(session))
            .verifyComplete();

        // touch() should have advanced (or kept equal to) the last-activity timestamp.
        org.junit.jupiter.api.Assertions.assertTrue(
            !session.getLastActivityAt().isBefore(before));

        StepVerifier.create(manager.get("conv-5"))
            .assertNext(found -> org.junit.jupiter.api.Assertions.assertSame(session, found))
            .verifyComplete();
    }

    @Test
    void deleteRemovesSession() {
        manager.getOrCreate("conv-6", ChannelType.SMS).block();

        StepVerifier.create(manager.delete("conv-6"))
            .verifyComplete();

        StepVerifier.create(manager.get("conv-6"))
            .verifyComplete();

        StepVerifier.create(manager.exists("conv-6"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void fullLifecycle() {
        // create -> exists -> get -> update -> delete -> gone
        Session created = manager.getOrCreate("life", ChannelType.WHATSAPP).block();
        org.junit.jupiter.api.Assertions.assertNotNull(created);

        StepVerifier.create(manager.exists("life")).expectNext(true).verifyComplete();
        StepVerifier.create(manager.get("life")).expectNextCount(1).verifyComplete();
        StepVerifier.create(manager.update(created)).verifyComplete();
        StepVerifier.create(manager.delete("life")).verifyComplete();
        StepVerifier.create(manager.exists("life")).expectNext(false).verifyComplete();
    }
}
