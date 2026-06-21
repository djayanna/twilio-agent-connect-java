package com.twilio.agentconnect.cache;

import reactor.core.publisher.Mono;

/**
 * Interface for idempotency token tracking.
 */
public interface IdempotencyCache {

    /**
     * Check if token exists and set it if not.
     *
     * @param token The idempotency token
     * @return Mono<Boolean> - true if this is a new token, false if duplicate
     */
    Mono<Boolean> checkAndSet(String token);
}
