package com.twilio.agentconnect.channels.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the {@link ConversationRelayMessage} POJO and its builder.
 */
class ConversationRelayMessageTest {

    @Test
    void builderSetsAllFields() {
        ConversationRelayMessage message = ConversationRelayMessage.builder()
            .type(ConversationRelayMessage.MessageType.SETUP)
            .callSid("CA1")
            .text("spoken")
            .from("+111")
            .to("+222")
            .markName("m1")
            .build();

        assertEquals(ConversationRelayMessage.MessageType.SETUP, message.getType());
        assertEquals("CA1", message.getCallSid());
        assertEquals("spoken", message.getText());
        assertEquals("+111", message.getFrom());
        assertEquals("+222", message.getTo());
        assertEquals("m1", message.getMarkName());
    }

    @Test
    void buildWithNoFieldsLeavesNulls() {
        ConversationRelayMessage message = ConversationRelayMessage.builder().build();

        assertNull(message.getType());
        assertNull(message.getCallSid());
        assertNull(message.getText());
        assertNull(message.getFrom());
        assertNull(message.getTo());
        assertNull(message.getMarkName());
    }

    @Test
    void settersUpdateFields() {
        ConversationRelayMessage message = new ConversationRelayMessage();
        message.setType(ConversationRelayMessage.MessageType.PROMPT);
        message.setCallSid("CA9");
        message.setText("hello");
        message.setFrom("+1");
        message.setTo("+2");
        message.setMarkName("mark");

        assertEquals(ConversationRelayMessage.MessageType.PROMPT, message.getType());
        assertEquals("CA9", message.getCallSid());
        assertEquals("hello", message.getText());
        assertEquals("+1", message.getFrom());
        assertEquals("+2", message.getTo());
        assertEquals("mark", message.getMarkName());
    }

    @Test
    void messageTypeEnumHasExpectedValues() {
        assertEquals(4, ConversationRelayMessage.MessageType.values().length);
        assertEquals(ConversationRelayMessage.MessageType.SETUP,
            ConversationRelayMessage.MessageType.valueOf("SETUP"));
        assertEquals(ConversationRelayMessage.MessageType.MARK,
            ConversationRelayMessage.MessageType.valueOf("MARK"));
    }
}
