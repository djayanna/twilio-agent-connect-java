package com.twilio.agentconnect.channels.voice;



/**
 * Message from Conversation Relay WebSocket.
 */
public class ConversationRelayMessage {

    /**
     * Message type
     */
    private MessageType type;

    /**
     * Call SID
     */
    private String callSid;

    /**
     * Transcribed text (for PROMPT messages)
     */
    private String text;

    /**
     * From phone number
     */
    private String from;

    /**
     * To phone number
     */
    private String to;

    /**
     * Mark name (for MARK messages)
     */
    private String markName;

    /**
     * Custom {@code <Parameter>} values from the TwiML, present on SETUP when
     * the verb declared {@code <Parameter name="..." value="..."/>} children
     * (used here to flag briefing sessions and pass the context lookup id).
     */
    private java.util.Map<String, String> customParameters;

    /**
     * Message types from Conversation Relay
     */
    public enum MessageType {
        SETUP,      // Initial call metadata
        PROMPT,     // Transcribed user speech
        INTERRUPT,  // User interrupted agent
        MARK        // TTS playback milestone
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getCallSid() {
        return callSid;
    }

    public void setCallSid(String callSid) {
        this.callSid = callSid;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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

    public String getMarkName() {
        return markName;
    }

    public void setMarkName(String markName) {
        this.markName = markName;
    }

    public java.util.Map<String, String> getCustomParameters() {
        return customParameters;
    }

    public void setCustomParameters(java.util.Map<String, String> customParameters) {
        this.customParameters = customParameters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MessageType type;
        private String callSid;
        private String text;
        private String from;
        private String to;
        private String markName;
        private java.util.Map<String, String> customParameters;

        public Builder type(MessageType type) {
            this.type = type;
            return this;
        }

        public Builder callSid(String callSid) {
            this.callSid = callSid;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
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

        public Builder markName(String markName) {
            this.markName = markName;
            return this;
        }

        public Builder customParameters(java.util.Map<String, String> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        public ConversationRelayMessage build() {
            ConversationRelayMessage message = new ConversationRelayMessage();
            message.type = this.type;
            message.callSid = this.callSid;
            message.text = this.text;
            message.from = this.from;
            message.to = this.to;
            message.markName = this.markName;
            message.customParameters = this.customParameters;
            return message;
        }
    }
}
