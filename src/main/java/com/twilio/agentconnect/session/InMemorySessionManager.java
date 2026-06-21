package com.twilio.agentconnect.session;


import com.twilio.agentconnect.core.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session manager implementation.
 * Suitable for single-instance deployments.
 */
@Service
@ConditionalOnMissingBean(name = "redisSessionManager")
public class InMemorySessionManager implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(InMemorySessionManager.class);

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Mono<Session> getOrCreate(String sessionId, ChannelType channelType) {
        return Mono.fromCallable(() ->
            sessions.computeIfAbsent(sessionId, id -> {
                log.debug("Creating new session: {}", id);
                return Session.builder()
                    .id(id)
                    .channelType(channelType)
                    .conversationId(id)
                    .createdAt(Instant.now())
                    .lastActivityAt(Instant.now())
                    .build();
            })
        );
    }

    @Override
    public Mono<Session> get(String sessionId) {
        return Mono.justOrEmpty(sessions.get(sessionId));
    }

    @Override
    public Mono<Void> update(Session session) {
        return Mono.fromRunnable(() -> {
            session.touch();
            sessions.put(session.getId(), session);
            log.debug("Updated session: {}", session.getId());
        });
    }

    @Override
    public Mono<Void> delete(String sessionId) {
        return Mono.fromRunnable(() -> {
            sessions.remove(sessionId);
            log.debug("Deleted session: {}", sessionId);
        });
    }

    @Override
    public Mono<Boolean> exists(String sessionId) {
        return Mono.just(sessions.containsKey(sessionId));
    }
}
