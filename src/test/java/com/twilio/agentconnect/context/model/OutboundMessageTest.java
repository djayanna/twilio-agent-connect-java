package com.twilio.agentconnect.context.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OutboundMessage}.
 */
class OutboundMessageTest {

    @Test
    void emptyMessageHasMutableMetadataMap() {
        OutboundMessage message = new OutboundMessage();

        assertThat(message.getMetadata()).isNotNull().isEmpty();
        message.getMetadata().put("k", "v");
        assertThat(message.getMetadata()).containsEntry("k", "v");
    }

    @Test
    void gettersAndSetters() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("retry", true);

        OutboundMessage message = new OutboundMessage();
        message.setContent("Response text");
        message.setConversationId("CH123");
        message.setMetadata(metadata);

        assertThat(message.getContent()).isEqualTo("Response text");
        assertThat(message.getConversationId()).isEqualTo("CH123");
        assertThat(message.getMetadata()).isSameAs(metadata);
    }

    @Test
    void builderPopulatesAllFields() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("foo", "bar");

        OutboundMessage message = OutboundMessage.builder()
            .content("Built response")
            .conversationId("CH777")
            .metadata(metadata)
            .build();

        assertThat(message.getContent()).isEqualTo("Built response");
        assertThat(message.getConversationId()).isEqualTo("CH777");
        assertThat(message.getMetadata()).containsEntry("foo", "bar");
    }

    @Test
    void builderDefaultsMetadataToEmptyMap() {
        OutboundMessage message = OutboundMessage.builder()
            .content("minimal")
            .build();

        assertThat(message.getContent()).isEqualTo("minimal");
        assertThat(message.getConversationId()).isNull();
        assertThat(message.getMetadata()).isNotNull().isEmpty();
    }
}
