package com.twilio.agentconnect.context.model;


import com.twilio.agentconnect.core.ChannelType;
import com.twilio.agentconnect.session.Session;

/**
 * Context provided to message ready callbacks.
 * Contains message, memory, and session information.
 */
public class MessageContext {

    /**
     * The inbound message
     */
    private InboundMessage message;

    /**
     * Memory response (profile, observations, summaries)
     */
    private MemoryResponse memory;

    /**
     * Session information
     */
    private Session session;

    /**
     * Conversation ID
     */
    private String conversationId;

    /**
     * Profile ID (from identity resolution)
     */
    private String profileId;

    /**
     * Channel type
     */
    private ChannelType channelType;

    public InboundMessage getMessage() {
        return message;
    }

    public void setMessage(InboundMessage message) {
        this.message = message;
    }

    public MemoryResponse getMemory() {
        return memory;
    }

    public void setMemory(MemoryResponse memory) {
        this.memory = memory;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(ChannelType channelType) {
        this.channelType = channelType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InboundMessage message;
        private MemoryResponse memory;
        private Session session;
        private String conversationId;
        private String profileId;
        private ChannelType channelType;

        public Builder message(InboundMessage message) {
            this.message = message;
            return this;
        }

        public Builder memory(MemoryResponse memory) {
            this.memory = memory;
            return this;
        }

        public Builder session(Session session) {
            this.session = session;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder profileId(String profileId) {
            this.profileId = profileId;
            return this;
        }

        public Builder channelType(ChannelType channelType) {
            this.channelType = channelType;
            return this;
        }

        public MessageContext build() {
            MessageContext context = new MessageContext();
            context.message = this.message;
            context.memory = this.memory;
            context.session = this.session;
            context.conversationId = this.conversationId;
            context.profileId = this.profileId;
            context.channelType = this.channelType;
            return context;
        }
    }
}
