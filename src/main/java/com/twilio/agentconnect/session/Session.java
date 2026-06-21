package com.twilio.agentconnect.session;


import com.twilio.agentconnect.core.ChannelType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Session state for a conversation.
 */
public class Session {

    /**
     * Session ID (typically conversation ID)
     */
    private String id;

    /**
     * Channel type
     */
    private ChannelType channelType;

    /**
     * Profile ID
     */
    private String profileId;

    /**
     * Conversation ID
     */
    private String conversationId;

    /**
     * Session creation timestamp
     */
    private Instant createdAt;

    /**
     * Last activity timestamp
     */
    private Instant lastActivityAt;

    /**
     * Session-specific data
     */
    private Map<String, Object> data = new HashMap<>();

    /**
     * Update last activity timestamp
     */
    public void touch() {
        this.lastActivityAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(ChannelType channelType) {
        this.channelType = channelType;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private ChannelType channelType;
        private String profileId;
        private String conversationId;
        private Instant createdAt;
        private Instant lastActivityAt;
        private Map<String, Object> data = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder channelType(ChannelType channelType) {
            this.channelType = channelType;
            return this;
        }

        public Builder profileId(String profileId) {
            this.profileId = profileId;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder lastActivityAt(Instant lastActivityAt) {
            this.lastActivityAt = lastActivityAt;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Session build() {
            Session session = new Session();
            session.id = this.id;
            session.channelType = this.channelType;
            session.profileId = this.profileId;
            session.conversationId = this.conversationId;
            session.createdAt = this.createdAt;
            session.lastActivityAt = this.lastActivityAt;
            session.data = this.data;
            return session;
        }
    }
}
