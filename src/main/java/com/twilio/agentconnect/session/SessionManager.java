package com.twilio.agentconnect.session;

import com.twilio.agentconnect.core.ChannelType;
import reactor.core.publisher.Mono;

/**
 * Interface for session management.
 */
public interface SessionManager {

    /**
     * Get an existing session or create a new one
     */
    Mono<Session> getOrCreate(String sessionId, ChannelType channelType);

    /**
     * Get an existing session
     */
    Mono<Session> get(String sessionId);

    /**
     * Update a session
     */
    Mono<Void> update(Session session);

    /**
     * Delete a session
     */
    Mono<Void> delete(String sessionId);

    /**
     * Check if a session exists
     */
    Mono<Boolean> exists(String sessionId);
}
