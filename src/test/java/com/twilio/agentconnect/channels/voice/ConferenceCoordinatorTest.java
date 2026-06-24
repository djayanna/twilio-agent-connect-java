package com.twilio.agentconnect.channels.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConferenceCoordinatorTest {

    private final ConferenceCoordinator coordinator = new ConferenceCoordinator();

    @Test
    void storeReturnsUniqueIdsAndGetRoundTrips() {
        ConferenceCoordinator.BriefingContext a = new ConferenceCoordinator.BriefingContext(
            "CA1", "conf-CA1", "+1", "r", "s");
        ConferenceCoordinator.BriefingContext b = new ConferenceCoordinator.BriefingContext(
            "CA2", "conf-CA2", "+2", "r2", "s2");

        String idA = coordinator.store(a);
        String idB = coordinator.store(b);

        assertTrue(idA.startsWith("ctx_"));
        assertNotEquals(idA, idB);
        assertSame(a, coordinator.get(idA));
        assertSame(b, coordinator.get(idB));
    }

    @Test
    void getReturnsNullForUnknownId() {
        assertNull(coordinator.get("ctx_missing"));
    }

    @Test
    void removeDeletesAndReturnsContext() {
        ConferenceCoordinator.BriefingContext ctx = new ConferenceCoordinator.BriefingContext(
            "CA1", "conf-CA1", "+1", "r", "s");
        String id = coordinator.store(ctx);

        assertSame(ctx, coordinator.remove(id));
        assertNull(coordinator.get(id));
    }

    @Test
    void briefingContextRecordExposesAllFields() {
        ConferenceCoordinator.BriefingContext ctx = new ConferenceCoordinator.BriefingContext(
            "CA1", "conf-CA1", "+15550001111", "frustrated", "wants refund for damaged item");

        assertEquals("CA1", ctx.callerCallSid());
        assertEquals("conf-CA1", ctx.conferenceName());
        assertEquals("+15550001111", ctx.callerNumber());
        assertEquals("frustrated", ctx.reason());
        assertEquals("wants refund for damaged item", ctx.summary());
    }
}
