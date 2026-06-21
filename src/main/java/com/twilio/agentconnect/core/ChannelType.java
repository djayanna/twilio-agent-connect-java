package com.twilio.agentconnect.core;

/**
 * Enumeration of supported communication channels.
 */
public enum ChannelType {
    /**
     * Voice channel via WebSocket and Conversation Relay
     */
    VOICE,

    /**
     * SMS messaging channel
     */
    SMS,

    /**
     * WhatsApp messaging channel
     */
    WHATSAPP,

    /**
     * RCS (Rich Communication Services) messaging channel
     */
    RCS,

    /**
     * Chat messaging channel
     */
    CHAT
}
