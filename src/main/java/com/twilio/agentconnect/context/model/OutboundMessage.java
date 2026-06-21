package com.twilio.agentconnect.context.model;



import java.util.HashMap;
import java.util.Map;

/**
 * Outbound message to send to a customer.
 */
public class OutboundMessage {

    /**
     * Message content (text)
     */
    private String content;

    /**
     * Conversation ID
     */
    private String conversationId;

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

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
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
        private String conversationId;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public OutboundMessage build() {
            OutboundMessage message = new OutboundMessage();
            message.content = this.content;
            message.conversationId = this.conversationId;
            message.metadata = this.metadata;
            return message;
        }
    }
}
