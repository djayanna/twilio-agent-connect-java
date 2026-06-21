package com.twilio.agentconnect.context.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CommunicationHistory}.
 *
 * <p>The class is currently a data holder with only private fields and no public
 * accessors, so coverage is limited to verifying it can be instantiated.
 */
class CommunicationHistoryTest {

    @Test
    void canBeInstantiated() {
        CommunicationHistory history = new CommunicationHistory();

        assertThat(history).isNotNull();
    }
}
