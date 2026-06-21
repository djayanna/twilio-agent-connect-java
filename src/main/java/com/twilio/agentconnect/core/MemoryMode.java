package com.twilio.agentconnect.core;

/**
 * Memory retrieval modes for Conversation Memory integration.
 */
public enum MemoryMode {
    /**
     * Retrieve memory on every message
     */
    ALWAYS,

    /**
     * Retrieve memory once per conversation and cache it
     */
    ONCE,

    /**
     * Never retrieve memory
     */
    NEVER
}
