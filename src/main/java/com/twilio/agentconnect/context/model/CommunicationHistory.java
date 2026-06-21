package com.twilio.agentconnect.context.model;



import java.time.Instant;

/**
 * Communication history entry from Conversation Memory.
 */


public class CommunicationHistory {

    /**
     * Entry ID
     */
    private String id;

    /**
     * Channel type
     */
    private String channel;

    /**
     * Message content
     */
    private String content;

    /**
     * Direction (inbound/outbound)
     */
    private String direction;

    /**
     * Timestamp
     */
    private Instant timestamp;

    /**
     * Source conversation ID
     */
    private String conversationId;
}
