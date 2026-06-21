package com.twilio.agentconnect.context.model;

import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.session.Session;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MessageContext}.
 */
class MessageContextTest {

    @Test
    void gettersAndSetters() {
        InboundMessage message = InboundMessage.builder().content("hi").build();
        MemoryResponse memory = MemoryResponse.empty();
        Session session = Session.builder().id("CH1").build();

        MessageContext context = new MessageContext();
        context.setMessage(message);
        context.setMemory(memory);
        context.setSession(session);
        context.setConversationId("CH1");
        context.setProfileId("mem_profile_1");
        context.setChannelType(ChannelType.CHAT);

        assertThat(context.getMessage()).isSameAs(message);
        assertThat(context.getMemory()).isSameAs(memory);
        assertThat(context.getSession()).isSameAs(session);
        assertThat(context.getConversationId()).isEqualTo("CH1");
        assertThat(context.getProfileId()).isEqualTo("mem_profile_1");
        assertThat(context.getChannelType()).isEqualTo(ChannelType.CHAT);
    }

    @Test
    void builderPopulatesAllFields() {
        InboundMessage message = InboundMessage.builder().content("hello").build();
        MemoryResponse memory = MemoryResponse.builder().profileId("mem_profile_x").build();
        Session session = Session.builder().id("CH2").build();

        MessageContext context = MessageContext.builder()
            .message(message)
            .memory(memory)
            .session(session)
            .conversationId("CH2")
            .profileId("mem_profile_x")
            .channelType(ChannelType.VOICE)
            .build();

        assertThat(context.getMessage()).isSameAs(message);
        assertThat(context.getMemory()).isSameAs(memory);
        assertThat(context.getSession()).isSameAs(session);
        assertThat(context.getConversationId()).isEqualTo("CH2");
        assertThat(context.getProfileId()).isEqualTo("mem_profile_x");
        assertThat(context.getChannelType()).isEqualTo(ChannelType.VOICE);
    }

    @Test
    void builderLeavesUnsetFieldsNull() {
        MessageContext context = MessageContext.builder()
            .conversationId("CH3")
            .build();

        assertThat(context.getConversationId()).isEqualTo("CH3");
        assertThat(context.getMessage()).isNull();
        assertThat(context.getMemory()).isNull();
        assertThat(context.getSession()).isNull();
        assertThat(context.getProfileId()).isNull();
        assertThat(context.getChannelType()).isNull();
    }
}
