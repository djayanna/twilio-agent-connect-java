package com.twilio.agentconnect.session;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.agentconnect.core.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-based session manager for distributed deployments.
 */
@Service("redisSessionManager")
@ConditionalOnProperty(prefix = "twilio.agent-connect.cache", name = "provider", havingValue = "redis")
public class RedisSessionManager implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionManager.class);
    private static final String KEY_PREFIX = "tac:session:";
    private static final Duration TTL = Duration.ofHours(24);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSessionManager(ReactiveRedisTemplate<String, String> redisTemplate,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Session> getOrCreate(String sessionId, ChannelType channelType) {
        String key = KEY_PREFIX + sessionId;

        return redisTemplate.opsForValue().get(key)
            .flatMap(json -> deserializeSession(json))
            .switchIfEmpty(Mono.defer(() -> {
                log.debug("Creating new session: {}", sessionId);
                Session session = Session.builder()
                    .id(sessionId)
                    .channelType(channelType)
                    .conversationId(sessionId)
                    .createdAt(Instant.now())
                    .lastActivityAt(Instant.now())
                    .build();

                return save(session).thenReturn(session);
            }));
    }

    @Override
    public Mono<Session> get(String sessionId) {
        String key = KEY_PREFIX + sessionId;

        return redisTemplate.opsForValue().get(key)
            .flatMap(this::deserializeSession);
    }

    @Override
    public Mono<Void> update(Session session) {
        session.touch();
        return save(session);
    }

    @Override
    public Mono<Void> delete(String sessionId) {
        String key = KEY_PREFIX + sessionId;

        return redisTemplate.delete(key)
            .doOnSuccess(deleted -> log.debug("Deleted session: {}", sessionId))
            .then();
    }

    @Override
    public Mono<Boolean> exists(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        return redisTemplate.hasKey(key);
    }

    /**
     * Save session to Redis.
     */
    private Mono<Void> save(Session session) {
        String key = KEY_PREFIX + session.getId();

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(session))
            .flatMap(json -> redisTemplate.opsForValue().set(key, json, TTL))
            .doOnSuccess(saved -> log.debug("Saved session: {}", session.getId()))
            .then();
    }

    /**
     * Deserialize session from JSON.
     */
    private Mono<Session> deserializeSession(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, Session.class))
            .onErrorResume(error -> {
                log.error("Error deserializing session", error);
                return Mono.empty();
            });
    }
}
