package com.twilio.agentconnect.callback;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry for message and conversation callbacks.
 */
@Component
public class CallbackRegistry {

    private static final Logger log = LoggerFactory.getLogger(CallbackRegistry.class);

    private MessageReadyCallback messageReadyCallback;
    private ConversationEndedCallback conversationEndedCallback;

    /**
     * Register a message ready callback
     */
    public void onMessageReady(MessageReadyCallback callback) {
        log.info("Registered message ready callback");
        this.messageReadyCallback = callback;
    }

    /**
     * Register a conversation ended callback
     */
    public void onConversationEnded(ConversationEndedCallback callback) {
        log.info("Registered conversation ended callback");
        this.conversationEndedCallback = callback;
    }

    /**
     * Check if message ready callback is registered
     */
    public boolean hasMessageReadyCallback() {
        return messageReadyCallback != null;
    }

    /**
     * Check if conversation ended callback is registered
     */
    public boolean hasConversationEndedCallback() {
        return conversationEndedCallback != null;
    }

    /**
     * Get the registered message ready callback
     */
    public MessageReadyCallback getMessageReadyCallback() {
        return messageReadyCallback;
    }

    /**
     * Get the registered conversation ended callback
     */
    public ConversationEndedCallback getConversationEndedCallback() {
        return conversationEndedCallback;
    }
}
