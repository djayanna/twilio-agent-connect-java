package com.twilio.agentconnect.context.model;

import com.twilio.agentconnect.core.ChannelType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InboundMessage}.
 */
class InboundMessageTest {

    @Test
    void emptyMessageHasMutableMetadataMap() {
        InboundMessage message = new InboundMessage();

        assertThat(message.getMetadata()).isNotNull().isEmpty();
        message.getMetadata().put("k", "v");
        assertThat(message.getMetadata()).containsEntry("k", "v");
    }

    @Test
    void gettersAndSetters() {
        Instant now = Instant.now();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "webhook");

        InboundMessage message = new InboundMessage();
        message.setContent("Hello");
        message.setChannelType(ChannelType.WHATSAPP);
        message.setConversationId("CH123");
        message.setMessageSid("SM123");
        message.setParticipantSid("MB123");
        message.setFrom("+15551112222");
        message.setTo("+15553334444");
        message.setTimestamp(now);
        message.setMetadata(metadata);

        assertThat(message.getContent()).isEqualTo("Hello");
        assertThat(message.getChannelType()).isEqualTo(ChannelType.WHATSAPP);
        assertThat(message.getConversationId()).isEqualTo("CH123");
        assertThat(message.getMessageSid()).isEqualTo("SM123");
        assertThat(message.getParticipantSid()).isEqualTo("MB123");
        assertThat(message.getFrom()).isEqualTo("+15551112222");
        assertThat(message.getTo()).isEqualTo("+15553334444");
        assertThat(message.getTimestamp()).isEqualTo(now);
        assertThat(message.getMetadata()).isSameAs(metadata);
    }

    @Test
    void builderPopulatesAllFields() {
        Instant now = Instant.now();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("foo", "bar");

        InboundMessage message = InboundMessage.builder()
            .content("Hi there")
            .channelType(ChannelType.SMS)
            .conversationId("CH999")
            .messageSid("SM999")
            .participantSid("MB999")
            .from("+10000000000")
            .to("+19999999999")
            .timestamp(now)
            .metadata(metadata)
            .build();

        assertThat(message.getContent()).isEqualTo("Hi there");
        assertThat(message.getChannelType()).isEqualTo(ChannelType.SMS);
        assertThat(message.getConversationId()).isEqualTo("CH999");
        assertThat(message.getMessageSid()).isEqualTo("SM999");
        assertThat(message.getParticipantSid()).isEqualTo("MB999");
        assertThat(message.getFrom()).isEqualTo("+10000000000");
        assertThat(message.getTo()).isEqualTo("+19999999999");
        assertThat(message.getTimestamp()).isEqualTo(now);
        assertThat(message.getMetadata()).containsEntry("foo", "bar");
    }

    @Test
    void builderDefaultsMetadataToEmptyMap() {
        InboundMessage message = InboundMessage.builder()
            .content("minimal")
            .build();

        assertThat(message.getContent()).isEqualTo("minimal");
        assertThat(message.getMetadata()).isNotNull().isEmpty();
    }
}
