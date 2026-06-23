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
    private MessageStreamCallback messageStreamCallback;
    private ConversationEndedCallback conversationEndedCallback;

    /**
     * Register a message ready callback
     */
    public void onMessageReady(MessageReadyCallback callback) {
        log.info("Registered message ready callback");
        this.messageReadyCallback = callback;
    }

    /**
     * Register a streaming message callback (token-by-token responses).
     */
    public void onMessageStream(MessageStreamCallback callback) {
        log.info("Registered message stream callback");
        this.messageStreamCallback = callback;
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
     * Check if a streaming message callback is registered
     */
    public boolean hasMessageStreamCallback() {
        return messageStreamCallback != null;
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
     * Get the registered streaming message callback
     */
    public MessageStreamCallback getMessageStreamCallback() {
        return messageStreamCallback;
    }

    /**
     * Get the registered conversation ended callback
     */
    public ConversationEndedCallback getConversationEndedCallback() {
        return conversationEndedCallback;
    }
}
