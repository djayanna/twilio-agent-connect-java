package com.twilio.agentconnect.context.model;


import com.twilio.agentconnect.core.ChannelType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Inbound message from a customer.
 */
public class InboundMessage {

    /**
     * Message content (text)
     */
    private String content;

    /**
     * Channel type
     */
    private ChannelType channelType;

    /**
     * Conversation ID
     */
    private String conversationId;

    /**
     * Message SID
     */
    private String messageSid;

    /**
     * Participant SID
     */
    private String participantSid;

    /**
     * Customer address (phone number, email, etc.)
     */
    private String from;

    /**
     * Agent address (phone number, etc.)
     */
    private String to;

    /**
     * Timestamp
     */
    private Instant timestamp;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata = new HashMap<>();

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(ChannelType channelType) {
        this.channelType = channelType;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessageSid() {
        return messageSid;
    }

    public void setMessageSid(String messageSid) {
        this.messageSid = messageSid;
    }

    public String getParticipantSid() {
        return participantSid;
    }

    public void setParticipantSid(String participantSid) {
        this.participantSid = participantSid;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String content;
        private ChannelType channelType;
        private String conversationId;
        private String messageSid;
        private String participantSid;
        private String from;
        private String to;
        private Instant timestamp;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder channelType(ChannelType channelType) {
            this.channelType = channelType;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder messageSid(String messageSid) {
            this.messageSid = messageSid;
            return this;
        }

        public Builder participantSid(String participantSid) {
            this.participantSid = participantSid;
            return this;
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public InboundMessage build() {
            InboundMessage message = new InboundMessage();
            message.content = this.content;
            message.channelType = this.channelType;
            message.conversationId = this.conversationId;
            message.messageSid = this.messageSid;
            message.participantSid = this.participantSid;
            message.from = this.from;
            message.to = this.to;
            message.timestamp = this.timestamp;
            message.metadata = this.metadata;
            return message;
        }
    }
}
